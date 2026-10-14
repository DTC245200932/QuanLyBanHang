/**
 * orders.js — S4-07: Quản lý và tra cứu danh sách đơn hàng kèm bộ lọc.
 *
 * Tính năng:
 * - Dành cho Quản lý kinh doanh (ROLE_SALES_MANAGER) nắm tình hình bán hàng toàn công ty trong một màn hình.
 * - Nhân viên kinh doanh (ROLE_SALES) chỉ thấy các đơn hàng thuộc đại lý mình phụ trách.
 * - Lọc đa chiều: Trạng thái, Đại lý, Nhân viên, Khu vực, Khoảng thời gian (từ ngày - đến ngày + preset).
 * - Hiển thị tổng tiền và các chỉ số tập kết quả đang lọc (realtime reactive KPI).
 * - Xem chi tiết đơn hàng (dòng sản phẩm, giá, chiết khấu, hạn mức công nợ).
 * - Phê duyệt / Từ chối đơn hàng bán dưới giá sàn hoặc vượt hạn mức (Quản lý kinh doanh).
 * - Huỷ đơn hàng giải phóng tồn kho (có cảnh báo).
 */
(() => {
  /* ── 1. Auth Guard ─────────────────────────────────────────── */
  if (!Auth.requireLogin()) return;

  const user = Auth.getUser();
  const CAN_ACCESS = Auth.hasAnyRole(
    'ROLE_ADMIN',
    'ROLE_SALES_MANAGER',
    'ROLE_SALES',
    'ROLE_ACCOUNTANT',
    'ROLE_VIEWER',
    'ROLE_WAREHOUSE_MANAGER',
  );
  if (!CAN_ACCESS) {
    location.href = '/error?status=403';
    return;
  }

  const CAN_APPROVE = Auth.hasAnyRole('ROLE_ADMIN', 'ROLE_SALES_MANAGER');
  const CAN_CANCEL = Auth.hasAnyRole('ROLE_ADMIN', 'ROLE_SALES_MANAGER', 'ROLE_SALES');
  const IS_SALES_ONLY = Auth.hasRole('ROLE_SALES') && !Auth.hasAnyRole('ROLE_ADMIN', 'ROLE_SALES_MANAGER');

  /* ── 2. DOM Helpers ────────────────────────────────────────── */
  const $ = (id) => document.getElementById(id);
  const escHtml = (v) =>
    String(v ?? '').replace(/[&<>'"]/g, (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c],
    );

  const formatVnd = (amount) => {
    const num = Number(amount ?? 0);
    return new Intl.NumberFormat('vi-VN').format(isNaN(num) ? 0 : num) + ' ₫';
  };

  const formatDate = (val) => {
    if (!val) return '—';
    const d = new Date(val);
    if (isNaN(d.getTime())) return String(val);
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(d);
  };

  const formatDateShort = (val) => {
    if (!val) return '—';
    const d = new Date(val);
    if (isNaN(d.getTime())) return String(val);
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(d);
  };

  /* ── 3. Group Labels & Badges ────────────────────────────── */
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

  const STATUS_CONFIG = {
    PENDING_APPROVAL: { label: 'Chờ duyệt', cls: 'order-status-badge--pending' },
    RECORDED:         { label: 'Đã ghi nhận', cls: 'order-status-badge--approved' },
    APPROVED:         { label: 'Đã duyệt', cls: 'order-status-badge--approved' },
    PICKING:          { label: 'Đang lấy hàng', cls: 'order-status-badge--picking' },
    EXPORTED:         { label: 'Đã xuất kho', cls: 'order-status-badge--exported' },
    DELIVERED:        { label: 'Đã giao hàng', cls: 'order-status-badge--delivered' },
    COMPLETED:        { label: 'Hoàn thành', cls: 'order-status-badge--delivered' },
    CANCELLED:        { label: 'Đã huỷ', cls: 'order-status-badge--cancelled' },
    REJECTED:         { label: 'Bị từ chối', cls: 'order-status-badge--rejected' },
  };

  function renderStatusBadge(status) {
    const cfg = STATUS_CONFIG[status] || { label: status || '—', cls: 'order-status-badge--draft' };
    return `<span class="order-status-badge ${cfg.cls}"><span class="order-status-badge__dot"></span>${escHtml(cfg.label)}</span>`;
  }

  function groupBadge(group) {
    if (!group) return '—';
    const label = GROUP_LABELS[group] || group;
    const cls = GROUP_BADGE_CLASS[group] || 'group-badge--unknown';
    return `<span class="group-badge ${cls}">${escHtml(label)}</span>`;
  }

  /* ── 4. State ──────────────────────────────────────────────── */
  const PAGE_SIZE = 15;

  const state = {
    /** All orders loaded from API */
    allOrders: [],
    /** Accessible orders based on user scope */
    scopedOrders: [],
    /** Filtered subset */
    filteredOrders: [],
    /** Map of dealerId -> dealer object */
    dealersMap: new Map(),
    /** List of all dealers */
    dealersList: [],
    /** Options (territories, assignedUsers) */
    options: {
      territories: [],
      assignedUsers: [],
    },
    /** Pagination */
    page: 1,
    /** Filters */
    filters: {
      search: '',
      status: '',
      dealerId: '',
      owner: '',
      region: '',
      dateFrom: '',
      dateTo: '',
      datePreset: 'all',
      quickChip: 'all',
    },
    /** Currently selected order for modal view */
    activeOrder: null,
  };

  /* ── 5. Layout & Topbar Init ───────────────────────────────── */
  (function initLayout() {
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

    if (IS_SALES_ONLY) {
      $('ord-sales-scope-banner').hidden = false;
      $('ord-heading-desc').textContent =
        'Danh sách đơn hàng của các đại lý bạn được phân công phụ trách.';
    }
  })();

  /* ── 6. Toast Helper ───────────────────────────────────────── */
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

  /* ── 7. Data Loading ───────────────────────────────────────── */
  async function loadInitialData() {
    try {
      // 1. Fetch dealers options & full dealers list in parallel
      const [optRes, dlrRes, ordRes] = await Promise.all([
        Auth.apiFetch('/dealers/options').catch(() => null),
        Auth.apiFetch('/dealers?page=0&size=500').catch(() => null),
        Auth.apiFetch('/orders').catch(() => null),
      ]);

      if (optRes && optRes.ok) {
        const optJson = await optRes.json();
        if (optJson.success && optJson.data) {
          state.options = optJson.data;
        }
      }

      if (dlrRes && dlrRes.ok) {
        const dlrJson = await dlrRes.json();
        const dealers = Array.isArray(dlrJson.data)
          ? dlrJson.data
          : dlrJson.data?.content || [];
        state.dealersList = dealers;
        dealers.forEach((d) => {
          if (d.id != null) state.dealersMap.set(d.id, d);
        });
      }

      if (ordRes && ordRes.ok) {
        const ordJson = await ordRes.json();
        state.allOrders = Array.isArray(ordJson.data) ? ordJson.data : [];
      } else {
        throw new Error('Không thể tải danh sách đơn hàng');
      }

      // 2. Scope orders for ROLE_SALES
      applyUserScoping();

      // 3. Populate filter dropdowns
      populateFilterDropdowns();

      // 4. Apply initial filters & render
      applyFilters();
    } catch (err) {
      console.error(err);
      const tbody = $('ord-tbody');
      tbody.innerHTML = `
        <tr>
          <td colspan="8">
            <div class="dlr-empty">
              <p>Lỗi khi tải dữ liệu: ${escHtml(err.message || 'Mất kết nối máy chủ')}</p>
              <button type="button" class="btn btn--secondary" onclick="location.reload()" style="margin-top:12px">Thử lại</button>
            </div>
          </td>
        </tr>`;
      showToast(err.message || 'Không thể tải danh sách đơn hàng', 'error');
    }
  }

  /**
   * Nhân viên kinh doanh chỉ thấy đơn của đại lý mình phụ trách (S4-07).
   */
  function applyUserScoping() {
    if (!IS_SALES_ONLY) {
      state.scopedOrders = [...state.allOrders];
      return;
    }

    const curUserId = user.id;
    const curUsername = (user.username || '').toLowerCase();
    const curFullName = (user.fullName || '').toLowerCase();

    // Identify dealer IDs assigned to current sales rep
    const myDealerIds = new Set();
    state.dealersList.forEach((d) => {
      const matchId = d.assignedUserId && d.assignedUserId === curUserId;
      const matchUsername = d.assignedUsername && d.assignedUsername.toLowerCase() === curUsername;
      const matchFullName = d.assignedUserFullName && d.assignedUserFullName.toLowerCase() === curFullName;
      if (matchId || matchUsername || matchFullName) {
        myDealerIds.add(d.id);
      }
    });

    state.scopedOrders = state.allOrders.filter((ord) => {
      // Belongs to an assigned dealer OR created by this sales rep
      if (ord.dealerId != null && myDealerIds.has(ord.dealerId)) return true;
      if (ord.createdBy && ord.createdBy.toLowerCase() === curUsername) return true;
      return false;
    });
  }

  /* ── 8. Populate Filter Dropdowns ──────────────────────────── */
  function populateFilterDropdowns() {
    // 1. Đại lý
    const dealerSelect = $('ord-filter-dealer');
    if (dealerSelect) {
      const relevantDealers = IS_SALES_ONLY
        ? state.dealersList.filter((d) => {
            const matchId = d.assignedUserId && d.assignedUserId === user.id;
            const matchUsername = d.assignedUsername && d.assignedUsername.toLowerCase() === (user.username || '').toLowerCase();
            return matchId || matchUsername;
          })
        : state.dealersList;

      relevantDealers.sort((a, b) => String(a.name || '').localeCompare(String(b.name || ''), 'vi'));

      const opts = ['<option value="">Tất cả đại lý</option>'];
      relevantDealers.forEach((d) => {
        opts.push(`<option value="${escHtml(d.id)}">${escHtml(d.name)} (${escHtml(d.code)})</option>`);
      });
      dealerSelect.innerHTML = opts.join('');
    }

    // 2. Nhân viên phụ trách
    const ownerSelect = $('ord-filter-owner');
    if (ownerSelect) {
      if (IS_SALES_ONLY) {
        // Locked to current sales rep
        ownerSelect.innerHTML = `<option value="${escHtml(user.id)}">${escHtml(user.fullName || user.username)} (Tôi)</option>`;
        ownerSelect.disabled = true;
      } else {
        const userMap = new Map();
        (state.options.assignedUsers || []).forEach((u) => {
          if (u && (u.id || u.username)) {
            userMap.set(String(u.id ?? u.username), u.fullName || u.username);
          }
        });
        state.dealersList.forEach((d) => {
          const key = d.assignedUserId ? String(d.assignedUserId) : (d.assignedUsername || '');
          const name = d.assignedUserFullName || d.assignedUsername || '';
          if (key && name && !userMap.has(key)) userMap.set(key, name);
        });
        const opts = ['<option value="">Tất cả nhân viên</option>'];
        userMap.forEach((name, key) => {
          opts.push(`<option value="${escHtml(key)}">${escHtml(name)}</option>`);
        });
        ownerSelect.innerHTML = opts.join('');
      }
    }

    // 3. Khu vực
    const regionSelect = $('ord-filter-region');
    if (regionSelect) {
      const territoryMap = new Map();
      (state.options.territories || []).forEach((t) => {
        if (t && t.name) territoryMap.set(String(t.id ?? t.name), t.name);
      });
      state.dealersList.forEach((d) => {
        const key = d.territoryId ? String(d.territoryId) : (d.territoryName || '');
        const name = d.territoryName || '';
        if (key && name && !territoryMap.has(key)) territoryMap.set(key, name);
      });
      const opts = ['<option value="">Tất cả khu vực</option>'];
      territoryMap.forEach((name, key) => {
        opts.push(`<option value="${escHtml(key)}">${escHtml(name)}</option>`);
      });
      regionSelect.innerHTML = opts.join('');
    }
  }

  /* ── 9. Filter Application & KPI Recalculation ─────────────── */
  function matchesSearch(order, dealer, query) {
    if (!query) return true;
    const q = query.toLowerCase().trim();
    if (!q) return true;
    if ((order.orderCode || '').toLowerCase().includes(q)) return true;
    if ((order.customerName || '').toLowerCase().includes(q)) return true;
    if (dealer && (dealer.name || '').toLowerCase().includes(q)) return true;
    if (dealer && (dealer.code || '').toLowerCase().includes(q)) return true;
    if ((order.note || '').toLowerCase().includes(q)) return true;
    return false;
  }

  function matchesStatus(order, status) {
    if (!status) return true;
    return (order.status || '').toUpperCase() === status.toUpperCase();
  }

  function matchesDealer(order, dealerId) {
    if (!dealerId) return true;
    return String(order.dealerId) === String(dealerId);
  }

  function matchesOwner(order, dealer, ownerVal) {
    if (!ownerVal) return true;
    if (!dealer) return (order.createdBy || '').toLowerCase() === ownerVal.toLowerCase();
    const target = String(ownerVal).toLowerCase();
    if (dealer.assignedUserId && String(dealer.assignedUserId) === target) return true;
    if (dealer.assignedUsername && dealer.assignedUsername.toLowerCase() === target) return true;
    if (dealer.assignedUserFullName && dealer.assignedUserFullName.toLowerCase() === target) return true;
    if (order.createdBy && order.createdBy.toLowerCase() === target) return true;
    return false;
  }

  function matchesRegion(order, dealer, regionVal) {
    if (!regionVal) return true;
    if (!dealer) return false;
    const target = String(regionVal).toLowerCase();
    if (dealer.territoryId && String(dealer.territoryId) === target) return true;
    if (dealer.territoryName && dealer.territoryName.toLowerCase() === target) return true;
    if (dealer.territoryCode && dealer.territoryCode.toLowerCase() === target) return true;
    return false;
  }

  function matchesDateRange(order, fromStr, toStr) {
    if (!fromStr && !toStr) return true;
    const ordDate = order.createdAt ? new Date(order.createdAt) : null;
    if (!ordDate || isNaN(ordDate.getTime())) return true;

    if (fromStr) {
      const from = new Date(fromStr + 'T00:00:00');
      if (ordDate < from) return false;
    }
    if (toStr) {
      const to = new Date(toStr + 'T23:59:59.999');
      if (ordDate > to) return false;
    }
    return true;
  }

  function matchesQuickChip(order, chip) {
    if (!chip || chip === 'all') return true;
    const st = (order.status || '').toUpperCase();
    if (chip === 'pending') {
      return st === 'PENDING_APPROVAL' || Boolean(order.requiresApproval);
    }
    if (chip === 'in-progress') {
      return ['RECORDED', 'APPROVED', 'PICKING', 'EXPORTED'].includes(st);
    }
    if (chip === 'completed') {
      return ['DELIVERED', 'COMPLETED'].includes(st);
    }
    return true;
  }

  function applyFilters() {
    const { search, status, dealerId, owner, region, dateFrom, dateTo, quickChip } = state.filters;

    state.filteredOrders = state.scopedOrders.filter((order) => {
      const dealer = order.dealerId != null ? state.dealersMap.get(order.dealerId) : null;
      return (
        matchesSearch(order, dealer, search) &&
        matchesStatus(order, status) &&
        matchesDealer(order, dealerId) &&
        matchesOwner(order, dealer, owner) &&
        matchesRegion(order, dealer, region) &&
        matchesDateRange(order, dateFrom, dateTo) &&
        matchesQuickChip(order, quickChip)
      );
    });

    // Reset pagination to first page
    state.page = 1;

    // Recalculate and update summary totals
    updateKpiMetrics();

    // Render table & pagination
    renderTable();
    renderPagination();
    updateToolbarMeta();
  }

  /**
   * S4-07: Hiển thị tổng tiền của tập kết quả đang lọc
   */
  function updateKpiMetrics() {
    const list = state.filteredOrders;
    let totalRevenue = 0;
    let pendingCount = 0;
    let completedCount = 0;

    list.forEach((ord) => {
      const amt = Number(ord.totalAmount ?? 0);
      if (!isNaN(amt)) totalRevenue += amt;

      const st = (ord.status || '').toUpperCase();
      if (st === 'PENDING_APPROVAL' || Boolean(ord.requiresApproval)) {
        pendingCount++;
      }
      if (st === 'DELIVERED' || st === 'COMPLETED') {
        completedCount++;
      }
    });

    $('kpi-total-amount').textContent = formatVnd(totalRevenue);
    $('kpi-total-sub').textContent = `Tính trên ${list.length} đơn hàng đang lọc`;

    $('kpi-order-count').textContent = String(list.length);
    $('kpi-count-sub').textContent = `${list.length} / ${state.scopedOrders.length} đơn hàng`;

    $('kpi-pending-count').textContent = String(pendingCount);
    $('kpi-pending-sub').textContent = pendingCount > 0 ? 'Có đơn cần duyệt ngay' : 'Không có đơn tồn đọng';

    $('kpi-completed-count').textContent = String(completedCount);
    $('kpi-completed-sub').textContent = `${completedCount} đơn giao thành công`;
  }

  function updateToolbarMeta() {
    const hasFilter = Boolean(
      state.filters.search ||
      state.filters.status ||
      state.filters.dealerId ||
      (!IS_SALES_ONLY && state.filters.owner) ||
      state.filters.region ||
      state.filters.dateFrom ||
      state.filters.dateTo ||
      state.filters.quickChip !== 'all'
    );

    const resetBtn = $('btn-reset-filters');
    if (resetBtn) resetBtn.hidden = !hasFilter;

    const countText = $('ord-count-text');
    if (countText) {
      if (state.scopedOrders.length === 0) {
        countText.textContent = 'Chưa có đơn hàng nào trong hệ thống';
      } else if (hasFilter) {
        countText.textContent = `Tìm thấy ${state.filteredOrders.length} / ${state.scopedOrders.length} đơn hàng`;
      } else {
        countText.textContent = `Tổng cộng ${state.scopedOrders.length} đơn hàng`;
      }
    }
  }

  /* ── 10. Table Rendering ───────────────────────────────────── */
  function renderTable() {
    const tbody = $('ord-tbody');
    const start = (state.page - 1) * PAGE_SIZE;
    const slice = state.filteredOrders.slice(start, start + PAGE_SIZE);

    if (!slice.length) {
      tbody.innerHTML = `
        <tr>
          <td colspan="8">
            <div class="dlr-empty">
              <svg width="48" height="48" viewBox="0 0 24 24" stroke-width="1.2" fill="none" stroke="currentColor">
                <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/>
                <rect x="9" y="3" width="6" height="4" rx="2"/>
                <path d="M9 12h6M9 16h4"/>
              </svg>
              <p>${
                state.scopedOrders.length === 0
                  ? 'Chưa có đơn hàng nào.'
                  : 'Không tìm thấy đơn hàng phù hợp với bộ lọc hiện tại.'
              }</p>
              ${
                state.scopedOrders.length > 0
                  ? `<button type="button" class="btn btn--secondary" id="btn-empty-reset" style="margin-top:12px">Xoá bộ lọc</button>`
                  : ''
              }
            </div>
          </td>
        </tr>`;

      $('btn-empty-reset')?.addEventListener('click', resetAllFilters);
      return;
    }

    tbody.innerHTML = slice.map((ord) => {
      const dealer = ord.dealerId != null ? state.dealersMap.get(ord.dealerId) : null;
      const dealerName = ord.customerName || (dealer ? dealer.name : '—');
      const dealerCode = dealer ? dealer.code : '';
      const group = ord.customerGroup || (dealer ? dealer.customerGroup : null);
      const regionText = dealer ? (dealer.territoryName || '—') : '—';
      const ownerText = dealer ? (dealer.assignedUserFullName || dealer.assignedUsername || ord.createdBy || '—') : (ord.createdBy || '—');

      const isPending = (ord.status || '').toUpperCase() === 'PENDING_APPROVAL';
      const isCancellable = CAN_CANCEL && !['CANCELLED', 'REJECTED', 'DELIVERED', 'COMPLETED'].includes((ord.status || '').toUpperCase());

      const actions = `
        <div class="ord-table-actions">
          <button type="button" class="dlr-action-btn ord-btn-view" data-action="view" data-id="${ord.id}" title="Xem chi tiết đơn" aria-label="Xem chi tiết đơn ${escHtml(ord.orderCode)}">
            <svg viewBox="0 0 24 24"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>
          </button>
          ${CAN_APPROVE && isPending ? `
            <button type="button" class="dlr-action-btn ord-btn-approve" data-action="approve" data-id="${ord.id}" title="Phê duyệt đơn" aria-label="Phê duyệt ${escHtml(ord.orderCode)}">
              <svg viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"/></svg>
            </button>
            <button type="button" class="dlr-action-btn ord-btn-reject" data-action="reject" data-id="${ord.id}" title="Từ chối đơn" aria-label="Từ chối ${escHtml(ord.orderCode)}">
              <svg viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>` : ''}
          ${isCancellable ? `
            <button type="button" class="dlr-action-btn dlr-action-btn--warn" data-action="cancel" data-id="${ord.id}" title="Huỷ đơn hàng" aria-label="Huỷ ${escHtml(ord.orderCode)}">
              <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
            </button>` : ''}
        </div>`;

      const discountAmount = Number(ord.discountAmount ?? 0);
      const discountNote = discountAmount > 0
        ? `<span class="ord-discount-note">Đã trừ CK: -${formatVnd(discountAmount)}</span>`
        : '';

      return `
        <tr>
          <td>
            <button type="button" class="ord-code-btn" data-action="view" data-id="${ord.id}">
              ${escHtml(ord.orderCode || `#${ord.id}`)}
            </button>
          </td>
          <td>
            <span class="dlr-date">${formatDate(ord.createdAt)}</span>
          </td>
          <td>
            <div class="ord-dealer-cell">
              <span class="ord-dealer-name">${escHtml(dealerName)}</span>
              <div>
                ${dealerCode ? `<span class="dlr-code">${escHtml(dealerCode)}</span> ` : ''}
                ${group ? groupBadge(group) : ''}
              </div>
            </div>
          </td>
          <td>
            <span class="dlr-region-text">${escHtml(regionText)}</span>
          </td>
          <td>
            <span class="dlr-owner-text">${escHtml(ownerText)}</span>
          </td>
          <td>
            ${renderStatusBadge(ord.status)}
          </td>
          <td class="col-total">
            <span class="ord-total-val">${formatVnd(ord.totalAmount)}</span>
            ${discountNote}
          </td>
          <td>
            ${actions}
          </td>
        </tr>`;
    }).join('');

    // Bind action handlers
    tbody.querySelectorAll('[data-action="view"]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const orderId = Number(btn.dataset.id);
        openOrderDetailModal(orderId);
      });
    });

    tbody.querySelectorAll('[data-action="approve"]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const orderId = Number(btn.dataset.id);
        handleApproveOrder(orderId);
      });
    });

    tbody.querySelectorAll('[data-action="reject"]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const orderId = Number(btn.dataset.id);
        openRejectModal(orderId);
      });
    });

    tbody.querySelectorAll('[data-action="cancel"]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const orderId = Number(btn.dataset.id);
        openCancelModal(orderId);
      });
    });
  }

  /* ── 11. Pagination ────────────────────────────────────────── */
  function renderPagination() {
    const el = $('ord-pagination');
    const total = state.filteredOrders.length;
    const totalPages = Math.ceil(total / PAGE_SIZE);

    if (totalPages <= 1) {
      el.hidden = true;
      return;
    }

    el.hidden = false;
    const start = (state.page - 1) * PAGE_SIZE + 1;
    const end = Math.min(state.page * PAGE_SIZE, total);

    const pageButtons = [];
    for (let p = 1; p <= totalPages; p++) {
      pageButtons.push(
        `<button class="dlr-page-btn${p === state.page ? ' dlr-page-btn--active' : ''}" data-page="${p}">${p}</button>`,
      );
    }

    el.innerHTML = `
      <span class="dlr-pagination__info">${start}–${end} / ${total} đơn hàng</span>
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

  /* ── 12. Modal: Order Detail ───────────────────────────────── */
  async function openOrderDetailModal(orderId) {
    const modal = $('order-detail-modal');
    modal.hidden = false;

    // Reset detail contents
    $('ord-detail-title').textContent = 'Đang tải thông tin đơn hàng…';
    $('ord-detail-sub').textContent = '';
    $('ord-detail-status-badge').innerHTML = '';
    $('ord-detail-warning').hidden = true;
    $('ord-detail-debt-warning').hidden = true;
    $('ord-items-tbody').innerHTML = '<tr><td colspan="6" style="text-align:center;padding:20px;">Đang tải chi tiết dòng hàng…</td></tr>';
    $('btn-detail-approve').hidden = true;
    $('btn-detail-reject').hidden = true;
    $('btn-detail-cancel').hidden = true;
    $('ord-approval-box').hidden = true;

    try {
      const res = await Auth.apiFetch(`/orders/${orderId}`);
      const json = await res.json();
      if (!res.ok || !json.success || !json.data) {
        throw new Error(json.message || 'Không thể tải chi tiết đơn hàng');
      }
      const order = json.data;
      state.activeOrder = order;

      const dealer = order.dealerId != null ? state.dealersMap.get(order.dealerId) : null;

      $('ord-detail-title').textContent = `Đơn hàng ${order.orderCode || '#' + order.id}`;
      $('ord-detail-sub').textContent = `Tạo lúc ${formatDate(order.createdAt)} · Bởi ${order.createdBy || 'Hệ thống'}`;
      $('ord-detail-status-badge').innerHTML = renderStatusBadge(order.status);

      // Warning banners
      if (order.dealerLockWarning) {
        $('ord-detail-warning').hidden = false;
        $('ord-detail-warning').textContent = order.dealerLockWarning;
      }
      if (order.warning && order.warning !== order.dealerLockWarning) {
        $('ord-detail-debt-warning').hidden = false;
        $('ord-detail-debt-warning').innerHTML = `<span><strong>Cảnh báo:</strong> ${escHtml(order.warning)}</span>`;
      }

      // Dealer Info
      $('ord-detail-dealer-name').textContent = order.customerName || (dealer ? dealer.name : '—');
      $('ord-detail-group').innerHTML = groupBadge(order.customerGroup || (dealer ? dealer.customerGroup : null));
      $('ord-detail-region').textContent = dealer ? (dealer.territoryName || '—') : '—';
      $('ord-detail-owner').textContent = dealer ? (dealer.assignedUserFullName || dealer.assignedUsername || '—') : '—';

      // Order meta
      $('ord-detail-pricelist').textContent = order.priceListCode ? `Bảng giá #${order.priceListCode}` : 'Bảng giá mặc định';
      $('ord-detail-delivery-date').textContent = formatDateShort(order.expectedDeliveryDate);
      $('ord-detail-created-by').textContent = order.createdBy || '—';
      $('ord-detail-note').textContent = order.note || 'Không có ghi chú';

      // Items table
      const items = order.items || [];
      if (!items.length) {
        $('ord-items-tbody').innerHTML = '<tr><td colspan="6" style="text-align:center;padding:16px;">Đơn hàng không có sản phẩm.</td></tr>';
      } else {
        $('ord-items-tbody').innerHTML = items.map((it) => {
          const discount = Number(it.discountAmount ?? 0);
          return `
            <tr>
              <td>
                <strong>${escHtml(it.productName || it.productCode || 'Sản phẩm')}</strong>
                <div style="font-size:0.75rem;color:var(--ink-500);">${escHtml(it.productCode || '')}</div>
              </td>
              <td>${escHtml(it.unit || '—')}</td>
              <td class="text-right">${escHtml(it.quantity ?? 1)}</td>
              <td class="text-right">${formatVnd(it.price)}</td>
              <td class="text-right" style="color:var(--teal-deep);">${discount > 0 ? '-' + formatVnd(discount) : '—'}</td>
              <td class="text-right"><strong>${formatVnd(it.subtotalAmount ?? it.totalAmount)}</strong></td>
            </tr>`;
        }).join('');
      }

      // Financials
      $('ord-sum-subtotal').textContent = formatVnd(order.subtotalAmount || order.totalAmount);
      $('ord-sum-discount').textContent = '-' + formatVnd(order.discountAmount ?? 0);
      $('ord-sum-total').textContent = formatVnd(order.totalAmount);

      // Approval history
      if (order.approvedBy || order.approvalReason) {
        $('ord-approval-box').hidden = false;
        let approvalHtml = '';
        if (order.approvalReason) {
          approvalHtml += `<div><strong>Lý do yêu cầu duyệt:</strong> ${escHtml(order.approvalReason)}</div>`;
        }
        if (order.approvedBy) {
          approvalHtml += `<div style="margin-top:4px;"><strong>Người duyệt/xử lý:</strong> ${escHtml(order.approvedBy)} (${formatDate(order.approvedAt)})</div>`;
        }
        $('ord-approval-box-content').innerHTML = approvalHtml;
      }

      // Actions in modal
      const isPending = (order.status || '').toUpperCase() === 'PENDING_APPROVAL';
      const isCancellable = CAN_CANCEL && !['CANCELLED', 'REJECTED', 'DELIVERED', 'COMPLETED'].includes((order.status || '').toUpperCase());

      if (CAN_APPROVE && isPending) {
        $('btn-detail-approve').hidden = false;
        $('btn-detail-reject').hidden = false;
      }
      if (isCancellable) {
        $('btn-detail-cancel').hidden = false;
      }
    } catch (e) {
      $('ord-detail-title').textContent = 'Lỗi xem chi tiết đơn';
      $('ord-items-tbody').innerHTML = `<tr><td colspan="6" style="text-align:center;color:var(--danger);padding:16px;">${escHtml(e.message)}</td></tr>`;
    }
  }

  function closeOrderDetailModal() {
    $('order-detail-modal').hidden = true;
    state.activeOrder = null;
  }

  /* ── 13. Actions: Approve / Reject / Cancel ────────────────── */
  async function handleApproveOrder(orderId) {
    if (!CAN_APPROVE) return;
    if (!confirm('Bạn có chắc chắn muốn phê duyệt đơn hàng này?')) return;
    try {
      const res = await Auth.apiFetch(`/orders/${orderId}/approve`, { method: 'PUT' });
      const json = await res.json();
      if (!res.ok || !json.success) {
        throw new Error(json.message || 'Phê duyệt đơn hàng thất bại');
      }
      showToast(json.message || 'Đã phê duyệt đơn hàng thành công', 'success');
      closeOrderDetailModal();
      await loadInitialData();
    } catch (err) {
      showToast(err.message || 'Lỗi khi phê duyệt đơn hàng', 'error');
    }
  }

  function openRejectModal(orderId) {
    if (!CAN_APPROVE) return;
    $('reject-order-id').value = orderId;
    $('reject-modal-sub').textContent = `Đơn hàng #${orderId}`;
    $('reject-reason').value = '';
    $('reject-message').hidden = true;
    $('reject-modal').hidden = false;
    setTimeout(() => $('reject-reason').focus(), 50);
  }

  function closeRejectModal() {
    $('reject-modal').hidden = true;
  }

  async function handleRejectSubmit(e) {
    e.preventDefault();
    const orderId = $('reject-order-id').value;
    const reason = $('reject-reason').value.trim();
    if (!reason) {
      $('reject-message').textContent = 'Vui lòng nhập lý do từ chối';
      $('reject-message').hidden = false;
      return;
    }
    const btn = $('btn-confirm-reject');
    btn.disabled = true;
    try {
      const res = await Auth.apiFetch(`/orders/${orderId}/reject?reason=${encodeURIComponent(reason)}`, {
        method: 'PUT',
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        throw new Error(json.message || 'Từ chối đơn hàng thất bại');
      }
      showToast(json.message || 'Đã từ chối đơn hàng thành công', 'success');
      closeRejectModal();
      closeOrderDetailModal();
      await loadInitialData();
    } catch (err) {
      $('reject-message').textContent = err.message || 'Lỗi từ chối đơn';
      $('reject-message').hidden = false;
    } finally {
      btn.disabled = false;
    }
  }

  function openCancelModal(orderId) {
    $('cancel-order-id').value = orderId;
    $('cancel-order-sub').textContent = `Đơn hàng #${orderId}`;
    $('cancel-order-reason').value = '';
    $('cancel-order-message').hidden = true;
    $('cancel-order-modal').hidden = false;
    setTimeout(() => $('cancel-order-reason').focus(), 50);
  }

  function closeCancelModal() {
    $('cancel-order-modal').hidden = true;
  }

  async function handleCancelSubmit(e) {
    e.preventDefault();
    const orderId = $('cancel-order-id').value;
    const reason = $('cancel-order-reason').value.trim();
    if (!reason) {
      $('cancel-order-message').textContent = 'Vui lòng nhập lý do huỷ đơn hàng';
      $('cancel-order-message').hidden = false;
      return;
    }
    const btn = $('btn-submit-cancel-order');
    btn.disabled = true;
    try {
      const res = await Auth.apiFetch(`/orders/${orderId}/cancel`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason }),
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        throw new Error(json.message || 'Huỷ đơn hàng thất bại');
      }
      showToast(json.message || 'Đã huỷ đơn hàng và giải phóng tồn kho', 'success');
      closeCancelModal();
      closeOrderDetailModal();
      await loadInitialData();
    } catch (err) {
      $('cancel-order-message').textContent = err.message || 'Lỗi huỷ đơn hàng';
      $('cancel-order-message').hidden = false;
    } finally {
      btn.disabled = false;
    }
  }

  /* ── 14. Event Listeners & Reset ───────────────────────────── */
  function resetAllFilters() {
    state.filters.search = '';
    state.filters.status = '';
    state.filters.dealerId = '';
    if (!IS_SALES_ONLY) state.filters.owner = '';
    state.filters.region = '';
    state.filters.dateFrom = '';
    state.filters.dateTo = '';
    state.filters.datePreset = 'all';
    state.filters.quickChip = 'all';

    $('ord-search').value = '';
    $('ord-search-clear').hidden = true;
    $('ord-filter-status').value = '';
    $('ord-filter-dealer').value = '';
    if (!IS_SALES_ONLY) $('ord-filter-owner').value = '';
    $('ord-filter-region').value = '';
    $('ord-date-from').value = '';
    $('ord-date-to').value = '';

    document.querySelectorAll('.ord-chip').forEach((c) => {
      c.classList.toggle('ord-chip--active', c.dataset.date === 'all');
    });
    document.querySelectorAll('#ord-quick-chips .dlr-chip').forEach((c) => {
      c.classList.toggle('dlr-chip--active', c.dataset.chip === 'all');
    });

    applyFilters();
  }

  function setupEventListeners() {
    // Search input (debounced 150ms)
    let searchTimer = null;
    $('ord-search')?.addEventListener('input', (e) => {
      clearTimeout(searchTimer);
      const val = e.target.value;
      $('ord-search-clear').hidden = !val;
      searchTimer = setTimeout(() => {
        state.filters.search = val;
        applyFilters();
      }, 150);
    });

    $('ord-search-clear')?.addEventListener('click', () => {
      $('ord-search').value = '';
      $('ord-search-clear').hidden = true;
      state.filters.search = '';
      applyFilters();
    });

    // Dropdown filters
    $('ord-filter-status')?.addEventListener('change', (e) => {
      state.filters.status = e.target.value;
      applyFilters();
    });

    $('ord-filter-dealer')?.addEventListener('change', (e) => {
      state.filters.dealerId = e.target.value;
      applyFilters();
    });

    $('ord-filter-owner')?.addEventListener('change', (e) => {
      state.filters.owner = e.target.value;
      applyFilters();
    });

    $('ord-filter-region')?.addEventListener('change', (e) => {
      state.filters.region = e.target.value;
      applyFilters();
    });

    // Date range inputs
    $('ord-date-from')?.addEventListener('change', (e) => {
      state.filters.dateFrom = e.target.value;
      // Clear preset active state
      document.querySelectorAll('.ord-chip').forEach((c) => c.classList.remove('ord-chip--active'));
      applyFilters();
    });

    $('ord-date-to')?.addEventListener('change', (e) => {
      state.filters.dateTo = e.target.value;
      // Clear preset active state
      document.querySelectorAll('.ord-chip').forEach((c) => c.classList.remove('ord-chip--active'));
      applyFilters();
    });

    // Date preset chips
    document.querySelectorAll('.ord-chip[data-date]').forEach((chip) => {
      chip.addEventListener('click', () => {
        document.querySelectorAll('.ord-chip[data-date]').forEach((c) => c.classList.remove('ord-chip--active'));
        chip.classList.add('ord-chip--active');

        const preset = chip.dataset.date;
        state.filters.datePreset = preset;

        const now = new Date();
        const yyyy = now.getFullYear();
        const mm = String(now.getMonth() + 1).padStart(2, '0');
        const dd = String(now.getDate()).padStart(2, '0');
        const todayStr = `${yyyy}-${mm}-${dd}`;

        if (preset === 'today') {
          state.filters.dateFrom = todayStr;
          state.filters.dateTo = todayStr;
        } else if (preset === '7days') {
          const past = new Date();
          past.setDate(past.getDate() - 7);
          const pyyyy = past.getFullYear();
          const pmm = String(past.getMonth() + 1).padStart(2, '0');
          const pdd = String(past.getDate()).padStart(2, '0');
          state.filters.dateFrom = `${pyyyy}-${pmm}-${pdd}`;
          state.filters.dateTo = todayStr;
        } else if (preset === 'thisMonth') {
          state.filters.dateFrom = `${yyyy}-${mm}-01`;
          state.filters.dateTo = todayStr;
        } else {
          state.filters.dateFrom = '';
          state.filters.dateTo = '';
        }

        $('ord-date-from').value = state.filters.dateFrom;
        $('ord-date-to').value = state.filters.dateTo;
        applyFilters();
      });
    });

    // Quick status chips
    document.querySelectorAll('#ord-quick-chips .dlr-chip').forEach((chip) => {
      chip.addEventListener('click', () => {
        document.querySelectorAll('#ord-quick-chips .dlr-chip').forEach((c) => c.classList.remove('dlr-chip--active'));
        chip.classList.add('dlr-chip--active');
        state.filters.quickChip = chip.dataset.chip;
        applyFilters();
      });
    });

    // Reset button
    $('btn-reset-filters')?.addEventListener('click', resetAllFilters);

    // Modal close events
    document.querySelectorAll('[data-close-detail]').forEach((btn) => {
      btn.addEventListener('click', closeOrderDetailModal);
    });
    $('order-detail-modal')?.addEventListener('click', (e) => {
      if (e.target === $('order-detail-modal')) closeOrderDetailModal();
    });

    document.querySelectorAll('[data-close-reject]').forEach((btn) => {
      btn.addEventListener('click', closeRejectModal);
    });
    $('reject-modal')?.addEventListener('click', (e) => {
      if (e.target === $('reject-modal')) closeRejectModal();
    });
    $('reject-form')?.addEventListener('submit', handleRejectSubmit);

    document.querySelectorAll('[data-close-cancel-order]').forEach((btn) => {
      btn.addEventListener('click', closeCancelModal);
    });
    $('cancel-order-modal')?.addEventListener('click', (e) => {
      if (e.target === $('cancel-order-modal')) closeCancelModal();
    });
    $('cancel-order-form')?.addEventListener('submit', handleCancelSubmit);

    // Buttons in detail modal
    $('btn-detail-approve')?.addEventListener('click', () => {
      if (state.activeOrder) handleApproveOrder(state.activeOrder.id);
    });
    $('btn-detail-reject')?.addEventListener('click', () => {
      if (state.activeOrder) openRejectModal(state.activeOrder.id);
    });
    $('btn-detail-cancel')?.addEventListener('click', () => {
      if (state.activeOrder) openCancelModal(state.activeOrder.id);
    });

    // Escape key closes modals
    window.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        closeOrderDetailModal();
        closeRejectModal();
        closeCancelModal();
      }
    });
  }

  /* ── 15. Bootstrap ─────────────────────────────────────────── */
  setupEventListeners();
  loadInitialData();
})();

