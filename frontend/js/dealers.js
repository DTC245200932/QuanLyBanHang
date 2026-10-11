/**
 * dealers.js — S3-03 / S3-04 / S3-08 Quản lý đại lý và điểm giao hàng.
 *
 * Tính năng:
 * - Dành cho Nhân viên kinh doanh (ROLE_SALES), Quản lý kinh doanh, Kế toán, Admin, Viewer
 * - Tìm nhanh theo mã, tên, số điện thoại (hỗ trợ gọi ngay trên di động khi đang đứng ngoài đường)
 * - Lọc theo khu vực, nhóm khách hàng, người phụ trách, trạng thái
 * - Quick chips: Tất cả, Khách của tôi, Đang giao dịch
 * - Thêm / Sửa đại lý & Ngừng / Khôi phục giao dịch (ROLE_ADMIN, ROLE_SALES_MANAGER, ROLE_ACCOUNTANT)
 * - Khai báo nhiều điểm giao hàng, chọn mặc định và gắn vào đơn hàng (S3-04)
 * - Phân trang phía client, tải danh mục tham chiếu từ /dealers/options
 */
(() => {
  /* ── Auth guard ─────────────────────────────────────────── */
  if (!Auth.requireLogin()) return;

  if (!Auth.hasAnyRole('ROLE_ADMIN', 'ROLE_SALES_MANAGER', 'ROLE_ACCOUNTANT', 'ROLE_SALES', 'ROLE_VIEWER')) {
    location.href = '/error?status=403';
    return;
  }
  const CAN_MANAGE = Auth.hasAnyRole('ROLE_ADMIN', 'ROLE_SALES_MANAGER', 'ROLE_ACCOUNTANT');
  const CAN_MANAGE_DELIVERY_POINTS = Auth.hasAnyRole(
    'ROLE_ADMIN', 'ROLE_SALES_MANAGER', 'ROLE_ACCOUNTANT', 'ROLE_SALES',
  );

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
    /** metadata from /dealers/options */
    options: {
      customerGroups: [],
      territories: [],
      assignedUsers: [],
    },
    /** current page (1-indexed) */
    page: 1,
    searchQuery: '',
    regionFilter: '',
    groupFilter: '',
    ownerFilter: '',
    statusFilter: '',
    activeChip: 'all',
    deliveryPoints: [],
    deliveryDealerId: null,
    editingDeliveryPointId: null,
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

  /* ── Load reference options (/dealers/options) ──────────── */
  async function loadOptions() {
    try {
      const res = await Auth.apiFetch('/dealers/options');
      const result = await res.json();
      if (res.ok && result.success && result.data) {
        state.options = result.data;
      }
    } catch (e) {
      console.warn('Không tải được danh mục tham chiếu đại lý:', e);
    }
    populateFilterDropdowns();
    populateModalDropdowns();
  }

  /* ── Populate filter dropdowns ──────────────────────────── */
  function populateFilterDropdowns() {
    // 1. Khu vực (Region / Territory)
    const regionSelect = $('dlr-filter-region');
    if (regionSelect) {
      const curVal = regionSelect.value;
      const territoryMap = new Map();

      // From options API
      (state.options.territories || []).forEach((t) => {
        if (t && t.name) territoryMap.set(String(t.id ?? t.name), t.name);
      });

      // From loaded dealers (fallback & supplement)
      state.all.forEach((d) => {
        const key = d.territoryId ? String(d.territoryId) : (d.territoryName || d.region || '');
        const name = d.territoryName || d.region || '';
        if (key && name && !territoryMap.has(key)) {
          territoryMap.set(key, name);
        }
      });

      const optionsHtml = ['<option value="">Tất cả khu vực</option>'];
      territoryMap.forEach((name, val) => {
        optionsHtml.push(`<option value="${escHtml(val)}">${escHtml(name)}</option>`);
      });
      regionSelect.innerHTML = optionsHtml.join('');
      if (curVal && territoryMap.has(curVal)) {
        regionSelect.value = curVal;
      }
    }

    // 2. Người phụ trách (Assigned User / Owner)
    const ownerSelect = $('dlr-filter-owner');
    if (ownerSelect) {
      const curVal = ownerSelect.value;
      const userMap = new Map();

      // From options API
      (state.options.assignedUsers || []).forEach((u) => {
        if (u) {
          const key = String(u.id ?? u.username);
          const name = u.fullName || u.username;
          if (key && name) userMap.set(key, name);
        }
      });

      // From loaded dealers (fallback & supplement)
      state.all.forEach((d) => {
        const key = d.assignedUserId ? String(d.assignedUserId) : (d.assignedUserFullName || d.owner || d.assignedUsername || '');
        const name = d.assignedUserFullName || d.owner || d.assignedUsername || '';
        if (key && name && !userMap.has(key)) {
          userMap.set(key, name);
        }
      });

      const optionsHtml = ['<option value="">Tất cả người phụ trách</option>'];
      userMap.forEach((name, val) => {
        optionsHtml.push(`<option value="${escHtml(val)}">${escHtml(name)}</option>`);
      });
      ownerSelect.innerHTML = optionsHtml.join('');
      if (curVal && (curVal === '__ME__' || userMap.has(curVal))) {
        ownerSelect.value = curVal;
      }
    }
  }

  /* ── Populate modal dropdowns ───────────────────────────── */
  function populateModalDropdowns() {
    // Modal Khu vực
    const modalRegion = $('dlr-region');
    if (modalRegion && modalRegion.tagName === 'SELECT') {
      const territoryMap = new Map();
      (state.options.territories || []).forEach((t) => {
        if (t && t.name) territoryMap.set(String(t.id ?? t.name), t.name);
      });
      state.all.forEach((d) => {
        const key = d.territoryId ? String(d.territoryId) : (d.territoryName || d.region || '');
        const name = d.territoryName || d.region || '';
        if (key && name && !territoryMap.has(key)) territoryMap.set(key, name);
      });
      const opts = ['<option value="">— Chọn khu vực —</option>'];
      territoryMap.forEach((name, val) => {
        opts.push(`<option value="${escHtml(val)}">${escHtml(name)}</option>`);
      });
      modalRegion.innerHTML = opts.join('');
    }

    // Modal Người phụ trách
    const modalOwner = $('dlr-owner');
    if (modalOwner && modalOwner.tagName === 'SELECT') {
      const userMap = new Map();
      (state.options.assignedUsers || []).forEach((u) => {
        if (u) {
          const key = String(u.id ?? u.username);
          const name = u.fullName || u.username;
          if (key && name) userMap.set(key, name);
        }
      });
      state.all.forEach((d) => {
        const key = d.assignedUserId ? String(d.assignedUserId) : (d.assignedUserFullName || d.owner || d.assignedUsername || '');
        const name = d.assignedUserFullName || d.owner || d.assignedUsername || '';
        if (key && name && !userMap.has(key)) userMap.set(key, name);
      });
      const opts = ['<option value="">— Chọn người phụ trách —</option>'];
      userMap.forEach((name, val) => {
        opts.push(`<option value="${escHtml(val)}">${escHtml(name)}</option>`);
      });
      modalOwner.innerHTML = opts.join('');
    }
  }

  /* ── Filter & Search matching rules ─────────────────────── */
  function matchesSearch(dlr, query) {
    if (!query) return true;
    const q = query.toLowerCase().trim();
    if (!q) return true;

    // 1. Mã đại lý
    if ((dlr.code || '').toLowerCase().includes(q)) return true;

    // 2. Tên đại lý
    if ((dlr.name || '').toLowerCase().includes(q)) return true;

    // 3. Số điện thoại (tìm nhanh theo chuỗi hoặc định dạng số)
    const phone = String(dlr.phone || '');
    if (phone.toLowerCase().includes(q)) return true;
    const qDigits = q.replace(/\D/g, '');
    const phoneDigits = phone.replace(/\D/g, '');
    if (qDigits && phoneDigits && phoneDigits.includes(qDigits)) return true;

    // 4. Mã số thuế
    if ((dlr.taxCode || '').toLowerCase().includes(q)) return true;

    return false;
  }

  function matchesRegion(dlr, regionVal) {
    if (!regionVal) return true;
    const target = String(regionVal).toLowerCase();
    if (dlr.territoryId && String(dlr.territoryId) === regionVal) return true;
    if (dlr.territoryCode && dlr.territoryCode.toLowerCase() === target) return true;
    if (dlr.territoryName && dlr.territoryName.toLowerCase() === target) return true;
    if (dlr.region && dlr.region.toLowerCase() === target) return true;
    return false;
  }

  function matchesGroup(dlr, groupVal) {
    if (!groupVal) return true;
    return dlr.customerGroup === groupVal;
  }

  function matchesOwner(dlr, ownerVal) {
    if (!ownerVal) return true;
    if (ownerVal === '__ME__') {
      const curUser = Auth.getUser?.();
      if (!curUser) return true;
      if (dlr.assignedUserId && curUser.id && dlr.assignedUserId === curUser.id) return true;
      if (dlr.assignedUsername && curUser.username && dlr.assignedUsername.toLowerCase() === curUser.username.toLowerCase()) return true;
      if (dlr.assignedUserFullName && curUser.fullName && dlr.assignedUserFullName.toLowerCase() === curUser.fullName.toLowerCase()) return true;
      if (dlr.owner && curUser.fullName && dlr.owner.toLowerCase() === curUser.fullName.toLowerCase()) return true;
      return false;
    }
    const target = String(ownerVal).toLowerCase();
    if (dlr.assignedUserId && String(dlr.assignedUserId) === ownerVal) return true;
    if (dlr.assignedUsername && dlr.assignedUsername.toLowerCase() === target) return true;
    if (dlr.assignedUserFullName && dlr.assignedUserFullName.toLowerCase() === target) return true;
    if (dlr.owner && dlr.owner.toLowerCase() === target) return true;
    return false;
  }

  function matchesStatus(dlr, statusVal) {
    if (!statusVal) return true;
    if (statusVal === 'LOCKED') return Boolean(dlr.locked);
    if (statusVal === 'ACTIVE') return Boolean(dlr.active) && !dlr.locked;
    return !dlr.active;
  }

  /* ── Filter application & toolbar meta ──────────────────── */
  function applyFilter() {
    state.filtered = state.all.filter((dlr) => {
      return (
        matchesSearch(dlr, state.searchQuery) &&
        matchesRegion(dlr, state.regionFilter) &&
        matchesGroup(dlr, state.groupFilter) &&
        matchesOwner(dlr, state.ownerFilter) &&
        matchesStatus(dlr, state.statusFilter)
      );
    });

    state.page = 1;
    renderTable();
    renderPagination();
    updateToolbarMeta();
  }

  function updateToolbarMeta() {
    const hasFilter = Boolean(
      state.searchQuery ||
      state.regionFilter ||
      state.groupFilter ||
      state.ownerFilter ||
      state.statusFilter
    );

    const resetBtn = $('btn-reset-filters');
    if (resetBtn) resetBtn.hidden = !hasFilter;

    const countText = $('dlr-count-text');
    if (countText) {
      if (state.all.length === 0) {
        countText.textContent = '0 đại lý';
      } else if (hasFilter) {
        countText.textContent = `Tìm thấy ${state.filtered.length} / ${state.all.length} đại lý`;
      } else {
        countText.textContent = `Tổng cộng ${state.all.length} đại lý`;
      }
    }
  }

  function groupBadge(group) {
    if (!group) return '<span class="group-badge group-badge--unknown">—</span>';
    const label = GROUP_LABELS[group] || group;
    const cls   = GROUP_BADGE_CLASS[group] || 'group-badge--unknown';
    return `<span class="group-badge ${cls}">${escHtml(label)}</span>`;
  }

  /* ── Render table & mobile view ─────────────────────────── */
  function renderTable() {
    const tbody = $('dlr-tbody');
    const start = (state.page - 1) * PAGE_SIZE;
    const slice = state.filtered.slice(start, start + PAGE_SIZE);

    if (!slice.length) {
      const hasFilter = Boolean(
        state.searchQuery ||
        state.regionFilter ||
        state.groupFilter ||
        state.ownerFilter ||
        state.statusFilter
      );

      tbody.innerHTML = `
        <tr>
          <td colspan="8">
            <div class="dlr-empty">
              <svg width="48" height="48" viewBox="0 0 24 24" stroke-width="1.2" fill="none" stroke="currentColor">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
              <p>${
                state.all.length === 0
                  ? 'Chưa có đại lý nào trong hệ thống.'
                  : hasFilter
                    ? 'Không tìm thấy đại lý phù hợp với bộ lọc hiện tại.'
                    : 'Không có dữ liệu.'
              }</p>
              ${
                hasFilter
                  ? `<button type="button" class="btn btn--secondary" id="btn-empty-reset" style="margin-top:12px">Xoá bộ lọc</button>`
                  : ''
              }
            </div>
          </td>
        </tr>`;

      $('btn-empty-reset')?.addEventListener('click', () => {
        $('btn-reset-filters')?.click();
      });
      return;
    }

    tbody.innerHTML = slice.map((dlr) => {
      const isActive = Boolean(dlr.active);

      const isLocked = Boolean(dlr.locked);
      const statusBadge = isLocked
        ? `<span class="status-badge status-badge--locked" title="${escHtml(dlr.lockReason)}">Bị khoá giao dịch</span><span class="dlr-lock-reason">${escHtml(dlr.lockReason)}</span>`
        : isActive
        ? `<span class="status-badge status-badge--active"><span class="status-badge__dot"></span>Đang giao dịch</span>`
        : `<span class="status-badge status-badge--inactive"><span class="status-badge__dot"></span>Ngừng giao dịch</span>`;

      const actions = `<div class="dlr-actions">
            <button class="dlr-action-btn" data-action="orders" data-id="${dlr.id}" title="Đơn đang dở" aria-label="Đơn đang dở của ${escHtml(dlr.name)}">
              <svg viewBox="0 0 24 24"><path d="M9 5H5v16h14V5h-4M9 3h6v4H9zM8 12h8M8 16h5"/></svg>
            </button>
            <button class="dlr-action-btn" data-action="delivery-points" data-id="${dlr.id}" title="Điểm giao hàng" aria-label="Điểm giao hàng của ${escHtml(dlr.name)}">
              <svg viewBox="0 0 24 24"><path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/></svg>
            </button>
            ${CAN_MANAGE ? `
            <button class="dlr-action-btn" data-action="edit" data-id="${dlr.id}" title="Sửa thông tin">
              <svg viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            </button>
            ${isActive
              ? `<button class="dlr-action-btn dlr-action-btn--warn" data-action="deactivate" data-id="${dlr.id}" title="Ngừng giao dịch">
                  <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
                </button>`
              : `<button class="dlr-action-btn dlr-action-btn--success" data-action="activate" data-id="${dlr.id}" title="Khôi phục giao dịch">
                  <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
                </button>`}
            <button class="dlr-action-btn ${isLocked ? 'dlr-action-btn--success' : 'dlr-action-btn--lock'}" data-action="${isLocked ? 'unlock' : 'lock'}" data-id="${dlr.id}" title="${isLocked ? 'Mở khoá giao dịch' : 'Khoá giao dịch'}" aria-label="${isLocked ? 'Mở khoá' : 'Khoá'} giao dịch ${escHtml(dlr.name)}">
              <svg viewBox="0 0 24 24"><rect x="3" y="11" width="18" height="10" rx="2"/><path d="${isLocked ? 'M7 11V7a5 5 0 0 1 9-3' : 'M7 11V7a5 5 0 0 1 10 0v4'}"/></svg>
            </button>` : ''}
           </div>`;

      const phoneLinkHtml = dlr.phone
        ? `<a href="tel:${escHtml(dlr.phone.replace(/\s+/g, ''))}" class="dlr-phone-link" title="Gọi điện cho đại lý">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
            </svg>
            <span>${escHtml(dlr.phone)}</span>
           </a>`
        : `<span class="dlr-phone-muted">Chưa có SĐT</span>`;

      const addressHtml = dlr.address
        ? `<div class="dlr-address" title="${escHtml(dlr.address)}">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
              <circle cx="12" cy="10" r="3"/>
            </svg>
            <span>${escHtml(dlr.address)}</span>
           </div>`
        : '';

      const regionText = escHtml(dlr.territoryName || dlr.region || '—');
      const ownerText = escHtml(dlr.assignedUserFullName || dlr.owner || dlr.assignedUsername || '—');

      return `
        <tr>
          <td>
            <div class="dlr-mobile-card-header">
              <span class="dlr-code">${escHtml(dlr.code)}</span>
              <span class="dlr-mobile-status">${statusBadge}</span>
            </div>
          </td>
          <td>
            <div class="dlr-mobile-name-area">
              <div class="dlr-name" title="${escHtml(dlr.name)}">${escHtml(dlr.name)}</div>
              ${phoneLinkHtml}
              ${addressHtml}
            </div>
          </td>
          <td>
            <span class="dlr-desktop-only">${escHtml(dlr.taxCode || '—')}</span>
          </td>
          <td>
            <span class="dlr-mobile-meta-item">
              <span class="dlr-mobile-meta-label">Nhóm:</span>
              ${groupBadge(dlr.customerGroup)}
            </span>
          </td>
          <td>
            <span class="dlr-mobile-meta-item">
              <span class="dlr-mobile-meta-label">Khu vực:</span>
              <span class="dlr-region-text">${regionText}</span>
            </span>
          </td>
          <td>
            <span class="dlr-mobile-meta-item">
              <span class="dlr-mobile-meta-label">Phụ trách:</span>
              <span class="dlr-owner-text">${ownerText}</span>
            </span>
          </td>
          <td>
            <span class="dlr-desktop-only">${statusBadge}</span>
          </td>
          <td>
            ${actions ? `<div class="dlr-mobile-actions-area">${actions}</div>` : ''}
          </td>
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
    tbody.querySelectorAll('[data-action="lock"]').forEach((btn) => {
      btn.addEventListener('click', () => openLockModal(Number(btn.dataset.id)));
    });
    tbody.querySelectorAll('[data-action="unlock"]').forEach((btn) => {
      btn.addEventListener('click', () => openStatusModal(Number(btn.dataset.id), 'UNLOCK'));
    });
    tbody.querySelectorAll('[data-action="orders"]').forEach((btn) => {
      btn.addEventListener('click', () => openInProgressOrders(Number(btn.dataset.id)));
    });
    tbody.querySelectorAll('[data-action="delivery-points"]').forEach((btn) => {
      btn.addEventListener('click', () => openDeliveryPointsModal(Number(btn.dataset.id)));
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
      populateFilterDropdowns();
      populateModalDropdowns();
      applyFilter();
    } catch (e) {
      $('dlr-tbody').innerHTML = `
        <tr><td colspan="8"><div class="dlr-empty">
          <p>${escHtml(e.message || 'Lỗi tải dữ liệu')}</p>
        </div></td></tr>`;
      showToast(e.message || 'Không tải được danh sách đại lý', 'error');
    }
  }

  /* ── Dealer modal (Add / Edit) ──────────────────────────── */
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
      $('dlr-region').value    = dlr.territoryId ? String(dlr.territoryId) : '';
      $('dlr-owner').value     = dlr.assignedUserId ? String(dlr.assignedUserId) : '';
      $('dlr-phone').value     = dlr.phone ?? '';
      $('dlr-address').value   = dlr.address ?? '';
      if ($('dlr-notes')) $('dlr-notes').value = dlr.notes ?? '';
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

  $('dealer-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = $('dlr-id').value;

    const payload = {
      code:          $('dlr-code').value.trim().toUpperCase(),
      name:          $('dlr-name').value.trim(),
      taxCode:       $('dlr-tax').value.trim() || null,
      customerGroup: $('dlr-group').value || null,
      territoryId:   $('dlr-region').value ? Number($('dlr-region').value) : null,
      assignedUserId:$('dlr-owner').value ? Number($('dlr-owner').value) : null,
      phone:         $('dlr-phone').value.trim() || null,
      address:       $('dlr-address').value.trim() || null,
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
      const res = await Auth.apiFetch(
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

  /* ── Status modal (ngừng / khôi phục) ──────────────────── */
  function openStatusModal(dlrId, targetStatus) {
    if (!CAN_MANAGE) {
      showToast('Bạn không có quyền thay đổi trạng thái đại lý.', 'error');
      return;
    }

    const dlr = state.all.find((d) => d.id === dlrId);
    if (!dlr) return;

    const isDeactivate = targetStatus === 'INACTIVE';
    const isUnlock = targetStatus === 'UNLOCK';
    $('dlr-status-modal-title').textContent = isUnlock ? 'Mở khoá giao dịch' : isDeactivate ? 'Ngừng giao dịch' : 'Khôi phục giao dịch';
    $('dlr-status-modal-sub').textContent   = dlr.name;
    $('dlr-status-modal-body').textContent  = isUnlock
      ? `Mở khoá giao dịch với đại lý «${dlr.name}»? Trạng thái ngừng giao dịch, nếu có, vẫn được giữ.`
      : isDeactivate
      ? `Đại lý «${dlr.name}» sẽ bị ngừng giao dịch. Các hoá đơn hiện có sẽ không bị ảnh hưởng. Bạn có thể khôi phục bất kỳ lúc nào.`
      : `Khôi phục giao dịch với đại lý «${dlr.name}». Đại lý sẽ trở lại trạng thái đang giao dịch.`;

    $('dlr-status-id').value = dlrId;
    $('dlr-status-target').value = targetStatus;
    $('dlr-status-form-message').hidden = true;

    const confirmBtn = $('btn-confirm-dlr-status');
    confirmBtn.textContent = isUnlock ? 'Mở khoá' : isDeactivate ? 'Ngừng giao dịch' : 'Khôi phục';

    $('dlr-status-modal').hidden = false;
  }

  function closeStatusModal() {
    $('dlr-status-modal').hidden = true;
  }

  $('btn-confirm-dlr-status')?.addEventListener('click', async () => {
    const dlrId        = Number($('dlr-status-id').value);
    const targetStatus = $('dlr-status-target').value;

    const btn = $('btn-confirm-dlr-status');
    btn.disabled    = true;
    btn.textContent = 'Đang xử lý…';

    try {
      if (targetStatus === 'UNLOCK') {
        await DealerTransactions.unlock(dlrId);
      } else {
        const res = await Auth.apiFetch(`/dealers/${dlrId}/status?active=${targetStatus === 'ACTIVE'}`, { method: 'PATCH' });
        const result = await res.json().catch(() => ({}));
        if (!res.ok || !result.success) throw new Error(result.message || 'Không thay đổi được trạng thái');
      }

      closeStatusModal();
      showToast(
        targetStatus === 'UNLOCK' ? 'Đã mở khoá giao dịch.' : targetStatus === 'INACTIVE' ? 'Đã ngừng giao dịch đại lý.' : 'Đã khôi phục giao dịch.',
        'success',
      );
      await loadDealers();
    } catch (err) {
      showFormError('dlr-status-form-message', err.message || 'Lỗi không xác định');
    } finally {
      btn.disabled    = false;
      btn.textContent = targetStatus === 'UNLOCK' ? 'Mở khoá' : targetStatus === 'INACTIVE' ? 'Ngừng giao dịch' : 'Khôi phục';
    }
  });

  /* S3-07: giữ trạng thái ở server; chỉ cập nhật bảng sau khi API thành công. */
  let transactionPreviousFocus;
  function openTransactionModal(id) {
    transactionPreviousFocus = document.activeElement;
    $(id).hidden = false;
    document.querySelector('.app-shell').inert = true;
    $(id).querySelector('button, textarea')?.focus();
  }
  function closeTransactionModal(id) {
    $(id).hidden = true;
    document.querySelector('.app-shell').inert = false;
    transactionPreviousFocus?.focus();
  }
  function openLockModal(id) {
    if (!CAN_MANAGE) return;
    const dealer = state.all.find(row => row.id === id);
    if (!dealer) return;
    $('lock-dlr-id').value = id;
    $('lock-modal-sub').textContent = dealer.name;
    $('lock-reason').value = '';
    $('lock-form-msg').hidden = true;
    openTransactionModal('lock-modal');
    $('lock-reason').focus();
  }
  $('lock-form').addEventListener('submit', async event => {
    event.preventDefault();
    if (!CAN_MANAGE) return;
    const button = $('btn-confirm-lock');
    button.disabled = true;
    $('lock-form-msg').hidden = true;
    try {
      const dealer = await DealerTransactions.lock(Number($('lock-dlr-id').value), $('lock-reason').value);
      closeTransactionModal('lock-modal');
      showToast(Number(dealer.inProgressOrderCount) > 0
        ? `Đã khoá giao dịch. Có ${dealer.inProgressOrderCount} đơn đang dở cần theo dõi.` : 'Đã khoá giao dịch.', 'success');
      await loadDealers();
    } catch (error) {
      showFormError('lock-form-msg', error.message);
    } finally { button.disabled = false; }
  });

  let orderDealerId = null;
  async function openInProgressOrders(id) {
    orderDealerId = id;
    const dealer = state.all.find(row => row.id === id);
    $('dealer-orders-title').textContent = `Đơn đang dở · ${dealer?.name || ''}`;
    if ($('dealer-orders-modal').hidden) openTransactionModal('dealer-orders-modal');
    $('dealer-orders-message').hidden = true;
    $('dealer-orders-warning').hidden = !dealer?.locked;
    $('dealer-orders-warning').textContent = dealer?.locked
      ? `Đại lý đang bị khoá: ${dealer.lockReason || ''}. Đơn đã tạo vẫn được xử lý tiếp, cần theo dõi công nợ.` : '';
    $('dealer-orders-body').innerHTML = '<tr><td colspan="4">Đang tải đơn hàng…</td></tr>';
    try {
      const orders = await DealerTransactions.getInProgressOrders(id);
      if (orderDealerId !== id || $('dealer-orders-modal').hidden) return;
      const warning = orders.find(order => order.dealerLocked)?.dealerLockWarning;
      if (warning) {
        $('dealer-orders-warning').hidden = false;
        $('dealer-orders-warning').textContent = warning;
      }
      const canApprove = Auth.hasAnyRole('ROLE_ADMIN', 'ROLE_SALES_MANAGER');
      $('dealer-orders-body').innerHTML = orders.length ? orders.map(order => `<tr>
        <td>${escHtml(order.orderCode)}</td><td>${escHtml(order.status === 'PENDING_APPROVAL' ? 'Chờ duyệt' : order.status === 'RECORDED' ? 'Đã ghi sổ' : order.status)}</td>
        <td>${escHtml(Number(order.totalAmount || 0).toLocaleString('vi-VN'))} ₫</td>
        <td>${canApprove && order.status === 'PENDING_APPROVAL' ? `<button class="btn btn--secondary" data-order-action="approve" data-order-id="${order.id}">Duyệt</button> <button class="btn btn--secondary" data-order-action="reject" data-order-id="${order.id}">Từ chối</button>` : '—'}</td>
        </tr>`).join('') : '<tr><td colspan="4">Không có đơn đang dở.</td></tr>';
    } catch (error) {
      if (orderDealerId !== id) return;
      $('dealer-orders-body').innerHTML = '<tr><td colspan="4">Không tải được đơn hàng.</td></tr>';
      showFormError('dealer-orders-message', error.message);
    }
  }
  $('dealer-orders-body').addEventListener('click', async event => {
    const button = event.target.closest('[data-order-action]');
    if (!button || !Auth.hasAnyRole('ROLE_ADMIN', 'ROLE_SALES_MANAGER')) return;
    const dealerId = orderDealerId;
    button.disabled = true;
    try {
      await DealerTransactions.processOrder(Number(button.dataset.orderId), button.dataset.orderAction);
      showToast(button.dataset.orderAction === 'approve' ? 'Đã duyệt đơn hàng.' : 'Đã từ chối đơn hàng.');
      await loadDealers();
      if (orderDealerId === dealerId) await openInProgressOrders(dealerId);
    } catch (error) { showFormError('dealer-orders-message', error.message); }
    finally { button.disabled = false; }
  });
  document.querySelectorAll('[data-close-transaction]').forEach(button => button.addEventListener('click', () => {
    if (button.dataset.closeTransaction === 'dealer-orders-modal') orderDealerId = null;
    if (button.dataset.closeTransaction === 'delivery-points-modal') state.deliveryDealerId = null;
    closeTransactionModal(button.dataset.closeTransaction);
  }));
  ['lock-modal', 'dealer-orders-modal', 'delivery-points-modal'].forEach(id => $(id).addEventListener('click', event => {
    if (event.target !== $(id)) return;
    if (id === 'dealer-orders-modal') orderDealerId = null;
    if (id === 'delivery-points-modal') state.deliveryDealerId = null;
    closeTransactionModal(id);
  }));
  document.addEventListener('keydown', event => {
    const id = ['lock-modal', 'dealer-orders-modal', 'delivery-points-modal'].find(name => !$(name).hidden);
    if (!id) return;
    if (event.key === 'Escape') {
      if (id === 'dealer-orders-modal') orderDealerId = null;
      if (id === 'delivery-points-modal') state.deliveryDealerId = null;
      closeTransactionModal(id);
    }
    if (event.key === 'Tab') {
      const controls = [...$(id).querySelectorAll('button:not(:disabled), textarea, input:not([type="hidden"]), select')]
        .filter(control => !control.closest('[hidden]'));
      const first = controls[0], last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    }
  });

  /* ── S3-04: Delivery points ─────────────────────────────── */
  function responseData(payload) {
    return payload && Object.prototype.hasOwnProperty.call(payload, 'data') ? payload.data : payload;
  }

  function responseMessage(payload, fallback) {
    return payload?.message || payload?.error || fallback;
  }

  async function readJsonResponse(res) {
    return res.json().catch(() => null);
  }

  async function loadDeliveryPointsForDealer(dealerId) {
    const res = await Auth.apiFetch(`/dealers/${dealerId}/delivery-points`);
    const payload = await readJsonResponse(res);
    if (!res.ok) throw new Error(responseMessage(payload, 'Không tải được điểm giao hàng.'));

    const data = responseData(payload);
    state.deliveryPoints = Array.isArray(data) ? data : [];
    renderDeliveryPoints();
  }

  function renderDeliveryPoints() {
    const list = $('delivery-points-list');
    if (!list) return;

    if (!state.deliveryPoints.length) {
      list.innerHTML = '<div class="delivery-points-empty">Đại lý này chưa có điểm giao hàng nào.</div>';
      return;
    }

    list.innerHTML = state.deliveryPoints.map((point) => `
      <article class="delivery-point-card">
        <div class="delivery-point-card__main">
          <h3 class="delivery-point-card__title">
            ${escHtml(point.receiverName || 'Chưa có người nhận')}
            ${point.isDefault ? '<span class="delivery-point-badge">Mặc định</span>' : ''}
          </h3>
          <div class="delivery-point-card__meta">
            <div><strong>Số điện thoại:</strong> ${escHtml(point.phone || '—')}</div>
            <div><strong>Địa chỉ:</strong> ${escHtml(point.address || '—')}</div>
            ${point.directionNote ? `<div class="delivery-point-card__note"><strong>Ghi chú:</strong> ${escHtml(point.directionNote)}</div>` : ''}
          </div>
        </div>
        ${CAN_MANAGE_DELIVERY_POINTS ? `<div class="delivery-point-card__actions">
          <button type="button" class="btn btn--secondary" data-delivery-action="edit" data-id="${point.id}">Sửa</button>
          ${point.isDefault ? '' : `<button type="button" class="btn btn--secondary" data-delivery-action="default" data-id="${point.id}">Đặt mặc định</button>`}
          <button type="button" class="btn btn--secondary" data-delivery-action="delete" data-id="${point.id}">Xoá</button>
        </div>` : ''}
      </article>
    `).join('');
  }

  function closeDeliveryPointForm() {
    $('delivery-point-form').hidden = true;
    $('delivery-point-form-message').hidden = true;
    state.editingDeliveryPointId = null;
  }

  function openDeliveryPointForm(point = null) {
    if (!CAN_MANAGE_DELIVERY_POINTS) {
      showToast('Bạn không có quyền thay đổi điểm giao hàng.', 'error');
      return;
    }

    state.editingDeliveryPointId = point?.id ?? null;
    $('delivery-point-form').reset();
    $('delivery-point-form-message').hidden = true;
    $('delivery-point-id').value = point?.id ?? '';
    $('delivery-point-receiver').value = point?.receiverName ?? '';
    $('delivery-point-phone').value = point?.phone ?? '';
    $('delivery-point-address').value = point?.address ?? '';
    $('delivery-point-note').value = point?.directionNote ?? '';
    $('delivery-point-default').checked = Boolean(point?.isDefault);
    $('delivery-point-default').disabled = Boolean(point?.isDefault);
    $('delivery-point-form-title').textContent = point ? 'Sửa điểm giao hàng' : 'Thêm điểm giao hàng';
    $('btn-save-delivery-point').textContent = point ? 'Lưu thay đổi' : 'Lưu điểm giao';
    $('delivery-point-form').hidden = false;
    $('delivery-point-receiver').focus();
  }

  async function openDeliveryPointsModal(dealerId) {
    const dealer = state.all.find((row) => row.id === dealerId);
    if (!dealer) return;

    state.deliveryDealerId = dealerId;
    state.deliveryPoints = [];
    closeDeliveryPointForm();
    $('delivery-points-title').textContent = 'Điểm giao hàng';
    $('delivery-points-subtitle').textContent = `${dealer.code || ''} — ${dealer.name || ''}`;
    $('delivery-points-message').hidden = true;
    $('btn-add-delivery-point').hidden = !CAN_MANAGE_DELIVERY_POINTS;
    $('delivery-points-list').innerHTML = '<div class="delivery-points-loading">Đang tải điểm giao hàng…</div>';
    openTransactionModal('delivery-points-modal');

    try {
      await loadDeliveryPointsForDealer(dealerId);
    } catch (error) {
      if (state.deliveryDealerId !== dealerId) return;
      $('delivery-points-list').innerHTML = '<div class="delivery-points-empty">Không tải được danh sách điểm giao hàng.</div>';
      showFormError('delivery-points-message', error.message || 'Lỗi không xác định');
    }
  }

  $('btn-add-delivery-point')?.addEventListener('click', () => openDeliveryPointForm());
  $('btn-cancel-delivery-point')?.addEventListener('click', closeDeliveryPointForm);

  $('delivery-points-list')?.addEventListener('click', async (event) => {
    const button = event.target.closest('[data-delivery-action]');
    if (!button || !CAN_MANAGE_DELIVERY_POINTS) return;

    const pointId = Number(button.dataset.id);
    const point = state.deliveryPoints.find((item) => item.id === pointId);
    if (!point) return;

    if (button.dataset.deliveryAction === 'edit') {
      openDeliveryPointForm(point);
      return;
    }

    if (button.dataset.deliveryAction === 'delete') {
      if (point.isDefault) {
        showToast('Hãy đặt điểm khác làm mặc định trước khi xoá điểm này.', 'warning');
        return;
      }
      if (!window.confirm(`Xoá điểm giao hàng của ${point.receiverName || 'người nhận này'}?`)) return;
      button.disabled = true;
      try {
        const res = await Auth.apiFetch(`/dealers/delivery-points/${pointId}`, { method: 'DELETE' });
        const payload = await readJsonResponse(res);
        if (!res.ok) throw new Error(responseMessage(payload, 'Không xoá được điểm giao hàng.'));
        showToast('Đã xoá điểm giao hàng.', 'success');
        await loadDeliveryPointsForDealer(state.deliveryDealerId);
      } catch (error) {
        showFormError('delivery-points-message', error.message || 'Lỗi không xác định');
      } finally {
        button.disabled = false;
      }
      return;
    }

    button.disabled = true;
    try {
      const res = await Auth.apiFetch(`/dealers/${state.deliveryDealerId}/delivery-points/${pointId}/default`, { method: 'PATCH' });
      const payload = await readJsonResponse(res);
      if (!res.ok) throw new Error(responseMessage(payload, 'Không đặt được điểm mặc định.'));
      showToast('Đã đặt điểm giao hàng mặc định.', 'success');
      await loadDeliveryPointsForDealer(state.deliveryDealerId);
    } catch (error) {
      showFormError('delivery-points-message', error.message || 'Lỗi không xác định');
    } finally {
      button.disabled = false;
    }
  });

  $('delivery-point-form')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!CAN_MANAGE_DELIVERY_POINTS || !state.deliveryDealerId) return;

    const receiverName = $('delivery-point-receiver').value.trim();
    const phone = $('delivery-point-phone').value.trim();
    const address = $('delivery-point-address').value.trim();
    if (!receiverName || !phone || !address) {
      showFormError('delivery-point-form-message', 'Vui lòng nhập người nhận, số điện thoại và địa chỉ.');
      return;
    }

    const button = $('btn-save-delivery-point');
    button.disabled = true;
    $('delivery-point-form-message').hidden = true;
    const pointId = state.editingDeliveryPointId;
    const payload = {
      receiverName,
      phone,
      address,
      directionNote: $('delivery-point-note').value.trim() || null,
      isDefault: $('delivery-point-default').checked,
    };

    try {
      const res = await Auth.apiFetch(
        pointId ? `/dealers/delivery-points/${pointId}` : `/dealers/${state.deliveryDealerId}/delivery-points`,
        { method: pointId ? 'PUT' : 'POST', body: JSON.stringify(payload) },
      );
      const response = await readJsonResponse(res);
      if (!res.ok) throw new Error(responseMessage(response, 'Không lưu được điểm giao hàng.'));
      showToast(pointId ? 'Đã cập nhật điểm giao hàng.' : 'Đã thêm điểm giao hàng.', 'success');
      closeDeliveryPointForm();
      await loadDeliveryPointsForDealer(state.deliveryDealerId);
    } catch (error) {
      showFormError('delivery-point-form-message', error.message || 'Lỗi không xác định');
    } finally {
      button.disabled = false;
      button.textContent = pointId ? 'Lưu thay đổi' : 'Lưu điểm giao';
    }
  });

  /* ── Form error helper ──────────────────────────────────── */
  function showFormError(elId, text) {
    const el = $(elId);
    if (!el) return;
    el.hidden      = false;
    el.textContent = text;
  }

  /* ── Manage privileges UI ────────────────────────────────── */
  if (CAN_MANAGE) {
    const addBtn = $('btn-add-dealer');
    if (addBtn) addBtn.hidden = false;
  }

  /* ── Event bindings ─────────────────────────────────────── */

  $('btn-add-dealer')?.addEventListener('click', () => openDealerModal(null));

  // Quick search
  let searchTimer;
  $('dlr-search')?.addEventListener('input', () => {
    clearTimeout(searchTimer);
    const val = $('dlr-search').value;
    const clearBtn = $('dlr-search-clear');
    if (clearBtn) clearBtn.hidden = !val;

    searchTimer = setTimeout(() => {
      state.searchQuery = val;
      applyFilter();
    }, 150);
  });

  $('dlr-search-clear')?.addEventListener('click', () => {
    const searchInput = $('dlr-search');
    if (searchInput) {
      searchInput.value = '';
      searchInput.focus();
    }
    $('dlr-search-clear').hidden = true;
    state.searchQuery = '';
    applyFilter();
  });

  // Khu vực filter
  $('dlr-filter-region')?.addEventListener('change', () => {
    state.regionFilter = $('dlr-filter-region').value;
    applyFilter();
  });

  // Nhóm KH filter
  $('dlr-filter-group')?.addEventListener('change', () => {
    state.groupFilter = $('dlr-filter-group').value;
    applyFilter();
  });

  // Người phụ trách filter
  $('dlr-filter-owner')?.addEventListener('change', () => {
    state.ownerFilter = $('dlr-filter-owner').value;
    if (state.activeChip === 'my-dealers' && state.ownerFilter !== '__ME__') {
      setActiveChip(null);
    }
    applyFilter();
  });

  // Trạng thái filter
  $('dlr-filter-status')?.addEventListener('change', () => {
    state.statusFilter = $('dlr-filter-status').value;
    if (state.activeChip === 'active-only' && state.statusFilter !== 'ACTIVE') {
      setActiveChip(null);
    }
    applyFilter();
  });

  // Đặt lại bộ lọc (Reset)
  $('btn-reset-filters')?.addEventListener('click', () => {
    state.searchQuery = '';
    state.regionFilter = '';
    state.groupFilter = '';
    state.ownerFilter = '';
    state.statusFilter = '';
    setActiveChip('all');

    if ($('dlr-search')) $('dlr-search').value = '';
    if ($('dlr-search-clear')) $('dlr-search-clear').hidden = true;
    if ($('dlr-filter-region')) $('dlr-filter-region').value = '';
    if ($('dlr-filter-group')) $('dlr-filter-group').value = '';
    if ($('dlr-filter-owner')) $('dlr-filter-owner').value = '';
    if ($('dlr-filter-status')) $('dlr-filter-status').value = '';

    applyFilter();
  });

  // Quick Chips
  function setActiveChip(chipKey) {
    state.activeChip = chipKey;
    document.querySelectorAll('.dlr-chip').forEach((btn) => {
      btn.classList.toggle('dlr-chip--active', btn.dataset.chip === chipKey);
    });
  }

  document.querySelectorAll('.dlr-chip').forEach((btn) => {
    btn.addEventListener('click', () => {
      const chip = btn.dataset.chip;
      setActiveChip(chip);

      if (chip === 'all') {
        state.regionFilter = '';
        state.groupFilter = '';
        state.ownerFilter = '';
        state.statusFilter = '';
        if ($('dlr-filter-region')) $('dlr-filter-region').value = '';
        if ($('dlr-filter-group')) $('dlr-filter-group').value = '';
        if ($('dlr-filter-owner')) $('dlr-filter-owner').value = '';
        if ($('dlr-filter-status')) $('dlr-filter-status').value = '';
      } else if (chip === 'my-dealers') {
        state.ownerFilter = '__ME__';
        // Select in dropdown if user match exists
        const curUser = Auth.getUser?.();
        if (curUser && $('dlr-filter-owner')) {
          const ownerSelect = $('dlr-filter-owner');
          for (const opt of ownerSelect.options) {
            if (opt.value && (opt.value === String(curUser.id) || opt.text.includes(curUser.fullName || curUser.username))) {
              ownerSelect.value = opt.value;
              break;
            }
          }
        }
      } else if (chip === 'active-only') {
        state.statusFilter = 'ACTIVE';
        if ($('dlr-filter-status')) $('dlr-filter-status').value = 'ACTIVE';
      }
      applyFilter();
    });
  });

  // Modal close — dealer form
  document.querySelectorAll('[data-close-dealer]').forEach((btn) =>
    btn.addEventListener('click', closeDealerModal),
  );
  $('dealer-modal')?.addEventListener('click', (e) => {
    if (e.target === $('dealer-modal')) closeDealerModal();
  });

  // Modal close — status
  document.querySelectorAll('[data-close-dlr-status]').forEach((btn) =>
    btn.addEventListener('click', closeStatusModal),
  );
  $('dlr-status-modal')?.addEventListener('click', (e) => {
    if (e.target === $('dlr-status-modal')) closeStatusModal();
  });

  // Keyboard shortcut (Escape)
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if ($('dealer-modal') && !$('dealer-modal').hidden) closeDealerModal();
      if ($('dlr-status-modal') && !$('dlr-status-modal').hidden) closeStatusModal();
    }
  });

  /* ── Init ───────────────────────────────────────────────── */
  loadOptions();
  loadDealers();
})();
