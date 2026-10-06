/**
 * dealers.js — S3-03 Quản lý hồ sơ đại lý.
 *
 * Tính năng:
 * - Danh sách đại lý với tìm kiếm và lọc theo nhóm KH + trạng thái
 * - Thêm / Sửa đại lý (mã, tên, MST, nhóm KH, khu vực, người phụ trách, ...)
 * - Đại lý đã phát sinh giao dịch → KHÔNG xoá được, chỉ ngừng giao dịch
 * - Khôi phục giao dịch (ACTIVE ↔ INACTIVE)
 * - Phân trang phía client
 *
 * API endpoints:
 *   GET    /dealers?page=0&size=200   → { success, data: { content, last } }
 *   POST   /dealers                   → create
 *   PUT    /dealers/:id               → update
 *   PATCH  /dealers/:id/status?active=true|false
 */
(() => {
  /* ── Auth guard ─────────────────────────────────────────── */
  if (!Auth.requireLogin()) return;

  if (!Auth.hasAnyRole('ROLE_ADMIN', 'ROLE_SALES_MANAGER', 'ROLE_ACCOUNTANT', 'ROLE_VIEWER')) {
    location.href = '/error?status=403';
    return;
  }
  const CAN_MANAGE = Auth.hasAnyRole('ROLE_ADMIN', 'ROLE_SALES_MANAGER', 'ROLE_ACCOUNTANT');

  /* ── DOM helpers ────────────────────────────────────────── */
  const $ = (id) => document.getElementById(id);
  const escHtml = (v) =>
    String(v ?? '').replace(/[&<>'"]/g, (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c],
    );

  /* ── Customer group labels & badge classes ──────────────── */
  const GROUP_LABELS = {
    DAI_LY_CAP_1: 'Đại lý cấp 1',
    DAI_LY_CAP_2: 'Đại lý cấp 2',
    KHACH_LE:     'Khách lẻ',
  };

  const GROUP_BADGE_CLASS = {
    DAI_LY_CAP_1: 'group-badge--cap1',
    DAI_LY_CAP_2: 'group-badge--cap2',
    KHACH_LE:     'group-badge--le',
  };

  /* ── State ──────────────────────────────────────────────── */
  const PAGE_SIZE = 20;

  const state = {
    /** @type {Array} all loaded dealers */
    all: [],
    /** filtered + searched subset */
    filtered: [],
    /** current page (1-indexed) */
    page: 1,
    searchQuery: '',
    groupFilter: '',
    statusFilter: '',
  };

  /* ── Sidebar / layout ───────────────────────────────────── */
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
      a.className = 'sidebar__link' + (item.id === 'dealers' ? ' sidebar__link--active' : '');
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

  /* ── Filter & render ────────────────────────────────────── */
  function applyFilter() {
    const q = state.searchQuery.toLowerCase().trim();
    const g = state.groupFilter;
    const s = state.statusFilter;

    state.filtered = state.all.filter((dlr) => {
      const matchSearch =
        !q ||
        (dlr.name || '').toLowerCase().includes(q) ||
        (dlr.code || '').toLowerCase().includes(q) ||
        (dlr.taxCode || '').toLowerCase().includes(q);
      const matchGroup  = !g || dlr.customerGroup === g;
      const matchStatus = !s || Boolean(dlr.active) === (s === 'ACTIVE');
      return matchSearch && matchGroup && matchStatus;
    });

    state.page = 1;
    renderTable();
    renderPagination();
  }

  function groupBadge(group) {
    if (!group) return '<span class="group-badge group-badge--unknown">—</span>';
    const label = GROUP_LABELS[group] || group;
    const cls   = GROUP_BADGE_CLASS[group] || 'group-badge--unknown';
    return `<span class="group-badge ${cls}">${escHtml(label)}</span>`;
  }

  function renderTable() {
    const tbody = $('dlr-tbody');
    const start = (state.page - 1) * PAGE_SIZE;
    const slice = state.filtered.slice(start, start + PAGE_SIZE);

    if (!slice.length) {
      tbody.innerHTML = `
        <tr>
          <td colspan="8">
            <div class="dlr-empty">
              <svg width="48" height="48" viewBox="0 0 24 24" stroke-width="1.2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
              <p>${state.all.length === 0 ? 'Chưa có đại lý nào. Bấm «Thêm đại lý» để bắt đầu.' : 'Không tìm thấy đại lý phù hợp.'}</p>
            </div>
          </td>
        </tr>`;
      return;
    }

    tbody.innerHTML = slice.map((dlr) => {
      const isActive = Boolean(dlr.active);

      const statusBadge = isActive
        ? `<span class="status-badge status-badge--active"><span class="status-badge__dot"></span>Đang giao dịch</span>`
        : `<span class="status-badge status-badge--inactive"><span class="status-badge__dot"></span>Ngừng giao dịch</span>`;

      const actions = CAN_MANAGE
        ? `<div class="dlr-actions">
            <button class="dlr-action-btn" data-action="edit" data-id="${dlr.id}" title="Sửa">
              <svg viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            </button>
            ${isActive
              ? `<button class="dlr-action-btn dlr-action-btn--warn" data-action="deactivate" data-id="${dlr.id}" title="Ngừng giao dịch">
                  <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
                </button>`
              : `<button class="dlr-action-btn dlr-action-btn--success" data-action="activate" data-id="${dlr.id}" title="Khôi phục giao dịch">
                  <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
                </button>`}
           </div>`
        : '';

      return `
        <tr>
          <td><span class="dlr-code">${escHtml(dlr.code)}</span></td>
          <td>
            <div class="dlr-name" title="${escHtml(dlr.name)}">${escHtml(dlr.name)}</div>
            ${dlr.phone ? `<div class="dlr-phone">${escHtml(dlr.phone)}</div>` : ''}
          </td>
          <td>${escHtml(dlr.taxCode || '—')}</td>
          <td>${groupBadge(dlr.customerGroup)}</td>
          <td><span class="dlr-region-text">${escHtml(dlr.region || '—')}</span></td>
          <td><span class="dlr-owner-text">${escHtml(dlr.owner || '—')}</span></td>
          <td>${statusBadge}</td>
          <td>${actions}</td>
        </tr>`;
    }).join('');

    // Bind action buttons
    tbody.querySelectorAll('[data-action="edit"]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const dlr = state.all.find((d) => d.id === Number(btn.dataset.id));
        if (dlr) openDealerModal(dlr);
      });
    });

    tbody.querySelectorAll('[data-action="deactivate"]').forEach((btn) => {
      btn.addEventListener('click', () => openStatusModal(Number(btn.dataset.id), 'INACTIVE'));
    });

    tbody.querySelectorAll('[data-action="activate"]').forEach((btn) => {
      btn.addEventListener('click', () => openStatusModal(Number(btn.dataset.id), 'ACTIVE'));
    });
  }

  /* ── Pagination ─────────────────────────────────────────── */
  function renderPagination() {
    const el = $('dlr-pagination');
    const total      = state.filtered.length;
    const totalPages = Math.ceil(total / PAGE_SIZE);

    if (totalPages <= 1) {
      el.hidden = true;
      return;
    }

    el.hidden = false;
    const start = (state.page - 1) * PAGE_SIZE + 1;
    const end   = Math.min(state.page * PAGE_SIZE, total);

    const pageButtons = [];
    for (let p = 1; p <= totalPages; p++) {
      pageButtons.push(
        `<button class="dlr-page-btn${p === state.page ? ' dlr-page-btn--active' : ''}" data-page="${p}">${p}</button>`,
      );
    }

    el.innerHTML = `
      <span class="dlr-pagination__info">${start}–${end} / ${total} đại lý</span>
      <div class="dlr-pagination__controls">
        <button class="dlr-page-btn" data-page="${state.page - 1}" ${state.page === 1 ? 'disabled' : ''}>‹</button>
        ${pageButtons.join('')}
        <button class="dlr-page-btn" data-page="${state.page + 1}" ${state.page === totalPages ? 'disabled' : ''}>›</button>
      </div>`;

    el.querySelectorAll('[data-page]').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.page = Number(btn.dataset.page);
        renderTable();
        renderPagination();
      });
    });
  }

  /* ── Load all dealers ───────────────────────────────────── */
  async function loadDealers() {
    $('dlr-tbody').innerHTML = `<tr class="dlr-loading-row"><td colspan="8"><div class="dlr-loading">Đang tải dữ liệu…</div></td></tr>`;

    try {
      const dealers = [];
      let page = 0;
      let last = false;
      do {
        const res    = await Auth.apiFetch(`/dealers?page=${page}&size=200`);
        const result = await res.json();
        if (!res.ok || !result.success || !Array.isArray(result.data?.content)) {
          throw new Error(result.message || 'Không tải được dữ liệu');
        }
        dealers.push(...result.data.content);
        last = result.data.last !== false;
        page++;
      } while (!last);

      state.all = dealers;
      applyFilter();
    } catch (e) {
      $('dlr-tbody').innerHTML = `
        <tr><td colspan="8"><div class="dlr-empty">
          <p>${escHtml(e.message || 'Lỗi tải dữ liệu')}</p>
        </div></td></tr>`;
      showToast(e.message || 'Không tải được danh sách đại lý', 'error');
    }
  }

  /* ── Dealer modal ───────────────────────────────────────── */
  function openDealerModal(dlr = null) {
    if (!CAN_MANAGE) {
      showToast('Bạn không có quyền quản lý đại lý.', 'error');
      return;
    }

    $('dealer-form').reset();
    $('dealer-form-message').hidden = true;

    if (dlr) {
      $('dealer-modal-title').textContent = 'Sửa đại lý';
      $('dealer-modal-sub').textContent   = 'Cập nhật thông tin đại lý.';
      $('dlr-id').value        = dlr.id;
      $('dlr-code').value      = dlr.code ?? '';
      $('dlr-code').disabled   = true; // Mã không thay đổi sau khi tạo
      $('dlr-name').value      = dlr.name ?? '';
      $('dlr-tax').value       = dlr.taxCode ?? '';
      $('dlr-group').value     = dlr.customerGroup ?? '';
      $('dlr-region').value    = dlr.region ?? '';
      $('dlr-owner').value     = dlr.owner ?? '';
      $('dlr-phone').value     = dlr.phone ?? '';
      $('dlr-address').value   = dlr.address ?? '';
      $('dlr-notes').value     = dlr.notes ?? '';
    } else {
      $('dealer-modal-title').textContent = 'Thêm đại lý';
      $('dealer-modal-sub').textContent   = 'Khai báo thông tin đại lý mới.';
      $('dlr-id').value      = '';
      $('dlr-code').disabled = false;
    }

    $('dealer-modal').hidden = false;
    setTimeout(() => $('dlr-name').focus(), 50);
  }

  function closeDealerModal() {
    $('dealer-modal').hidden = true;
    $('dlr-code').disabled   = false;
  }

  $('dealer-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = $('dlr-id').value;

    const payload = {
      code:          $('dlr-code').value.trim().toUpperCase(),
      name:          $('dlr-name').value.trim(),
      taxCode:       $('dlr-tax').value.trim() || null,
      customerGroup: $('dlr-group').value || null,
      region:        $('dlr-region').value.trim() || null,
      owner:         $('dlr-owner').value.trim() || null,
      phone:         $('dlr-phone').value.trim() || null,
      address:       $('dlr-address').value.trim() || null,
      notes:         $('dlr-notes').value.trim() || null,
    };

    if (!payload.name) {
      showFormError('dealer-form-message', 'Vui lòng nhập tên đại lý.');
      return;
    }
    if (!id && !payload.code) {
      showFormError('dealer-form-message', 'Vui lòng nhập mã đại lý.');
      return;
    }
    if (!payload.customerGroup) {
      showFormError('dealer-form-message', 'Vui lòng chọn nhóm khách hàng.');
      return;
    }

    const btn = $('btn-save-dealer');
    btn.disabled    = true;
    btn.textContent = 'Đang lưu…';

    try {
      const res    = await Auth.apiFetch(
        id ? `/dealers/${id}` : '/dealers',
        { method: id ? 'PUT' : 'POST', body: JSON.stringify(payload) },
      );
      const result = await res.json();
      if (!res.ok || !result.success) throw new Error(result.message || 'Không lưu được');

      closeDealerModal();
      showToast(id ? 'Đã cập nhật đại lý.' : 'Đã thêm đại lý mới.', 'success');
      await loadDealers();
    } catch (err) {
      showFormError('dealer-form-message', err.message || 'Lỗi không xác định');
    } finally {
      btn.disabled    = false;
      btn.textContent = 'Lưu';
    }
  });

  /* ── Status (ngừng / khôi phục) modal ──────────────────── */
  function openStatusModal(dlrId, targetStatus) {
    if (!CAN_MANAGE) {
      showToast('Bạn không có quyền thay đổi trạng thái đại lý.', 'error');
      return;
    }

    const dlr = state.all.find((d) => d.id === dlrId);
    if (!dlr) return;

    const isDeactivate = targetStatus === 'INACTIVE';
    $('dlr-status-modal-title').textContent = isDeactivate ? 'Ngừng giao dịch' : 'Khôi phục giao dịch';
    $('dlr-status-modal-sub').textContent   = dlr.name;
    $('dlr-status-modal-body').textContent  = isDeactivate
      ? `Đại lý «${dlr.name}» sẽ bị ngừng giao dịch. Các hoá đơn hiện có sẽ không bị ảnh hưởng. Bạn có thể khôi phục bất kỳ lúc nào.`
      : `Khôi phục giao dịch với đại lý «${dlr.name}». Đại lý sẽ trở lại trạng thái đang giao dịch.`;

    $('dlr-status-id').value = dlrId;
    $('dlr-status-target').value = targetStatus;
    $('dlr-status-form-message').hidden = true;

    const confirmBtn = $('btn-confirm-dlr-status');
    confirmBtn.textContent = isDeactivate ? 'Ngừng giao dịch' : 'Khôi phục';

    $('dlr-status-modal').hidden = false;
  }

  function closeStatusModal() {
    $('dlr-status-modal').hidden = true;
  }

  $('btn-confirm-dlr-status').addEventListener('click', async () => {
    const dlrId        = Number($('dlr-status-id').value);
    const targetStatus = $('dlr-status-target').value;

    const btn = $('btn-confirm-dlr-status');
    btn.disabled    = true;
    btn.textContent = 'Đang xử lý…';

    try {
      const res    = await Auth.apiFetch(`/dealers/${dlrId}/status?active=${targetStatus === 'ACTIVE'}`, {
        method: 'PATCH',
      });
      const result = await res.json().catch(() => ({}));
      if (!res.ok || !result.success) throw new Error(result.message || 'Không thay đổi được trạng thái');

      closeStatusModal();
      showToast(
        targetStatus === 'INACTIVE' ? 'Đã ngừng giao dịch đại lý.' : 'Đã khôi phục giao dịch.',
        'success',
      );
      await loadDealers();
    } catch (err) {
      showFormError('dlr-status-form-message', err.message || 'Lỗi không xác định');
    } finally {
      btn.disabled    = false;
      btn.textContent = targetStatus === 'INACTIVE' ? 'Ngừng giao dịch' : 'Khôi phục';
    }
  });

  /* ── Form error helper ──────────────────────────────────── */
  function showFormError(elId, text) {
    const el       = $(elId);
    el.hidden      = false;
    el.textContent = text;
  }

  /* ── Show add button for managers ───────────────────────── */
  if (CAN_MANAGE) $('btn-add-dealer').hidden = false;

  /* ── Event bindings ─────────────────────────────────────── */

  $('btn-add-dealer')?.addEventListener('click', () => openDealerModal(null));

  // Search
  let searchTimer;
  $('dlr-search').addEventListener('input', () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      state.searchQuery = $('dlr-search').value;
      applyFilter();
    }, 200);
  });

  // Group filter
  $('dlr-filter-group').addEventListener('change', () => {
    state.groupFilter = $('dlr-filter-group').value;
    applyFilter();
  });

  // Status filter
  $('dlr-filter-status').addEventListener('change', () => {
    state.statusFilter = $('dlr-filter-status').value;
    applyFilter();
  });

  // Modal close — dealer form
  document.querySelectorAll('[data-close-dealer]').forEach((btn) =>
    btn.addEventListener('click', closeDealerModal),
  );
  $('dealer-modal').addEventListener('click', (e) => {
    if (e.target === $('dealer-modal')) closeDealerModal();
  });

  // Modal close — status
  document.querySelectorAll('[data-close-dlr-status]').forEach((btn) =>
    btn.addEventListener('click', closeStatusModal),
  );
  $('dlr-status-modal').addEventListener('click', (e) => {
    if (e.target === $('dlr-status-modal')) closeStatusModal();
  });

  // Keyboard
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (!$('dealer-modal').hidden)     closeDealerModal();
      if (!$('dlr-status-modal').hidden) closeStatusModal();
    }
  });

  /* ── Init ───────────────────────────────────────────────── */
  loadDealers();
})();
