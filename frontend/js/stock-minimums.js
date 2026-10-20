/** S5-09 configuration; actual inventory is deliberately not requested on this page. */
(() => {
  if (!Auth.requireLogin()) return;
  const $ = id => document.getElementById(id);
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const quantity = value => new Intl.NumberFormat('vi-VN',{maximumFractionDigits:4}).format(Number(value));
  const allowed = () => Auth.hasPermission('WAREHOUSE_MANAGE');
  const state = {rows:[],filters:{},options:null,page:0,size:20,pages:0,saving:false,current:null,started:false,version:0,controller:null};
  function error(id, message='') {$(id).textContent=message; $(id).hidden=!message;}
  function empty(message) {$('min-rows').innerHTML=`<tr><td colspan="8" class="stock-empty">${esc(message)}</td></tr>`;}
  function controls() {
    $('min-apply').disabled=$('min-reset').disabled=!state.options || !allowed();
    $('min-prev').disabled=!state.options || !state.rows.length || state.page<=0 || !allowed(); $('min-next').disabled=!state.options || !state.rows.length || state.page+1>=state.pages || !allowed();
    $('min-save').disabled=$('min-clear').disabled=$('min-close').disabled=state.saving || !allowed();
    $('min-quantity').disabled=state.saving || !allowed();
    $('min-rows').querySelectorAll('[data-edit]').forEach(button=>button.disabled=state.saving || !state.options || !allowed() || button.dataset.inactive==='true');
  }
  async function request(path, options={}) {
    const response=await Auth.apiFetch('/warehouse/stock-minimums'+path,{cache:'no-store',...options});
    const payload=await response.json().catch(()=>null);
    if(!response.ok || !payload?.success) throw new Error(payload?.message || 'Không thực hiện được thao tác. Vui lòng tải lại.');
    return payload.data;
  }
  function render() {
    $('min-rows').innerHTML=state.rows.map((row,index)=>`<tr><td><strong>${esc(row.productName)}</strong><small>${esc(row.productCode)}</small></td><td>${esc(row.categoryName || 'Chưa phân nhóm')}</td><td><strong>${esc(row.warehouseName)}</strong><small>${esc(row.warehouseCode)}${!row.warehouseActive?' · Ngừng hoạt động':''}</small></td><td>${esc(row.baseUnit)}</td><td class="stock-number">${row.minimumQuantity==null?'<small>Chưa khai báo</small>':quantity(row.minimumQuantity)}${row.minimumQuantity!=null && !row.minimumUnitMatches?'<small class="stock-minimum-warning">Đơn vị đã đổi; cần khai báo lại</small>':''}</td><td>${esc(row.updatedBy || '—')}</td><td>${row.updatedAt && Number.isFinite(new Date(row.updatedAt).getTime())?esc(new Date(row.updatedAt).toLocaleString('vi-VN')):'—'}</td><td><button class="btn btn--secondary" type="button" data-edit="${index}" data-inactive="${!row.warehouseActive || !row.productActive}">${row.minimumQuantity==null?'Khai báo':'Sửa ngưỡng'}</button>${!row.productActive?'<small>Sản phẩm ngừng hoạt động</small>':''}</td></tr>`).join('');
    if(!state.rows.length) empty('Không có SKU và kho phù hợp.'); controls();
  }
  function query() {return new URLSearchParams({...state.filters,page:state.page,size:state.size});}
  async function load() {
    if(!allowed() || !state.options) return;
    state.controller?.abort(); const controller=state.controller=new AbortController(), version=++state.version;
    state.rows=[]; controls(); error('min-error'); empty('Đang tải khai báo…'); $('min-count').textContent='Đang tải…'; $('min-page').textContent='';
    const url=new URL(location.href); url.search=query().toString(); history.replaceState(history.state,'',url);
    try {
      const data=await request('?'+query(),{signal:controller.signal});
      if(version!==state.version || !allowed()) return;
      if(!Array.isArray(data?.content) || !Number.isFinite(data.totalPages) || !Number.isFinite(data.totalElements)) throw new Error('Dữ liệu khai báo không hợp lệ.');
      if(state.page>0 && state.page>=data.totalPages){state.page=Math.max(0,data.totalPages-1);return load();}
      state.rows=data.content;state.pages=data.totalPages;
      $('min-count').textContent=`${data.totalElements} dòng SKU–kho`; $('min-page').textContent=data.totalPages?`Trang ${state.page+1} / ${data.totalPages}`:'Không có kết quả'; render();
    } catch(e) {if(e.name!=='AbortError' && version===state.version){state.pages=0;empty('Không tải được khai báo. Chọn Tải lại để thử lại.');$('min-count').textContent='Không tải được dữ liệu';error('min-error',e.message);}}
    finally {if(version===state.version) controls();}
  }
  function select(id, rows, label) {$(id).innerHTML=`<option value="">${label}</option>`+rows.map(row=>`<option value="${esc(row.id)}">${esc(row.code+' · '+row.name)}</option>`).join('');$(id).disabled=false;}
  function filters() {const result={};for(const [key,id] of Object.entries({warehouseId:'min-warehouse',categoryId:'min-category',keyword:'min-keyword'})) if($(id).value.trim()) result[key]=$(id).value.trim();return result;}
  async function initialize() {
    if(!allowed()) return;
    state.controller?.abort(); const controller=state.controller=new AbortController(), version=++state.version;
    state.options=null; state.rows=[]; state.pages=0; controls(); error('min-error'); empty('Đang tải bộ lọc và khai báo…'); $('min-count').textContent='Đang tải…'; $('min-page').textContent='';
    try {
      if(!await Auth.ready) throw new Error('Không xác minh được quyền. Chọn Tải lại để thử lại.');
      if(version!==state.version || !allowed()) return;
      const data=await request('/options',{signal:controller.signal}); if(version!==state.version || !allowed())return;
      if(!Array.isArray(data?.warehouses) || !Array.isArray(data.categories)) throw new Error('Bộ chọn khai báo không hợp lệ.');
      select('min-warehouse',data.warehouses,'Tất cả kho trong phạm vi');select('min-category',data.categories,'Tất cả nhóm hàng');
      const params=new URLSearchParams(location.search);
      for(const [key,id] of Object.entries({warehouseId:'min-warehouse',categoryId:'min-category'})){
        const selected=params.get(key)||'';if(selected && ![...$(id).options].some(o=>o.value===selected)) throw new Error('Kho/nhóm trong đường dẫn không còn hợp lệ hoặc nằm ngoài phạm vi.');$(id).value=selected;
      }
      $('min-keyword').value=params.get('keyword')||'';
      state.size=[20,50,100].includes(Number(params.get('size')))?Number(params.get('size')):20;$('min-size').value=state.size;
      const page=Number(params.get('page'));state.page=Number.isSafeInteger(page)&&page>=0?page:0;
      state.filters=filters();state.options=data;controls();
      if(!data.warehouses.length){empty('Chưa có kho trong phạm vi. Liên hệ quản trị viên để phân công kho.');$('min-count').textContent='Chưa có kho';return;}
      await load();
    } catch(e){if(e.name!=='AbortError' && version===state.version){state.options=null;error('min-error',e.message);empty('Chưa tải được dữ liệu khai báo.');$('min-count').textContent='Chưa tải được dữ liệu';}}
    finally {if(version===state.version) controls();}
  }
  function open(index) {
    const row=state.rows[index];if(!row || !allowed() || state.saving || !row.warehouseActive || !row.productActive) return;
    state.current=row; $('min-context').textContent=`${row.productCode} · ${row.productName} — ${row.warehouseCode} · ${row.warehouseName}`;
    $('min-unit').textContent=`(${row.baseUnit})`;$('min-quantity').value=row.minimumQuantity??'';
    $('min-clear').hidden=row.minimumQuantity==null;error('min-form-error');controls();$('min-dialog').showModal();$('min-quantity').focus();
  }
  async function save(clear=false) {
    if(state.saving || !allowed() || !state.current) return;
    const row=state.current, value=$('min-quantity').value;
    if(!clear && (value==='' || !Number.isFinite(Number(value)) || Number(value)<0)) return error('min-form-error','Nhập mức tối thiểu không âm.');
    state.saving=true;error('min-form-error');controls();
    try {
      await request(`/${encodeURIComponent(row.warehouseId)}/${encodeURIComponent(row.productId)}`,clear?{method:'DELETE'}:{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({minimumQuantity:value,baseUnit:row.baseUnit})});
      if(allowed()){$('min-dialog').close();state.current=null;Toast.show(clear?'Đã bỏ khai báo mức tối thiểu.':'Đã lưu mức tối thiểu.','success');await load();}
    } catch(e){if(allowed())error('min-form-error',e.message);}
    finally {state.saving=false;controls();}
  }
  $('min-filter').addEventListener('submit',e=>{e.preventDefault();state.filters=filters();state.page=0;load();});
  $('min-reset').addEventListener('click',()=>{$('min-filter').reset();state.filters={};state.page=0;load();});
  $('min-reload').addEventListener('click',()=>{if(!state.saving)initialize();});
  $('min-size').addEventListener('change',()=>{state.size=Number($('min-size').value);state.page=0;load();});
  $('min-prev').addEventListener('click',()=>{if(state.page>0){state.page--;load();}});
  $('min-next').addEventListener('click',()=>{if(state.page+1<state.pages){state.page++;load();}});
  $('min-rows').addEventListener('click',e=>{const button=e.target.closest('[data-edit]');if(button)open(Number(button.dataset.edit));});
  $('min-form').addEventListener('submit',e=>{e.preventDefault();save();});$('min-clear').addEventListener('click',()=>save(true));
  $('min-close').addEventListener('click',()=>{if(!state.saving){$('min-dialog').close();state.current=null;}});
  $('min-dialog').addEventListener('cancel',e=>{if(state.saving)e.preventDefault();else state.current=null;});
  window.addEventListener('auth:permissions-changed',()=>{
    if(!state.started)return;
    if(!allowed()){++state.version;state.controller?.abort();state.rows=[];state.options=null;state.current=null;$('min-dialog').close();empty('Bạn không còn quyền khai báo mức tối thiểu.');controls();return;}
    initialize();
  });
  Promise.resolve(Auth.ready).then(verified=>{state.started=true;if(!verified){error('min-error','Không xác minh được quyền. Chọn Tải lại để thử lại.');return;}if(allowed())initialize();}).catch(e=>error('min-error',e.message));
})();
