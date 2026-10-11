/**
 * create-order.js — S3-09 Tạo đơn hàng cho đại lý.
 *
 * Tính năng:
 * - Chọn đại lý (load từ /dealers/active), điểm giao hàng, ngày giao
 * - Thêm dòng hàng: tìm kiếm sản phẩm theo SKU/tên, chọn đơn vị tính & số lượng
 * - Hiển thị tổng tiền hàng, chiết khấu, tổng phải thu theo thời gian thực
 * - Lưu bản nháp vào database qua /orders/draft, mở lại tiếp tục
 * - Responsive xuống 360px
 *
 * API:
 *   GET  /dealers/active                       → đại lý đang hoạt động
 *   GET  /dealers/{id}/delivery-points         → điểm giao hàng của đại lý
 *   GET  /products?search={q}&active=true      → tìm sản phẩm
 *   GET  /products/{id}/unit-conversions       → đơn vị tính quy đổi
 *   POST /orders                               → tạo đơn hàng
 */
(() => {
  /* ── Auth guard ─────────────────────────────────────────── */
  if (!Auth.requireLogin()) return;

  if (!Auth.hasAnyRole(
    'ROLE_ADMIN', 'ROLE_SALES_MANAGER', 'ROLE_SALES',
    'ROLE_WAREHOUSE_MANAGER', 'ROLE_WAREHOUSE_STAFF',
  )) {
    location.href = '/error?status=403';
    return;
  }

  /* ── DOM helpers ────────────────────────────────────────── */
  const $ = (id) => document.getElementById(id);
  const escHtml = (v) =>
    String(v ?? '').replace(/[&<>'"]/g, (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c],
    );

  const fmtMoney = (v) =>
    Number(v || 0).toLocaleString('vi-VN', { style: 'currency', currency: 'VND' });

  const todayInputValue = () => {
    const now = new Date();
    const pad = (value) => String(value).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  };

  /* ── State ──────────────────────────────────────────────── */
  const state = {
    dealers: [],        // list of active dealers
    deliveryPoints: [], // list of delivery points for selected dealer
    selectedDealer: null,
    items: [],          // { productId, sku, name, unitConversions, selectedUnit, qty, unitPrice, discountAmount }
    productCache: {},   // productId → { unitConversions }
    priceList: null,    // bảng giá hiệu lực theo nhóm đại lý
    pendingDeliveryPointId: null,
  };

  let pricingRequestId = 0;

  /* ── Layout: Sidebar ─────────────────────────────────────── */
  (function initLayout() {
    const user = Auth.getUser?.() ?? null;
    if (!user) return;

    const initials = (user.fullName || user.username || '?')
      .split(' ')
      .map((w) => w[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
    $('user-avatar').textContent = initials;
    $('user-name').textContent = user.fullName || user.username;
    $('user-role').textContent = user.roles
      ?.map((r) => MenuConfig.getRoleDisplayName(r))
      .join(', ') ?? '';

    const nav = $('sidebar-nav');
    const items = MenuConfig.getMenuForUser(user);
    let lastSection = null;
    items.forEach((item) => {
      if (item.section && item.section !== lastSection) {
        lastSection = item.section;
        const label = document.createElement('div');
        label.className = 'sidebar__section-label';
        label.textContent = item.section;
        nav.appendChild(label);
      }
      const a = document.createElement('a');
      a.className = 'sidebar__link';
      a.href = item.href;
      a.innerHTML = `<svg viewBox="0 0 24 24">${item.icon}</svg><span>${escHtml(item.label)}</span>`;
      nav.appendChild(a);
    });

    const doLogout = () => Auth.logout?.();
    $('btn-logout')?.addEventListener('click', doLogout);
    $('btn-logout-topbar')?.addEventListener('click', doLogout);

    $('sidebar-toggle')?.addEventListener('click', () => {
      $('sidebar').classList.toggle('sidebar--open');
      $('sidebar-overlay').classList.toggle('sidebar-overlay--visible');
    });
    $('sidebar-close')?.addEventListener('click', () => {
      $('sidebar').classList.remove('sidebar--open');
      $('sidebar-overlay').classList.remove('sidebar-overlay--visible');
    });
    $('sidebar-overlay')?.addEventListener('click', () => {
      $('sidebar').classList.remove('sidebar--open');
      $('sidebar-overlay').classList.remove('sidebar-overlay--visible');
    });
  })();

  /* ── Toast ──────────────────────────────────────────────── */
  function showToast(message, type = 'success') {
    const icons = {
      success: '<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>',
      error:   '<circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>',
      warning: '<path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>',
    };
    const container = $('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast toast--${type}`;
    toast.innerHTML = `<span class="toast__icon"><svg width="16" height="16" viewBox="0 0 24 24">${icons[type] || icons.success}</svg></span>${escHtml(message)}`;
    container.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add('toast--visible'));
    setTimeout(() => {
      toast.classList.add('toast--exit');
      setTimeout(() => toast.remove(), 300);
    }, 3800);
  }

  /* ── Load dealers ────────────────────────────────────────── */
  async function loadDealers() {
    try {
      const res = await Auth.apiFetch('/dealers/active');
      const result = await res.json();
      if (!res.ok || !result.success) throw new Error(result.message || 'Không tải được danh sách đại lý');
      state.dealers = Array.isArray(result.data) ? result.data : [];
      populateDealerSelect();
    } catch (e) {
      showToast(e.message || 'Lỗi tải danh sách đại lý', 'error');
    }
  }

  function populateDealerSelect() {
    const sel = $('co-dealer');
    sel.innerHTML = '<option value="">— Chọn đại lý —</option>';
    state.dealers.forEach((d) => {
      const opt = document.createElement('option');
      opt.value = d.id;
      opt.textContent = `${d.code} — ${d.name}`;
      opt.dataset.group = d.customerGroup || '';
      opt.dataset.address = d.address || '';
      sel.appendChild(opt);
    });
  }

  /* ── Dealer selection ────────────────────────────────────── */
  $('co-dealer').addEventListener('change', async () => {
    const val = $('co-dealer').value;
    const dealerData = state.dealers.find((d) => String(d.id) === String(val)) ?? null;
    state.selectedDealer = dealerData;

    // Update info strip
    const infoStrip = $('co-dealer-info');
    if (dealerData) {
      $('co-dealer-group-badge').innerHTML = groupBadge(dealerData.customerGroup);
      $('co-dealer-address').textContent = dealerData.address || '';
      infoStrip.hidden = false;
    } else {
      infoStrip.hidden = true;
    }

    // Reset & reload delivery points
    const dpSel = $('co-delivery-point');
    dpSel.innerHTML = '<option value="">— Chọn điểm giao hàng —</option>';
    dpSel.disabled = !dealerData;
    state.deliveryPoints = [];
    state.priceList = null;
    setDeliveryStatus('');

    if (dealerData) {
      await Promise.all([
        loadDeliveryPoints(dealerData.id),
        loadEffectivePriceList(dealerData.customerGroup),
      ]);
      syncItemPrices();
      refreshPricingPreview();
    }
  });

  async function loadDeliveryPoints(dealerId) {
    const dpSel = $('co-delivery-point');
    try {
      const res = await Auth.apiFetch(`/dealers/${dealerId}/delivery-points`);
      const result = await res.json().catch(() => null);
      if (!res.ok) throw new Error(result?.message || 'Không tải được điểm giao hàng.');
      const points = Array.isArray(result) ? result : (Array.isArray(result?.data) ? result.data : []);
      state.deliveryPoints = points;
      dpSel.innerHTML = '<option value="">— Chọn điểm giao hàng —</option>';
      if (!points.length) {
        dpSel.innerHTML = '<option value="">— Chưa khai báo điểm giao hàng —</option>';
        return;
      }

      points.forEach((p) => {
        const opt = document.createElement('option');
        opt.value = p.id;
        opt.textContent = `${p.receiverName || `Điểm #${p.id}`} — ${p.address || 'Chưa có địa chỉ'}${p.isDefault ? ' (Mặc định)' : ''}`;
        dpSel.appendChild(opt);
      });
      const defaultPoint = points.find((point) => point.isDefault);
      const requestedPoint = points.find((point) => String(point.id) === String(state.pendingDeliveryPointId));
      if (requestedPoint || defaultPoint) dpSel.value = String((requestedPoint || defaultPoint).id);
      state.pendingDeliveryPointId = null;
      setDeliveryStatus('Có thể chọn điểm giao hàng của đại lý cho đơn này.');
    } catch (error) {
      state.deliveryPoints = [];
      dpSel.innerHTML = '<option value="">— Không tải được điểm giao hàng —</option>';
      dpSel.disabled = true;
      setDeliveryStatus(error.message || 'Không tải được điểm giao hàng.', true);
      showToast(error.message || 'Không tải được điểm giao hàng.', 'error');
    }
  }

  async function loadEffectivePriceList(customerGroup) {
    if (!customerGroup) return;
    try {
      const res = await Auth.apiFetch(`/price-lists/effective?customerGroup=${encodeURIComponent(customerGroup)}`);
      const result = await res.json();
      if (!res.ok || !result.success) {
        state.priceList = null;
        return;
      }
      state.priceList = result.data || null;
    } catch {
      // Giá cuối cùng vẫn được backend tự xác định khi ghi nhận đơn.
      state.priceList = null;
    }
  }

  function setDeliveryStatus(message, isError = false) {
    const el = $('co-delivery-status');
    if (!el) return;
    el.textContent = message;
    el.hidden = !message;
    el.classList.toggle('co-field-hint--error', isError);
    el.classList.toggle('co-field-hint--info', !isError && !!message);
  }

  /* ── Group badge ─────────────────────────────────────────── */
  const GROUP_LABELS = {
    DAI_LY_CAP_1: 'Đại lý cấp 1',
    DAI_LY_CAP_2: 'Đại lý cấp 2',
    KHACH_LE: 'Khách lẻ',
  };
  const GROUP_BADGE_CLASS = {
    DAI_LY_CAP_1: 'group-badge--cap1',
    DAI_LY_CAP_2: 'group-badge--cap2',
    KHACH_LE: 'group-badge--le',
  };

  function groupBadge(group) {
    if (!group) return '';
    const label = GROUP_LABELS[group] || group;
    const cls = GROUP_BADGE_CLASS[group] || 'group-badge--unknown';
    return `<span class="group-badge ${cls}">${escHtml(label)}</span>`;
  }

  /* ── Product search ──────────────────────────────────────── */
  let searchTimer = null;
  const productInput = $('co-product-search');
  const dropdown = $('co-product-dropdown');

  productInput.addEventListener('input', () => {
    clearTimeout(searchTimer);
    const q = productInput.value.trim();
    if (!q) {
      dropdown.hidden = true;
      return;
    }
    searchTimer = setTimeout(() => searchProducts(q), 220);
  });

  productInput.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      dropdown.hidden = true;
      productInput.value = '';
    }
  });

  document.addEventListener('click', (e) => {
    if (!productInput.contains(e.target) && !dropdown.contains(e.target)) {
      dropdown.hidden = true;
    }
  });

  async function searchProducts(q) {
    dropdown.hidden = false;
    dropdown.innerHTML = '<div class="co-dropdown-loading">Đang tìm…</div>';
    try {
      const res = await Auth.apiFetch(`/products?search=${encodeURIComponent(q)}&active=true&size=30`);
      const result = await res.json();
      if (!res.ok || !result.success) throw new Error(result.message || 'Lỗi tìm kiếm');
      const products = Array.isArray(result.data)
        ? result.data
        : (Array.isArray(result.data?.content) ? result.data.content : []);

      if (!products.length) {
        dropdown.innerHTML = '<div class="co-dropdown-empty">Không tìm thấy sản phẩm phù hợp.</div>';
        return;
      }

      dropdown.innerHTML = products.map((p) => `
        <div class="co-product-option" role="option" data-id="${p.id}" tabindex="0">
          <span class="co-product-option__sku">${escHtml(p.sku || p.code || '')}</span>
          <span class="co-product-option__name">${escHtml(p.name)}</span>
          <span class="co-product-option__price">${fmtMoney(p.sellingPrice || p.price || 0)}</span>
        </div>
      `).join('');

      dropdown.querySelectorAll('.co-product-option').forEach((opt, idx) => {
        const prod = products[idx];
        const addProduct = () => {
          addItem(prod);
          dropdown.hidden = true;
          productInput.value = '';
          productInput.focus();
        };
        opt.addEventListener('click', addProduct);
        opt.addEventListener('keydown', (e) => {
          if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); addProduct(); }
        });
      });
    } catch (e) {
      dropdown.innerHTML = `<div class="co-dropdown-empty">${escHtml(e.message || 'Lỗi tìm kiếm')}</div>`;
    }
  }

  /* ── Items management ────────────────────────────────────── */
  let itemSeq = 0;

  async function addItem(product) {
    // Check duplicate
    const existing = state.items.find((it) => it.productId === product.id);
    if (existing) {
      // just bump qty
      existing.qty += 1;
      renderItems();
      updateSummary();
      refreshPricingPreview();
      showToast(`Đã tăng số lượng «${product.name}» lên ${existing.qty}.`, 'success');
      return;
    }

    // Fetch unit conversions if not cached
    let units = [{ id: null, unitName: product.unit || 'cái', conversionFactor: 1 }];
    if (!state.productCache[product.id]) {
      try {
        const res = await Auth.apiFetch(`/products/${product.id}/unit-conversions`);
        if (res.ok) {
          const result = await res.json();
          if (result.success && Array.isArray(result.data) && result.data.length) {
            units = result.data;
            state.productCache[product.id] = units;
          }
        }
      } catch { /* use default */ }
    } else {
      units = state.productCache[product.id];
    }

    const item = {
      _key: ++itemSeq,
      productId: product.id,
      sku: product.sku || product.code || '',
      name: product.name,
      unitConversions: units,
      selectedUnit: units[0],
      qty: 1,
      basePrice: Number(product.sellingPrice || product.price || 0),
      unitPrice: 0,
      discountAmount: 0,
    };
    item.unitPrice = getUnitPrice(item);
    state.items.push(item);
    renderItems();
    updateSummary();
    refreshPricingPreview();
  }

  function removeItem(key) {
    state.items = state.items.filter((it) => it._key !== key);
    renderItems();
    updateSummary();
    refreshPricingPreview();
  }

  function renderItems() {
    const tbody = $('co-items-body');
    const emptyRow = $('co-items-empty');

    if (!state.items.length) {
      // Remove existing item rows, show empty
      tbody.querySelectorAll('.co-item-row').forEach((r) => r.remove());
      emptyRow.hidden = false;
      return;
    }

    emptyRow.hidden = true;

    // Remove old item rows
    tbody.querySelectorAll('.co-item-row').forEach((r) => r.remove());

    state.items.forEach((item) => {
      const row = document.createElement('tr');
      row.className = 'co-item-row';
      row.dataset.key = item._key;

      const unitOptions = item.unitConversions.map((u) =>
        `<option value="${u.id ?? u.unitName}" ${u === item.selectedUnit ? 'selected' : ''}>${escHtml(u.unitName)}</option>`,
      ).join('');

      const lineTotal = calcLineTotal(item);

      row.innerHTML = `
        <td><span class="co-sku-badge">${escHtml(item.sku)}</span></td>
        <td style="min-width:160px;max-width:240px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escHtml(item.name)}</td>
        <td>
          <select class="co-unit-select" data-field="unit">
            ${unitOptions}
          </select>
        </td>
        <td style="text-align:right">
          <input class="co-qty-input" type="number" data-field="qty" min="1" step="1" value="${item.qty}" />
        </td>
        <td style="text-align:right"><span class="co-price-value" data-price>${fmtMoney(item.unitPrice)}</span></td>
        <td style="text-align:right"><span class="co-discount-value" data-discount>${item.discountAmount > 0 ? '−' + fmtMoney(item.discountAmount) : '—'}</span></td>
        <td style="text-align:right">
          <span class="co-item-total" data-total>${fmtMoney(lineTotal)}</span>
        </td>
        <td style="text-align:center">
          <button class="co-del-btn" type="button" title="Xoá dòng này" aria-label="Xoá ${escHtml(item.name)}">
            <svg viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6"/></svg>
          </button>
        </td>
      `;

      // Delete
      row.querySelector('.co-del-btn').addEventListener('click', () => removeItem(item._key));

      // Qty
      row.querySelector('[data-field="qty"]').addEventListener('change', (e) => {
        item.qty = Math.max(1, Number(e.target.value) || 1);
        e.target.value = item.qty;
        refreshRowTotal(row, item);
        updateSummary();
        saveDraftSilently();
        refreshPricingPreview();
      });

      // Unit
      row.querySelector('[data-field="unit"]').addEventListener('change', (e) => {
        const selected = item.unitConversions.find(
          (u) => String(u.id ?? u.unitName) === e.target.value,
        );
        if (selected) {
          item.selectedUnit = selected;
          item.unitPrice = getUnitPrice(item);
          renderItems();
          updateSummary();
          refreshPricingPreview();
        }
        saveDraftSilently();
      });

      tbody.insertBefore(row, emptyRow);
    });
  }

  function refreshRowTotal(row, item) {
    const total = calcLineTotal(item);
    const el = row.querySelector('[data-total]');
    if (el) el.textContent = fmtMoney(total);
    const priceEl = row.querySelector('[data-price]');
    if (priceEl) priceEl.textContent = fmtMoney(item.unitPrice);
    const discountEl = row.querySelector('[data-discount]');
    if (discountEl) discountEl.textContent = item.discountAmount > 0 ? `−${fmtMoney(item.discountAmount)}` : '—';
  }

  function calcLineTotal(item) {
    return Math.max(0, item.qty * item.unitPrice - item.discountAmount);
  }

  function getUnitPrice(item) {
    const factor = Number(item.selectedUnit?.conversionFactor ?? 1) || 1;
    const priceLine = state.priceList?.items?.find((line) => Number(line.productId) === Number(item.productId));
    if (priceLine?.sellingPrice != null) return Number(priceLine.sellingPrice) * factor;
    if (item.selectedUnit?.price != null) return Number(item.selectedUnit.price);
    return Number(item.basePrice || 0) * factor;
  }

  function syncItemPrices() {
    state.items.forEach((item) => {
      item.unitPrice = getUnitPrice(item);
    });
    renderItems();
    updateSummary();
  }

  /* ── Summary ─────────────────────────────────────────────── */
  function updateSummary() {
    let subtotal = 0;
    let totalDiscount = 0;
    state.items.forEach((item) => {
      subtotal += item.qty * item.unitPrice;
      totalDiscount += item.discountAmount || 0;
    });
    const grandTotal = Math.max(0, subtotal - totalDiscount);

    $('co-subtotal').textContent = fmtMoney(subtotal);
    $('co-total-discount').textContent = totalDiscount > 0 ? `−${fmtMoney(totalDiscount)}` : '−0 ₫';
    $('co-grand-total').textContent = fmtMoney(grandTotal);
  }

  async function refreshPricingPreview() {
    if (!state.items.length) {
      $('co-pricing-status').hidden = true;
      return;
    }

    const requestId = ++pricingRequestId;
    const statusEl = $('co-pricing-status');
    statusEl.hidden = false;
    statusEl.className = 'co-pricing-status co-pricing-status--info';
    statusEl.textContent = 'Đang cập nhật chiết khấu…';

    try {
      const payload = {
        dealerId: state.selectedDealer?.id ? Number(state.selectedDealer.id) : null,
        customerGroup: state.selectedDealer?.customerGroup || null,
        orderDate: todayInputValue(),
        items: state.items.map((item) => ({
          productId: Number(item.productId),
          unit: item.selectedUnit?.unitName || item.selectedUnit?.unit || 'cái',
          quantity: item.qty,
          price: item.unitPrice,
        })),
      };
      const res = await Auth.apiFetch('/volume-discounts/calculate', {
        method: 'POST',
        body: JSON.stringify(payload),
      });
      const result = await res.json();
      if (!res.ok || !result.success) throw new Error(result.message || 'Không tính được chiết khấu');
      if (requestId !== pricingRequestId) return;

      const calculatedItems = Array.isArray(result.data?.items) ? result.data.items : [];
      state.items.forEach((item, index) => {
        item.discountAmount = Number(calculatedItems[index]?.discountAmount || 0);
      });
      renderItems();
      updateSummary();
      statusEl.hidden = true;
    } catch {
      if (requestId !== pricingRequestId) return;
      state.items.forEach((item) => { item.discountAmount = 0; });
      renderItems();
      updateSummary();
      statusEl.className = 'co-pricing-status co-pricing-status--error';
      statusEl.textContent = 'Chưa cập nhật được chiết khấu; tổng tiền đang hiển thị theo giá tạm tính.';
    }
  }

  /* ── Draft: save / load ─────────────────────────────────── */
  function buildDraftPayload() {
    return {
      dealerId: $('co-dealer').value || null,
      deliveryPointId: $('co-delivery-point').value || null,
      deliveryDate: $('co-delivery-date').value || null,
      note: $('co-note').value || '',
      items: state.items.map((it) => ({
        _key: it._key,
        productId: it.productId,
        sku: it.sku,
        name: it.name,
        unitConversions: it.unitConversions,
        selectedUnitId: it.selectedUnit?.id ?? it.selectedUnit?.unitName ?? null,
        qty: it.qty,
        unitPrice: it.unitPrice,
        discountAmount: it.discountAmount || 0,
      })),
      _seq: itemSeq,
    };
  }

  let draftSaveTimer = null;
  let draftSaveInFlight = false;

  async function saveDraft() {
    const res = await Auth.apiFetch('/orders/draft', {
      method: 'PUT',
      body: JSON.stringify(buildDraftPayload()),
    });
    const result = await res.json();
    if (!res.ok || !result.success) throw new Error(result.message || 'Không lưu được bản nháp');
  }

  function saveDraftSilently() {
    clearTimeout(draftSaveTimer);
    draftSaveTimer = setTimeout(async () => {
      if (draftSaveInFlight) return;
      draftSaveInFlight = true;
      try {
        await saveDraft();
      } catch {
        // Không chặn việc nhập đơn; nút Lưu nháp sẽ báo lỗi rõ ràng nếu cần.
      } finally {
        draftSaveInFlight = false;
      }
    }, 350);
  }

  async function hasDraft() {
    try {
      const res = await Auth.apiFetch('/orders/draft');
      const result = await res.json();
      return res.ok && result.success && !!result.data;
    } catch {
      return false;
    }
  }

  async function loadDraft() {
    try {
      const res = await Auth.apiFetch('/orders/draft');
      const result = await res.json();
      if (!res.ok || !result.success || !result.data) return false;
      const draft = result.data;
      // Restore form fields
      if (draft.dealerId) $('co-dealer').value = draft.dealerId;
      if (draft.deliveryDate) $('co-delivery-date').value = draft.deliveryDate;
      if (draft.note) $('co-note').value = draft.note;

      // Trigger dealer change to load delivery points
      if (draft.dealerId) {
        state.pendingDeliveryPointId = draft.deliveryPointId || null;
        $('co-dealer').dispatchEvent(new Event('change'));
      }

      // Restore items
      itemSeq = draft._seq || 0;
      state.items = (draft.items || []).map((it) => {
        const selected = (it.unitConversions || []).find(
          (u) => String(u.id ?? u.unitName) === String(it.selectedUnitId),
        ) || it.unitConversions?.[0] || { unitName: 'cái', conversionFactor: 1 };
        return {
          _key: it._key,
          productId: it.productId,
          sku: it.sku,
          name: it.name,
          unitConversions: it.unitConversions || [selected],
          selectedUnit: selected,
          qty: it.qty,
          basePrice: Number(it.unitPrice || 0) / (Number(selected.conversionFactor || 1) || 1),
          unitPrice: Number(it.unitPrice || 0),
          discountAmount: Number(it.discountAmount || it.discount || 0),
        };
      });

      renderItems();
      updateSummary();
      refreshPricingPreview();
      return true;
    } catch {
      return false;
    }
  }

  async function clearDraft() {
    try {
      await Auth.apiFetch('/orders/draft', { method: 'DELETE' });
    } catch {
      // Không làm gián đoạn luồng tạo đơn nếu thao tác dọn bản nháp thất bại.
    }
  }

  /* ── Draft banner ────────────────────────────────────────── */
  function showDraftBanner() {
    const existing = document.querySelector('.co-draft-banner');
    if (existing) return;
    const banner = document.createElement('div');
    banner.className = 'co-draft-banner';
    banner.innerHTML = `
      <svg viewBox="0 0 24 24" width="16" height="16"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
      <span>Bạn có đơn nháp chưa gửi.</span>
      <button type="button" id="btn-restore-draft">Tiếp tục soạn</button>
      <button type="button" id="btn-discard-draft" style="margin-left:8px;color:var(--ink-500)">Bỏ qua</button>
    `;
    const form = $('order-form');
    form.parentNode.insertBefore(banner, form);
    $('btn-load-draft').hidden = false;

    document.getElementById('btn-restore-draft').addEventListener('click', async () => {
      if (await loadDraft()) {
        banner.remove();
        $('btn-load-draft').hidden = true;
        showToast('Đã khôi phục đơn nháp.', 'success');
      } else {
        showToast('Không tải được bản nháp. Vui lòng thử lại.', 'error');
      }
    });

    document.getElementById('btn-discard-draft').addEventListener('click', async () => {
      await clearDraft();
      banner.remove();
      $('btn-load-draft').hidden = true;
    });
  }

  $('btn-load-draft').addEventListener('click', async () => {
    if (await loadDraft()) {
      document.querySelector('.co-draft-banner')?.remove();
      $('btn-load-draft').hidden = true;
      showToast('Đã khôi phục đơn nháp.', 'success');
    } else {
      showToast('Không tải được bản nháp. Vui lòng thử lại.', 'error');
    }
  });

  /* ── Save draft button ───────────────────────────────────── */
  $('btn-save-draft').addEventListener('click', async () => {
    if (!state.items.length) {
      showToast('Chưa có dòng hàng nào để lưu nháp.', 'warning');
      return;
    }
    const btn = $('btn-save-draft');
    btn.disabled = true;
    try {
      await saveDraft();
      showToast('Đã lưu nháp đơn hàng. Bạn có thể mở lại ở lần sau.', 'success');
    } catch (e) {
      showToast(e.message || 'Không lưu được bản nháp.', 'error');
    } finally {
      btn.disabled = false;
    }
  });

  /* ── Submit order ────────────────────────────────────────── */
  $('order-form').addEventListener('submit', (e) => {
    e.preventDefault();
    if (!validateForm()) return;
    openConfirmModal();
  });

  function validateForm() {
    if (!$('co-dealer').value) {
      showToast('Vui lòng chọn đại lý.', 'warning');
      $('co-dealer').focus();
      return false;
    }
    if (!$('co-delivery-date').value) {
      showToast('Vui lòng chọn ngày giao mong muốn.', 'warning');
      $('co-delivery-date').focus();
      return false;
    }
    if (state.deliveryPoints.length && !$('co-delivery-point').value) {
      showToast('Vui lòng chọn điểm giao hàng.', 'warning');
      $('co-delivery-point').focus();
      return false;
    }
    if (!state.items.length) {
      showToast('Vui lòng thêm ít nhất một dòng hàng.', 'warning');
      $('co-product-search').focus();
      return false;
    }
    return true;
  }

  function openConfirmModal() {
    const dealer = state.selectedDealer;
    let subtotal = 0;
    let totalDiscount = 0;
    state.items.forEach((item) => {
      subtotal += item.qty * item.unitPrice;
      totalDiscount += item.discountAmount || 0;
    });
    const grandTotal = Math.max(0, subtotal - totalDiscount);

    $('confirm-modal-sub').textContent = dealer
      ? `${dealer.code} — ${dealer.name}`
      : '';

    $('confirm-summary').innerHTML = `
      <div class="co-cs-row"><span>Số dòng hàng</span><span>${state.items.length} sản phẩm</span></div>
      <div class="co-cs-row"><span>Tổng tiền hàng</span><span>${fmtMoney(subtotal)}</span></div>
      <div class="co-cs-row"><span>Chiết khấu</span><span>${totalDiscount > 0 ? '−' + fmtMoney(totalDiscount) : '—'}</span></div>
      <div class="co-cs-row"><span>Tổng phải thu</span><span>${fmtMoney(grandTotal)}</span></div>
    `;

    $('confirm-msg').hidden = true;
    $('btn-confirm-order').disabled = false;
    $('btn-confirm-order').textContent = 'Xác nhận tạo đơn';
    $('confirm-modal').hidden = false;
  }

  function closeConfirmModal() {
    $('confirm-modal').hidden = true;
  }

  $('btn-close-confirm').addEventListener('click', closeConfirmModal);
  $('btn-cancel-confirm').addEventListener('click', closeConfirmModal);
  $('confirm-modal').addEventListener('click', (e) => {
    if (e.target === $('confirm-modal')) closeConfirmModal();
  });

  $('btn-confirm-order').addEventListener('click', async () => {
    const btn = $('btn-confirm-order');
    btn.disabled = true;
    btn.textContent = 'Đang tạo đơn…';

    try {
      const payload = buildOrderPayload();
      const res = await Auth.apiFetch('/orders', {
        method: 'POST',
        body: JSON.stringify(payload),
      });
      const result = await res.json();
      if (!res.ok || !result.success) throw new Error(result.message || 'Không tạo được đơn hàng');

      closeConfirmModal();
      clearDraft();
      showToast('Đơn hàng đã được tạo thành công!', 'success');

      // Reset form
      $('order-form').reset();
      state.items = [];
      state.selectedDealer = null;
      $('co-dealer-info').hidden = true;
      $('co-delivery-point').innerHTML = '<option value="">— Chọn điểm giao hàng —</option>';
      $('co-delivery-point').disabled = true;
      state.deliveryPoints = [];
      state.priceList = null;
      setDeliveryStatus('');
      $('co-pricing-status').hidden = true;
      renderItems();
      updateSummary();
    } catch (err) {
      const msgEl = $('confirm-msg');
      msgEl.textContent = err.message || 'Lỗi không xác định. Vui lòng thử lại.';
      msgEl.hidden = false;
      btn.disabled = false;
      btn.textContent = 'Thử lại';
    }
  });

  function buildOrderPayload() {
    const dealerId = $('co-dealer').value;
    const deliveryPointId = $('co-delivery-point').value || null;
    const deliveryDate = $('co-delivery-date').value || null;
    const note = $('co-note').value.trim() || null;

    return {
      dealerId: Number(dealerId),
      deliveryPointId: deliveryPointId ? Number(deliveryPointId) : null,
      expectedDeliveryDate: deliveryDate || null,
      note,
      items: state.items.map((it) => ({
        productId: it.productId,
        unit: it.selectedUnit?.unitName || it.selectedUnit?.unit || 'cái',
        quantity: it.qty,
      })),
    };
  }

  /* ── Keyboard shortcut: Escape ───────────────────────────── */
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (!$('confirm-modal').hidden) closeConfirmModal();
      if (!$('co-product-dropdown').hidden) {
        $('co-product-dropdown').hidden = true;
        productInput.value = '';
      }
    }
  });

  /* ── Auto-save on item changes ───────────────────────────── */
  // Listen for any form input change to auto-save draft
  $('order-form').addEventListener('change', () => {
    if (state.items.length > 0 || $('co-dealer').value) {
      saveDraftSilently();
    }
  });

  /* ── Init ────────────────────────────────────────────────── */
  async function init() {
    const today = todayInputValue();
    $('co-delivery-date').min = today;
    await loadDealers();

    // Show draft banner if draft exists
    if (await hasDraft()) {
      showDraftBanner();
    }
  }

  init();
})();
