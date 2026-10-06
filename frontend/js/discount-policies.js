/**
 * discount-policies.js — Chính sách chiết khấu theo sản lượng (S3-01).
 *
 * Nghiệp vụ:
 *  - Bậc chiết khấu theo số lượng mua của một SKU hoặc một nhóm hàng.
 *  - Chiết khấu tính theo phần trăm (%) hoặc số tiền cố định (đ) trên đơn vị.
 *  - Nhiều chính sách cùng áp dụng → hệ thống lấy bậc có lợi nhất cho khách.
 *
 * API (sẽ có ở Sprint 3 backend):
 *  GET    /discount-policies
 *  POST   /discount-policies
 *  PUT    /discount-policies/{id}
 *  DELETE /discount-policies/{id}
 *  GET    /products          (dùng lại, lấy danh sách SKU)
 *  GET    /categories        (dùng lại, lấy nhóm hàng)
 */
(() => {
  if (!Auth.requireLogin()) return;
  if (!Auth.hasAnyRole('ROLE_SALES_MANAGER', 'ROLE_ADMIN')) {
    window.location.href = '/error?status=403';
    return;
  }

  /* ─── Helpers ─── */
  const $ = (id) => document.getElementById(id);
  const escHtml = (v) =>
    String(v ?? '').replace(/[&<>'"]/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;',
    })[c]);
  const formatCurrency = (v) =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(v) || 0);
  const formatDate = (v) =>
    v ? new Intl.DateTimeFormat('vi-VN').format(new Date(`${v}T00:00:00`)) : '—';
  function localDate(d = new Date()) {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
  function defaultEndDate() {
    const d = new Date();
    d.setFullYear(d.getFullYear() + 1);
    return localDate(d);
  }

  /* ─── State ─── */
  const state = {
    policies: [],
    products: [],
    categories: [],
    editingId: null,
    loading: true,
    loadError: '',
    tiers: [],           // working tiers in the modal
  };

  /* ─── Status helper ─── */
  const STATUS_LABELS = {
    active: 'Đang hiệu lực',
    upcoming: 'Sắp hiệu lực',
    expired: 'Đã hết hiệu lực',
    inactive: 'Ngừng áp dụng',
  };
  const SCOPE_LABELS = { SKU: 'SKU cụ thể', CATEGORY: 'Nhóm hàng' };
  const TYPE_LABELS  = { PERCENT: 'Phần trăm (%)', FIXED: 'Số tiền cố định (đ)' };
  const CG_LABELS    = { DAI_LY_CAP_1: 'Đại lý cấp 1', DAI_LY_CAP_2: 'Đại lý cấp 2', KHACH_LE: 'Khách lẻ' };

  function policyStatus(p) {
    if (p.active === false) return 'inactive';
    const today = localDate();
    if (p.endDate < today) return 'expired';
    if (p.startDate > today) return 'upcoming';
    return 'active';
  }

  /* ─── Toast ─── */
  function showToast(message, type = 'success') {
    const el = document.createElement('div');
    el.className = `toast toast--${type}`;
    el.setAttribute('role', 'status');
    el.textContent = message;
    $('toast-container').appendChild(el);
    requestAnimationFrame(() => el.classList.add('toast--visible'));
    setTimeout(() => { el.classList.remove('toast--visible'); el.remove(); }, 4200);
  }

  /* ─── Form message ─── */
  const formMsg = $('disc-form-message');
  function showFormMsg(text = '') {
    formMsg.textContent = text;
    formMsg.hidden = !text;
  }

  /* ─── API wrapper ─── */
  async function apiFetch(path, opts = {}) {
    const res = await Auth.apiFetch(path, opts);
    const body = await res.json().catch(() => ({}));
    if (!res.ok || body.success === false) throw new Error(body.message || 'Lỗi máy chủ.');
    return body;
  }

  /* ─── Load data ─── */
  async function loadProducts() {
    try {
      const body = await apiFetch('/products');
      state.products = Array.isArray(body.data) ? body.data.filter((p) => p.active !== false) : [];
    } catch {
      state.products = [];
    }
    rebuildTargetOptions();
  }

  async function loadCategories() {
    try {
      const body = await apiFetch('/categories');
      state.categories = Array.isArray(body.data) ? body.data : [];
    } catch {
      state.categories = [];
    }
    rebuildTargetOptions();
  }

  async function loadPolicies() {
    state.loading = true;
    state.loadError = '';
    renderTable();
    try {
      const body = await apiFetch('/discount-policies');
      state.policies = Array.isArray(body.data) ? body.data : [];
    } catch (err) {
      state.policies = [];
      state.loadError = err.message || 'Không tải được chính sách chiết khấu.';
      showToast(state.loadError, 'error');
    } finally {
      state.loading = false;
      renderTable();
    }
  }

  /* ─── Overview stats ─── */
  function updateOverview() {
    $('disc-total-count').textContent  = state.policies.length;
    $('disc-active-count').textContent = state.policies.filter((p) => policyStatus(p) === 'active').length;
    $('disc-sku-count').textContent    = state.policies.filter((p) => p.scope === 'SKU').length;
    $('disc-group-count').textContent  = state.policies.filter((p) => p.scope === 'CATEGORY').length;
  }

  /* ─── Filters ─── */
  function filteredPolicies() {
    const kw     = $('disc-search').value.trim().toLocaleLowerCase('vi');
    const scope  = $('disc-scope-filter').value;
    const type   = $('disc-type-filter').value;
    const status = $('disc-status-filter').value;
    return state.policies.filter((p) => {
      const name = String(p.name || '').toLocaleLowerCase('vi');
      const code = String(p.code || '').toLocaleLowerCase('vi');
      if (kw && !name.includes(kw) && !code.includes(kw)) return false;
      if (scope  && p.scope !== scope)         return false;
      if (type   && p.discountType !== type)   return false;
      if (status && policyStatus(p) !== status) return false;
      return true;
    });
  }

  /* ─── Render table ─── */
  function renderTable() {
    updateOverview();

    if (state.loading) {
      $('disc-result-summary').textContent = 'Đang tải dữ liệu…';
      $('disc-policies-body').innerHTML = `
        <tr><td class="disc-empty" colspan="7">
          <strong>Đang tải chính sách</strong>
          <span>Vui lòng chờ dữ liệu từ hệ thống.</span>
        </td></tr>`;
      return;
    }
    if (state.loadError) {
      $('disc-result-summary').textContent = 'Không thể tải dữ liệu.';
      $('disc-policies-body').innerHTML = `
        <tr><td class="disc-empty" colspan="7">
          <strong>Không tải được chính sách</strong>
          <span>${escHtml(state.loadError)}</span>
        </td></tr>`;
      return;
    }

    const list = filteredPolicies();
    $('disc-result-summary').textContent = state.policies.length
      ? `Hiển thị ${list.length} / ${state.policies.length} chính sách đã lưu.`
      : 'Chưa có chính sách chiết khấu nào trong hệ thống.';

    if (!list.length) {
      const hasData = state.policies.length > 0;
      $('disc-policies-body').innerHTML = `
        <tr><td class="disc-empty" colspan="7">
          <strong>${hasData ? 'Không có kết quả' : 'Chưa có chính sách'}</strong>
          <span>${hasData
            ? 'Không tìm thấy chính sách phù hợp với bộ lọc.'
            : 'Chọn "Tạo chính sách" để thêm chính sách đầu tiên.'}</span>
        </td></tr>`;
      return;
    }

    $('disc-policies-body').innerHTML = list.map((p) => {
      const status    = policyStatus(p);
      const tiers     = Array.isArray(p.tiers) ? p.tiers : [];
      const scopeLabel = SCOPE_LABELS[p.scope] || p.scope;
      const typeClass  = p.discountType === 'PERCENT' ? 'disc-type-badge--percent' : 'disc-type-badge--fixed';
      const typeLabel  = TYPE_LABELS[p.discountType] || p.discountType;
      const cgLabel    = p.customerGroup ? CG_LABELS[p.customerGroup] || p.customerGroup : '';
      const bestTier   = tiers.length
        ? (p.discountType === 'PERCENT'
            ? `Tối đa ${Math.max(...tiers.map((t) => Number(t.discountValue)))}%`
            : `Tối đa ${formatCurrency(Math.max(...tiers.map((t) => Number(t.discountValue))))} / đơn vị`)
        : '—';

      return `
        <tr>
          <td>
            <span class="disc-policy-name">
              <strong>${escHtml(p.name)}</strong>
              <span>${escHtml(p.code)}</span>
            </span>
          </td>
          <td>
            <span class="disc-scope-badge">${escHtml(scopeLabel)}</span><br />
            <span style="font-size:.6875rem;color:var(--ink-600)">${escHtml(p.targetName || '')}</span>
          </td>
          <td>
            <span class="disc-type-badge ${escHtml(typeClass)}">${escHtml(typeLabel)}</span>
            ${cgLabel ? `<br /><span class="disc-group-badge">${escHtml(cgLabel)}</span>` : ''}
          </td>
          <td>
            <span class="disc-tiers-summary">
              <strong>${tiers.length} bậc</strong>
              <button class="disc-tiers-btn" data-policy-id="${p.id}" data-action="view-tiers" type="button">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                Xem bậc &nbsp;· ${escHtml(bestTier)}
              </button>
            </span>
          </td>
          <td>
            <span class="disc-period">
              <strong>${formatDate(p.startDate)} – ${formatDate(p.endDate)}</strong>
              <span>${p.note ? escHtml(p.note) : 'Không có ghi chú'}</span>
            </span>
          </td>
          <td><span class="disc-status disc-status--${status}">${STATUS_LABELS[status]}</span></td>
          <td>
            <span class="disc-actions">
              <button class="disc-action" data-policy-id="${p.id}" data-action="edit" type="button">Sửa</button>
              <button class="disc-action disc-action--danger" data-policy-id="${p.id}" data-action="delete" type="button">Xóa</button>
            </span>
          </td>
        </tr>`;
    }).join('');
  }

  /* ─── Target (SKU / Category) selector ─── */
  function rebuildTargetOptions() {
    const scope = $('disc-scope').value;
    const label = scope === 'CATEGORY' ? 'Nhóm hàng áp dụng' : 'SKU áp dụng';
    $('disc-target-label').textContent = label;
    const sel = $('disc-target');
    if (scope === 'CATEGORY') {
      const opts = state.categories.map(
        (c) => `<option value="${c.id}">${escHtml(c.name)}</option>`
      ).join('');
      sel.innerHTML = `<option value="">Chọn nhóm hàng</option>${opts}`;
    } else {
      const opts = state.products.map(
        (p) => `<option value="${p.id}">${escHtml(p.name)} (${escHtml(p.code)})</option>`
      ).join('');
      sel.innerHTML = `<option value="">Chọn SKU</option>${opts}`;
    }
  }

  /* ─── Tiers editor ─── */
  function renderTiersEditor() {
    const body  = $('disc-tiers-body');
    const hint  = $('disc-tiers-hint');
    const type  = $('disc-type').value;
    const unit  = type === 'PERCENT' ? '%' : 'đ';
    $('disc-value-header').textContent = type === 'PERCENT' ? 'Chiết khấu (%)' : 'Chiết khấu (đ/đơn vị)';

    if (!state.tiers.length) {
      body.innerHTML = '';
      hint.hidden = false;
      return;
    }
    hint.hidden = true;
    body.innerHTML = state.tiers.map((tier, i) => `
      <tr data-tier-index="${i}">
        <td><span class="disc-tier-num">${i + 1}</span></td>
        <td>
          <input
            class="disc-tier-input tier-qty"
            type="number"
            min="1"
            step="1"
            value="${escHtml(tier.minQuantity)}"
            placeholder="VD: 100"
            aria-label="Số lượng tối thiểu bậc ${i + 1}"
          />
        </td>
        <td>
          <input
            class="disc-tier-input tier-val"
            type="number"
            min="0"
            step="${type === 'PERCENT' ? '0.01' : '1000'}"
            value="${escHtml(tier.discountValue)}"
            placeholder="${type === 'PERCENT' ? 'VD: 5' : 'VD: 2000'}"
            aria-label="Chiết khấu bậc ${i + 1} (${unit})"
          />
        </td>
        <td>
          <button class="disc-remove-tier" data-tier-index="${i}" type="button" aria-label="Xoá bậc ${i + 1}">
            <svg viewBox="0 0 24 24" aria-hidden="true"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/><path d="M9 6V4h6v2"/></svg>
          </button>
        </td>
      </tr>`).join('');
  }

  function addTier() {
    const lastQty = state.tiers.length
      ? Number(state.tiers[state.tiers.length - 1].minQuantity) + 100
      : 50;
    state.tiers.push({ minQuantity: lastQty, discountValue: '' });
    renderTiersEditor();
    // focus new qty input
    const rows = $('disc-tiers-body').querySelectorAll('tr');
    const last = rows[rows.length - 1];
    if (last) last.querySelector('.tier-qty')?.focus();
  }

  function removeTier(index) {
    state.tiers.splice(index, 1);
    renderTiersEditor();
  }

  function syncTiersFromDOM() {
    const rows = [...$('disc-tiers-body').querySelectorAll('tr[data-tier-index]')];
    state.tiers = rows.map((row) => ({
      minQuantity:   row.querySelector('.tier-qty')?.value ?? '',
      discountValue: row.querySelector('.tier-val')?.value ?? '',
    }));
  }

  /* ─── Modal open/close ─── */
  const modal = $('disc-modal');
  const form  = $('disc-form');

  function openModal(policy = null) {
    form.reset();
    form.querySelectorAll('[aria-invalid="true"]').forEach((f) => f.removeAttribute('aria-invalid'));
    showFormMsg();
    state.editingId = policy?.id ?? null;
    state.tiers     = policy?.tiers ? JSON.parse(JSON.stringify(policy.tiers)) : [];

    $('disc-modal-title').textContent    = policy ? 'Cập nhật chính sách' : 'Tạo chính sách chiết khấu';
    $('disc-modal-subtitle').textContent = policy
      ? 'Chỉnh sửa phạm vi, loại chiết khấu hoặc bậc sản lượng.'
      : 'Khai báo phạm vi, loại chiết khấu và các bậc sản lượng.';
    $('disc-policy-id').value = policy?.id ?? '';
    $('disc-code').value      = policy?.code ?? '';
    $('disc-name').value      = policy?.name ?? '';
    $('disc-scope').value     = policy?.scope ?? '';
    rebuildTargetOptions();
    $('disc-target').value          = policy?.targetId ?? '';
    $('disc-type').value            = policy?.discountType ?? '';
    $('disc-customer-group').value  = policy?.customerGroup ?? '';
    $('disc-start-date').value      = policy?.startDate ?? localDate();
    $('disc-end-date').value        = policy?.endDate   ?? defaultEndDate();
    $('disc-note').value            = policy?.note ?? '';
    $('save-disc-policy').textContent = policy ? 'Lưu thay đổi' : 'Tạo chính sách';
    renderTiersEditor();

    modal.hidden = false;
    document.body.style.overflow = 'hidden';
    $('disc-code').focus();
  }

  function closeModal() {
    modal.hidden = true;
    state.editingId = null;
    state.tiers     = [];
    document.body.style.overflow = '';
  }

  /* ─── Tiers view modal ─── */
  const tiersModal = $('disc-tiers-modal');

  function openTiersModal(policy) {
    const tiers = Array.isArray(policy.tiers) ? policy.tiers : [];
    $('disc-tiers-modal-title').textContent    = policy.name;
    $('disc-tiers-modal-subtitle').textContent =
      `${SCOPE_LABELS[policy.scope] || policy.scope} · ${TYPE_LABELS[policy.discountType] || policy.discountType}`;

    const isPercent = policy.discountType === 'PERCENT';
    $('disc-view-value-header').textContent = isPercent ? 'Chiết khấu (%)' : 'Chiết khấu (đ/đơn vị)';

    $('disc-tiers-view-body').innerHTML = tiers.length
      ? tiers.map((t, i) => `
          <tr>
            <td><span class="disc-tier-num">${i + 1}</span></td>
            <td style="font-variant-numeric:tabular-nums">${Number(t.minQuantity).toLocaleString('vi-VN')} ${escHtml(policy.unit || 'đơn vị')} trở lên</td>
            <td style="font-variant-numeric:tabular-nums;font-weight:650;color:var(--teal-deep)">
              ${isPercent ? `${t.discountValue}%` : formatCurrency(t.discountValue)}
            </td>
          </tr>`).join('')
      : `<tr><td colspan="3" style="text-align:center;padding:24px;color:var(--ink-500)">Chưa có bậc nào.</td></tr>`;

    tiersModal.hidden = false;
    document.body.style.overflow = 'hidden';
  }

  function closeTiersModal() {
    tiersModal.hidden = true;
    document.body.style.overflow = '';
  }

  /* ─── Save ─── */
  async function savePolicy(e) {
    e.preventDefault();
    form.querySelectorAll('[aria-invalid="true"]').forEach((f) => f.removeAttribute('aria-invalid'));
    showFormMsg();
    syncTiersFromDOM();

    // Validate required fields
    const requiredIds = ['disc-code', 'disc-name', 'disc-scope', 'disc-target', 'disc-type', 'disc-start-date', 'disc-end-date'];
    const missing = requiredIds.map($).filter((f) => !f.value.trim());
    if (missing.length) {
      missing.forEach((f) => f.setAttribute('aria-invalid', 'true'));
      showFormMsg('Nhập đầy đủ mã, tên, phạm vi, loại chiết khấu và thời gian hiệu lực.');
      return;
    }
    if ($('disc-end-date').value < $('disc-start-date').value) {
      $('disc-end-date').setAttribute('aria-invalid', 'true');
      showFormMsg('Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.');
      return;
    }

    // Validate tiers
    if (!state.tiers.length) { showFormMsg('Thêm ít nhất một bậc chiết khấu.'); return; }
    const isPercent = $('disc-type').value === 'PERCENT';
    for (let i = 0; i < state.tiers.length; i++) {
      const t   = state.tiers[i];
      const qty = Number(t.minQuantity);
      const val = Number(t.discountValue);
      if (!t.minQuantity || !Number.isFinite(qty) || qty <= 0) {
        showFormMsg(`Bậc ${i + 1}: Số lượng tối thiểu phải là số nguyên dương.`);
        return;
      }
      if (t.discountValue === '' || !Number.isFinite(val) || val < 0) {
        showFormMsg(`Bậc ${i + 1}: Giá trị chiết khấu không hợp lệ.`);
        return;
      }
      if (isPercent && val > 100) {
        showFormMsg(`Bậc ${i + 1}: Phần trăm chiết khấu không vượt quá 100%.`);
        return;
      }
      if (i > 0 && qty <= Number(state.tiers[i - 1].minQuantity)) {
        showFormMsg(`Bậc ${i + 1}: Số lượng phải lớn hơn bậc trước (${state.tiers[i - 1].minQuantity}).`);
        return;
      }
    }

    // Resolve target name for local display
    const scope    = $('disc-scope').value;
    const targetId = $('disc-target').value;
    const targetName = scope === 'CATEGORY'
      ? (state.categories.find((c) => String(c.id) === targetId)?.name || '')
      : (state.products.find((p) => String(p.id) === targetId)?.name || '');

    const payload = {
      code:          $('disc-code').value.trim().toUpperCase(),
      name:          $('disc-name').value.trim(),
      scope,
      targetId:      Number(targetId) || null,
      targetName,
      discountType:  $('disc-type').value,
      customerGroup: $('disc-customer-group').value || null,
      startDate:     $('disc-start-date').value,
      endDate:       $('disc-end-date').value,
      note:          $('disc-note').value.trim(),
      active:        true,
      tiers:         state.tiers.map((t) => ({
        minQuantity:   Number(t.minQuantity),
        discountValue: Number(t.discountValue),
      })),
    };

    const btn = $('save-disc-policy');
    const origText = btn.textContent;
    btn.disabled = true;
    btn.textContent = 'Đang lưu…';

    try {
      let result;
      if (state.editingId) {
        result = await apiFetch(`/discount-policies/${state.editingId}`, {
          method: 'PUT',
          body: JSON.stringify(payload),
        });
      } else {
        result = await apiFetch('/discount-policies', {
          method: 'POST',
          body: JSON.stringify(payload),
        });
      }
      closeModal();
      await loadPolicies();
      showToast(result.message || 'Đã lưu chính sách chiết khấu.', 'success');
    } catch (err) {
      showFormMsg(err.message || 'Không lưu được chính sách chiết khấu.');
    } finally {
      btn.disabled  = false;
      btn.textContent = origText;
    }
  }

  /* ─── Delete ─── */
  async function deletePolicy(policy, btn) {
    if (!window.confirm(`Xóa chính sách "${policy.name}"? Hành động này không thể hoàn tác.`)) return;
    const origText = btn.textContent;
    btn.disabled   = true;
    btn.textContent = 'Đang xóa…';
    try {
      const result = await apiFetch(`/discount-policies/${policy.id}`, { method: 'DELETE' });
      await loadPolicies();
      showToast(result.message || 'Đã xóa chính sách.', 'success');
    } catch (err) {
      showToast(err.message || 'Không xóa được chính sách.', 'error');
      btn.disabled   = false;
      btn.textContent = origText;
    }
  }

  /* ─── Events ─── */
  form.addEventListener('submit', savePolicy);

  $('create-policy').addEventListener('click', () => openModal());

  document.querySelectorAll('[data-close-disc-modal]').forEach((b) =>
    b.addEventListener('click', closeModal)
  );
  document.querySelectorAll('[data-close-tiers-modal]').forEach((b) =>
    b.addEventListener('click', closeTiersModal)
  );
  modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });
  tiersModal.addEventListener('click', (e) => { if (e.target === tiersModal) closeTiersModal(); });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (!tiersModal.hidden) closeTiersModal();
      else if (!modal.hidden) closeModal();
    }
  });

  // Scope change → rebuild target list + update label
  $('disc-scope').addEventListener('change', () => {
    rebuildTargetOptions();
    $('disc-target').value = '';
  });

  // Type change → re-render tiers header unit
  $('disc-type').addEventListener('change', () => renderTiersEditor());

  // Add tier button
  $('add-tier').addEventListener('click', () => {
    syncTiersFromDOM();
    addTier();
  });

  // Tier table: remove or live-sync
  $('disc-tiers-body').addEventListener('click', (e) => {
    const btn = e.target.closest('.disc-remove-tier');
    if (!btn) return;
    syncTiersFromDOM();
    removeTier(Number(btn.dataset.tierIndex));
  });
  $('disc-tiers-body').addEventListener('input', () => syncTiersFromDOM());

  // Table row actions
  $('disc-policies-body').addEventListener('click', (e) => {
    const btn = e.target.closest('[data-action]');
    if (!btn) return;
    const policy = state.policies.find((p) => String(p.id) === String(btn.dataset.policyId));
    if (!policy) return;
    if (btn.dataset.action === 'edit')        openModal(policy);
    if (btn.dataset.action === 'delete')      deletePolicy(policy, btn);
    if (btn.dataset.action === 'view-tiers')  openTiersModal(policy);
  });

  // Filters
  [$('disc-search'), $('disc-scope-filter'), $('disc-type-filter'), $('disc-status-filter')].forEach((ctrl) => {
    ctrl.addEventListener(ctrl.tagName === 'INPUT' ? 'input' : 'change', renderTable);
  });
  $('clear-disc-filters').addEventListener('click', () => {
    $('disc-search').value        = '';
    $('disc-scope-filter').value  = '';
    $('disc-type-filter').value   = '';
    $('disc-status-filter').value = '';
    renderTable();
  });

  /* ─── Boot ─── */
  Promise.allSettled([loadProducts(), loadCategories(), loadPolicies()]);
})();
