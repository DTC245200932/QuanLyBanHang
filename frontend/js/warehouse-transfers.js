/** S5-07. All quantities, scope, state transitions and permissions are validated by the server. */
(() => {
  if (!Auth.requireLogin()) return;
  const $ = id => document.getElementById(id);
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const number = value => new Intl.NumberFormat('vi-VN', {maximumFractionDigits: 4}).format(Number(value ?? 0));
  const date = value => value && Number.isFinite(new Date(value).getTime()) ? new Date(value).toLocaleString('vi-VN') : '—';
  const canWrite = () => Auth.hasPermission('WAREHOUSE_TRANSFER');
  const canRead = () => canWrite() || Auth.hasPermission('WAREHOUSE_VIEW');
  const state = {options: null, rows: [], lines: [], products: [], filters: {}, page: 0, size: 20, pages: 0,
    productPage: 0, productPages: 0, detail: null, saving: false, reads: new Map(), versions: new Map()};
  function message(id, value = '') { $(id).textContent = value; $(id).hidden = !value; }
  function notify(value) { if (window.Toast?.show) Toast.show(value, 'success'); }
  function abort(key) { state.reads.get(key)?.abort(); state.versions.set(key, (state.versions.get(key) || 0) + 1); }
  async function request(path, options = {}) {
    const response = await Auth.apiFetch('/warehouse/transfers' + path, {cache: 'no-store', ...options});
    const data = await response.json().catch(() => null);
    if (!response.ok || !data?.success) throw new Error(data?.message || 'Không thực hiện được thao tác. Vui lòng kiểm tra kết nối và tải lại.');
    return data.data;
  }
  async function read(key, path, apply, errorId = 'tr-error') {
    abort(key); const version = state.versions.get(key), controller = new AbortController(); state.reads.set(key, controller);
    try {
      const result = await request(path, {signal: controller.signal});
      if (version === state.versions.get(key) && canRead()) apply(result);
    } catch (error) {
      if (error.name !== 'AbortError' && version === state.versions.get(key) && canRead()) {
        message(errorId, error.message);
        if (key === 'list') {state.rows=[]; state.pages=0; $('tr-count').textContent='Không tải được dữ liệu'; $('tr-rows').innerHTML='<tr><td colspan="6" class="transfer-empty">Không tải được danh sách. Chọn Tải lại để thử lại.</td></tr>'; controls();}
        if (key === 'products') {$('tr-products').textContent='Không tải được hàng tại kho đi. Chọn Tìm hàng để thử lại.'; controls();}
        if (key === 'detail') $('tr-detail-info').textContent='Không tải được chi tiết phiếu.';
      }
    }
  }
  function options(select, rows, placeholder) {
    const previous = select.value;
    select.innerHTML = `<option value="">${esc(placeholder)}</option>` + rows.map(row => `<option value="${esc(row.id ?? row.value)}">${esc(row.code ? row.code + ' · ' + row.name : row.name ?? row.label)}</option>`).join('');
    if ([...select.options].some(option => option.value === previous)) select.value = previous;
  }
  function controls() {
    const writable = canWrite() && state.options?.canCreate;
    $('tr-new').hidden = !writable; $('tr-new').disabled = state.saving;
    $('tr-create-submit').disabled = !writable || state.saving || !state.lines.length;
    $('tr-apply').disabled = $('tr-reset').disabled = !state.options || !canRead();
    $('tr-prev').disabled = state.page <= 0 || !canRead(); $('tr-next').disabled = state.page + 1 >= state.pages || !canRead();
    $('tr-product-prev').disabled = state.productPage <= 0 || state.saving;
    $('tr-product-next').disabled = state.productPage + 1 >= state.productPages || state.saving;
    $('tr-receive-submit').disabled = $('tr-cancel-submit').disabled = state.saving || !canWrite();
    $('tr-receive-all').disabled = state.saving || !canWrite();
    $('tr-create-close').disabled = $('tr-detail-close').disabled = state.saving;
    $('tr-create-form').querySelectorAll('input,select,[data-remove]').forEach(el => el.disabled = state.saving || !canWrite());
    $('tr-product-search').disabled = $('tr-product-find').disabled = state.saving || !canWrite() || !$('tr-source').value;
    $('tr-products').querySelectorAll('[data-add]').forEach(button => {
      const row = state.products.find(item => String(item.productId) === button.dataset.add);
      button.disabled = state.saving || !canWrite() || !row || Number(row.availableQuantity) <= 0 || state.lines.some(item => String(item.productId) === button.dataset.add);
    });
    $('tr-detail').querySelectorAll('input').forEach(input => input.disabled = state.saving || !canWrite());
  }
  function validatePage(page) {
    if (!Array.isArray(page?.content) || !Number.isFinite(page.totalPages) || !Number.isFinite(page.totalElements)) throw new Error('Dữ liệu phân trang không hợp lệ.');
    return page;
  }
  function loadList() {
    message('tr-error'); $('tr-rows').innerHTML = '<tr><td colspan="6" class="transfer-empty">Đang tải dữ liệu…</td></tr>';
    const query = new URLSearchParams({...state.filters, page: state.page, size: state.size});
    return read('list', '/search?' + query, data => {
      validatePage(data); state.rows = data.content; state.pages = data.totalPages;
      $('tr-count').textContent = `${number(data.totalElements)} phiếu`;
      $('tr-page').textContent = data.totalPages ? `Trang ${state.page + 1} / ${data.totalPages}` : 'Không có kết quả';
      $('tr-rows').innerHTML = data.content.length ? data.content.map(row => `<tr><td><strong>${esc(row.transferCode)}</strong></td><td>${esc(row.fromWarehouseName)}<small>${esc(row.fromWarehouseCode)}</small></td><td>${esc(row.toWarehouseName)}<small>${esc(row.toWarehouseCode)}</small></td><td><span class="transfer-badge transfer-badge--${['IN_TRANSIT','COMPLETED','CANCELLED'].includes(row.status) ? row.status : ''}">${esc(row.statusDescription)}</span></td><td>${esc(date(row.dispatchedAt))}</td><td><button type="button" class="btn btn--secondary" data-detail="${esc(row.id)}">Xem chi tiết</button></td></tr>`).join('') : '<tr><td colspan="6" class="transfer-empty">Không có phiếu chuyển phù hợp.</td></tr>';
      controls();
    });
  }
  async function initialize() {
    return read('options', '/options', data => {
      if (!Array.isArray(data?.sourceWarehouses) || !Array.isArray(data.destinationWarehouses) || !Array.isArray(data.statuses)) throw new Error('Bộ chọn chuyển kho không hợp lệ.');
      state.options = data;
      for (const id of ['tr-source']) options($(id), data.sourceWarehouses, 'Chọn kho đi');
      options($('tr-target'), data.destinationWarehouses, 'Chọn kho đến');
      options($('tr-filter-source'), data.destinationWarehouses, 'Tất cả kho');
      options($('tr-filter-target'), data.destinationWarehouses, 'Tất cả kho');
      options($('tr-status'), data.statuses, 'Tất cả trạng thái');
      controls(); loadList();
    });
  }
  function filterValues() {
    const filters = {};
    for (const [key, id] of Object.entries({fromWarehouseId:'tr-filter-source',toWarehouseId:'tr-filter-target',status:'tr-status',keyword:'tr-keyword'})) if ($(id).value.trim()) filters[key] = $(id).value.trim();
    return filters;
  }
  function renderLines() {
    $('tr-create-rows').innerHTML = state.lines.length ? state.lines.map(row => `<tr><td>${esc(row.productCode)}<small>${esc(row.productName)}</small></td><td>${esc(row.unit)}</td><td class="transfer-number">${number(row.availableQuantity)}</td><td><input type="number" min="0.0001" step="0.0001" required value="${esc(row.quantity)}" data-quantity="${esc(row.productId)}" aria-label="Số lượng chuyển ${esc(row.productCode)}"></td><td><button type="button" class="btn btn--secondary" data-remove="${esc(row.productId)}">Bỏ</button></td></tr>`).join('') : '<tr><td colspan="5" class="transfer-empty">Chưa chọn hàng.</td></tr>';
    controls();
  }
  function resetDraft() {
    abort('products'); state.lines=[]; state.products=[]; state.productPage=0; state.productPages=0;
    $('tr-create-form').reset(); $('tr-products').textContent='Chọn kho đi để tải danh sách hàng.'; $('tr-product-page').textContent='';
    for (const option of $('tr-target').options) option.disabled=false;
    renderLines();
  }
  function loadProducts() {
    const warehouseId = $('tr-source').value;
    abort('products'); state.products = []; state.productPages = 0; controls();
    if (!warehouseId) { $('tr-products').textContent = 'Chọn kho đi để tải danh sách hàng.'; return; }
    $('tr-products').textContent = 'Đang tải hàng tại kho đi…';
    return read('products', '/products?' + new URLSearchParams({warehouseId, keyword:$('tr-product-search').value.trim(), page:state.productPage,size:10}), data => {
      validatePage(data); state.products = data.content; state.productPages = data.totalPages;
      $('tr-products').innerHTML = data.content.length ? data.content.map(row => `<div class="transfer-product"><div><strong>${esc(row.productCode)}</strong> · ${esc(row.productName)}<small>Khả dụng ${number(row.availableQuantity)} ${esc(row.unit)} · Thực tế ${number(row.physicalQuantity)} · Giữ chỗ ${number(row.reservedQuantity)}</small></div><button type="button" class="btn btn--secondary" data-add="${esc(row.productId)}" ${!canWrite() || Number(row.availableQuantity) <= 0 || state.lines.some(line => line.productId === row.productId) ? 'disabled' : ''}>Thêm</button></div>`).join('') : '<p>Không có sản phẩm phù hợp tại kho đi.</p>';
      $('tr-product-page').textContent = data.totalPages ? `${state.productPage + 1} / ${data.totalPages}` : ''; controls();
    }, 'tr-create-error');
  }
  async function openDetail(id) {
    message('tr-detail-error'); state.detail = null; $('tr-detail-info').textContent = 'Đang tải chi tiết…'; $('tr-detail-rows').innerHTML = '';
    $('tr-receive-submit').hidden = $('tr-receive-all').hidden = true; $('tr-cancel-form').hidden = true;
    $('tr-receive-note').value = $('tr-cancel-reason').value = '';
    if (!$('tr-detail').open) $('tr-detail').showModal();
    return read('detail', '/' + encodeURIComponent(id), row => { state.detail = row; renderDetail(); }, 'tr-detail-error');
  }
  function renderDetail() {
    const row = state.detail; if (!row) return;
    const receive = row.canReceive && canWrite(), cancel = row.canCancel && canWrite();
    $('tr-detail-code').textContent = row.transferCode;
    $('tr-detail-info').innerHTML = `<p><strong>Kho đi</strong><br>${esc(row.fromWarehouseCode)} · ${esc(row.fromWarehouseName)}</p><p><strong>Kho đến</strong><br>${esc(row.toWarehouseCode)} · ${esc(row.toWarehouseName)}</p><p><strong>Trạng thái</strong><br>${esc(row.statusDescription)}</p><p><strong>Xuất chuyển</strong><br>${esc(date(row.dispatchedAt))} · ${esc(row.createdBy)}</p>${row.receivedAt ? `<p><strong>Nhận hàng</strong><br>${esc(date(row.receivedAt))} · ${esc(row.receivedBy)}</p>` : ''}${row.note ? `<p><strong>Ghi chú</strong><br>${esc(row.note)}</p>` : ''}${row.receiveNote ? `<p><strong>Ghi chú nhận</strong><br>${esc(row.receiveNote)}</p>` : ''}${row.cancelReason ? `<p><strong>Lý do hủy</strong><br>${esc(row.cancelReason)}</p>` : ''}`;
    $('tr-detail-rows').innerHTML = (row.items || []).map(item => `<tr data-item="${esc(item.id)}"><td>${esc(item.productCode)}<small>${esc(item.productName)}</small></td><td>${esc(item.unit)}</td><td class="transfer-number">${number(item.quantity)}</td><td class="transfer-number">${receive ? `<input data-received type="number" min="0" step="0.0001" required value="${esc(item.quantity)}" aria-label="Thực nhận ${esc(item.productCode)}">` : item.receivedQuantity == null ? '—' : number(item.receivedQuantity)}</td><td class="transfer-number" data-discrepancy>${receive ? '0' : item.discrepancyQuantity == null ? '—' : number(item.discrepancyQuantity)}</td><td>${receive ? `<input data-reason maxlength="500" aria-label="Lý do chênh lệch ${esc(item.productCode)}" placeholder="Bắt buộc nếu có chênh lệch">` : esc(item.discrepancyReason || '—')}</td></tr>`).join('');
    $('tr-receive-submit').hidden = $('tr-receive-all').hidden = $('tr-receive-hint').hidden = !receive;
    $('tr-receive-note').closest('label').hidden = !receive; $('tr-cancel-form').hidden = !cancel; controls();
  }
  async function mutate(path, body, errorId, completed) {
    if (state.saving || !canWrite()) return;
    state.saving = true; message(errorId); controls();
    try {
      const result = await request(path, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
      if (canRead()) { completed(result); if (state.detail && $('tr-detail').open) renderDetail(); await loadList(); }
    } catch (error) { message(errorId, error.message); }
    finally { state.saving = false; controls(); }
  }
  $('tr-filter').addEventListener('submit', event => {event.preventDefault(); state.page=0; state.filters=filterValues(); loadList();});
  $('tr-reset').addEventListener('click', () => {$('tr-filter').reset(); state.page=0; state.filters={}; loadList();});
  $('tr-reload').addEventListener('click', () => { if (!state.saving) initialize(); });
  $('tr-size').addEventListener('change', () => {state.size=Number($('tr-size').value); state.page=0; loadList();});
  $('tr-prev').addEventListener('click', () => {if(state.page>0){state.page--; loadList();}});
  $('tr-next').addEventListener('click', () => {if(state.page+1<state.pages){state.page++; loadList();}});
  $('tr-rows').addEventListener('click', event => {const button=event.target.closest('[data-detail]'); if(button) openDetail(button.dataset.detail);});
  $('tr-new').addEventListener('click', () => {if(canWrite()){ $('tr-create-panel').hidden=false; $('tr-source').focus(); renderLines(); }});
  $('tr-create-close').addEventListener('click', () => {if(!state.saving){$('tr-create-panel').hidden=true; abort('products');}});
  $('tr-source').addEventListener('change', () => {state.lines=[]; state.productPage=0; renderLines(); message('tr-create-error'); $('tr-product-search').disabled=$('tr-product-find').disabled=!$('tr-source').value; for(const option of $('tr-target').options) option.disabled=Boolean(option.value && option.value===$('tr-source').value); if($('tr-target').value===$('tr-source').value) $('tr-target').value=''; loadProducts();});
  $('tr-product-find').addEventListener('click', () => {state.productPage=0; message('tr-create-error'); loadProducts();});
  $('tr-product-search').addEventListener('keydown', event => {if(event.key==='Enter'){event.preventDefault(); $('tr-product-find').click();}});
  $('tr-product-prev').addEventListener('click', () => {if(state.productPage>0){state.productPage--; loadProducts();}});
  $('tr-product-next').addEventListener('click', () => {if(state.productPage+1<state.productPages){state.productPage++; loadProducts();}});
  $('tr-products').addEventListener('click', event => {const button=event.target.closest('[data-add]'); if(!button || !canWrite()) return; const row=state.products.find(item=>String(item.productId)===button.dataset.add); if(row && !state.lines.some(item=>item.productId===row.productId)){state.lines.push({...row, quantity:''}); renderLines(); button.disabled=true;}});
  $('tr-create-rows').addEventListener('input', event => {const row=state.lines.find(item=>String(item.productId)===event.target.dataset.quantity); if(row) row.quantity=event.target.value;});
  $('tr-create-rows').addEventListener('click', event => {const button=event.target.closest('[data-remove]'); if(button && !state.saving){state.lines=state.lines.filter(item=>String(item.productId)!==button.dataset.remove); renderLines(); loadProducts();}});
  $('tr-create-form').addEventListener('submit', event => {event.preventDefault(); if(!state.lines.length) return message('tr-create-error','Vui lòng chọn ít nhất một sản phẩm.'); if($('tr-source').value===$('tr-target').value) return message('tr-create-error','Kho đi và kho đến không được trùng nhau.'); if(state.lines.some(item=>!Number.isFinite(Number(item.quantity)) || Number(item.quantity)<=0)) return message('tr-create-error','Vui lòng nhập số lượng chuyển lớn hơn 0.'); mutate('', {fromWarehouseId:Number($('tr-source').value),toWarehouseId:Number($('tr-target').value),transferCode:$('tr-code').value.trim()||null,note:$('tr-note').value.trim()||null,items:state.lines.map(item=>({productId:item.productId,unit:item.unit,quantity:item.quantity}))}, 'tr-create-error', row=>{resetDraft(); $('tr-create-panel').hidden=true; state.page=0; notify('Đã xuất chuyển và lập phiếu.'); openDetail(row.id);});});
  $('tr-detail-close').addEventListener('click', () => {if(!state.saving){abort('detail'); $('tr-detail').close(); state.detail=null;}});
  $('tr-detail').addEventListener('cancel', event => {if(state.saving) event.preventDefault(); else {abort('detail'); state.detail=null;}});
  $('tr-detail-rows').addEventListener('input', event => {const row=event.target.closest('[data-item]'); if(!row || !state.detail) return; const item=state.detail.items.find(item=>String(item.id)===row.dataset.item); const input=row.querySelector('[data-received]'), reason=row.querySelector('[data-reason]'); if(!input || !reason) return; const diff=Number(item.quantity)-Number(input.value); row.querySelector('[data-discrepancy]').textContent=input.value===''?'—':number(diff); reason.required=input.value!=='' && Math.abs(diff)>0.0000001;});
  $('tr-receive-all').addEventListener('click', () => {if(state.detail?.canReceive && canWrite() && !state.saving) renderDetail();});
  $('tr-receive-form').addEventListener('submit', event => {event.preventDefault(); if(!state.detail?.canReceive || !canWrite()) return; const items=[]; for(const row of $('tr-detail-rows').querySelectorAll('[data-item]')){const input=row.querySelector('[data-received]'), reason=row.querySelector('[data-reason]').value.trim(); const item=state.detail.items.find(item=>String(item.id)===row.dataset.item); if(input.value==='' || !Number.isFinite(Number(input.value)) || Number(input.value)<0) return message('tr-detail-error','Vui lòng nhập thực nhận không âm cho tất cả dòng hàng.'); if(Math.abs(Number(item.quantity)-Number(input.value))>0.0000001 && !reason) return message('tr-detail-error','Có chênh lệch khi nhận; vui lòng nhập lý do.'); items.push({itemId:item.id,receivedQuantity:input.value,discrepancyReason:reason||null});} mutate('/'+state.detail.id+'/receive', {items,note:$('tr-receive-note').value.trim()||null},'tr-detail-error',row=>{state.detail=row; notify('Đã xác nhận thực nhận tại kho đến.');});});
  $('tr-cancel-form').addEventListener('submit', event => {event.preventDefault(); if(!state.detail?.canCancel || !canWrite()) return; const reason=$('tr-cancel-reason').value.trim(); if(!reason) return message('tr-detail-error','Vui lòng nhập lý do hủy.'); mutate('/'+state.detail.id+'/cancel',{reason},'tr-detail-error',row=>{state.detail=row; notify('Đã hủy phiếu và hoàn tồn kho đi.');});});
  // Shared app-navigation owns the 403 redirect; a second redirect would abort navigation.
  window.addEventListener('auth:permissions-changed', () => {
    for (const key of state.reads.keys()) abort(key);
    state.options=null; state.products=[]; state.lines=[]; $('tr-create-panel').hidden=true; $('tr-detail').close(); state.detail=null; controls();
    if (!canRead()) {
      state.rows=[]; $('tr-count').textContent='Không có quyền truy cập';
      $('tr-rows').innerHTML='<tr><td colspan="6" class="transfer-empty">Bạn không còn quyền xem phiếu chuyển kho.</td></tr>';
      return;
    }
    initialize();
  });
  Promise.resolve(Auth.ready).then(() => {if(!canRead()) return; initialize();}).catch(error=>message('tr-error',error.message));
})();
