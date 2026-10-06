/**
 * dealers.js — S3-07 Quản lý Đại lý & Khóa / Mở Giao Dịch
 *
 * Nghiệp vụ cốt lõi (User Story S3-07):
 * Là Kế toán công nợ, tôi muốn khoá hoặc mở giao dịch với một đại lý,
 * để dừng bán ngay cho đại lý có dấu hiệu mất khả năng thanh toán.
 *
 * Yêu cầu đặc tả:
 * 1. Đại lý bị khoá không tạo được đơn mới, kể cả trên cổng đặt hàng
 * 2. Bắt buộc nhập lý do khoá
 * 3. Đơn đang dở của đại lý bị khoá vẫn xử lý tiếp được nhưng có cảnh báo
 */

(() => {
  /* ── 1. Auth Guard ─────────────────────────────────────────── */
  if (!Auth.requireLogin()) return;

  const currentUser = Auth.getUser() || {};
  const userRoles = currentUser.roles || [];

  // Quyền xem trang: Admin, Quản lý KD, Kế toán, Nhân viên KD, Chỉ xem
  const CAN_VIEW = Auth.hasAnyRole(
    "ROLE_ADMIN",
    "ROLE_SALES_MANAGER",
    "ROLE_ACCOUNTANT",
    "ROLE_SALES",
    "ROLE_VIEWER"
  );
  if (!CAN_VIEW) {
    location.href = Auth.getErrorPageUrl ? Auth.getErrorPageUrl(403) : "/error?status=403";
    return;
  }

  // Quyền khoá / mở giao dịch: Kế toán công nợ, Admin, Quản lý KD
  const CAN_LOCK_UNLOCK = Auth.hasAnyRole("ROLE_ADMIN", "ROLE_ACCOUNTANT", "ROLE_SALES_MANAGER");
  const CAN_MANAGE_DEALERS = Auth.hasAnyRole("ROLE_ADMIN", "ROLE_SALES_MANAGER");

  /* ── 2. DOM Helpers ────────────────────────────────────────── */
  const $ = (id) => document.getElementById(id);
  const escHtml = (v) =>
    String(v ?? "").replace(/[&<>'"]/g, (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[c]
    );
  const formatCurrency = (val) =>
    new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(Number(val) || 0);
  const formatNumber = (val) =>
    new Intl.NumberFormat("vi-VN").format(Number(val) || 0);

  /* ── 3. Data Storage & Initial Mock Data ───────────────────── */
  const STORAGE_KEY_DEALERS = "salestock_dealers_s307_v1";
  const STORAGE_KEY_ORDERS = "salestock_orders_s307_v1";

  const DEFAULT_DEALERS = [
    {
      id: 1,
      code: "DL001",
      name: "Công ty TNHH Thương mại Minh Đức",
      taxCode: "0102847592",
      customerGroup: "DAI_LY_CAP_1",
      debtLimit: 100000000,
      debtAmount: 45000000,
      region: "Hà Nội - Miền Bắc",
      assignedUser: "Nguyễn Văn Hùng (KD)",
      phone: "0912 345 678",
      address: "Số 18, Đường Láng, Đống Đa, Hà Nội",
      status: "ACTIVE", // ACTIVE | LOCKED | INACTIVE
      lockReason: null,
      lockedAt: null,
      lockedBy: null,
    },
    {
      id: 2,
      code: "DL002",
      name: "Công ty CP Phân phối Toàn Cầu",
      taxCode: "0301984721",
      customerGroup: "DAI_LY_CAP_1",
      debtLimit: 150000000,
      debtAmount: 185000000, // Vượt hạn mức nợ
      region: "TP. Hồ Chí Minh",
      assignedUser: "Trần Thị Mai (KD)",
      phone: "0908 765 432",
      address: "Tầng 5, Toà nhà Bitexco, Q.1, TP.HCM",
      status: "LOCKED", // Đã bị Kế toán khoá do mất khả năng thanh toán
      lockReason: "Vượt hạn mức tín dụng 35 triệu và quá hạn thanh toán 45 ngày; có dấu hiệu mất khả năng thanh toán.",
      lockedAt: "04/10/2026 09:30",
      lockedBy: "Kế toán công nợ (accountant)",
    },
    {
      id: 3,
      code: "DL003",
      name: "Đại lý Bách Hoá Tổng Hợp Hải Phòng",
      taxCode: "0200837461",
      customerGroup: "DAI_LY_CAP_2",
      debtLimit: 50000000,
      debtAmount: 12000000,
      region: "Hải Phòng - Duyên Hải",
      assignedUser: "Lê Hoàng Nam (KD)",
      phone: "0934 112 233",
      address: "45 Lạch Tray, Ngô Quyền, Hải Phòng",
      status: "ACTIVE",
      lockReason: null,
      lockedAt: null,
      lockedBy: null,
    },
    {
      id: 4,
      code: "DL004",
      name: "Công ty CP Bán lẻ & Chuỗi Siêu thị Phương Nam",
      taxCode: "0312456789",
      customerGroup: "DAI_LY_CAP_2",
      debtLimit: 60000000,
      debtAmount: 82000000, // Vượt hạn mức
      region: "Cần Thơ - Miền Tây",
      assignedUser: "Phạm Văn Sơn (KD)",
      phone: "0977 889 900",
      address: "128 Đường 30/4, Ninh Kiều, Cần Thơ",
      status: "LOCKED", // Đã bị Kế toán khoá
      lockReason: "Chậm thanh toán công nợ 3 kỳ liên tiếp; tạm dừng xuất hàng theo chỉ đạo Kế toán trưởng.",
      lockedAt: "02/10/2026 14:15",
      lockedBy: "Kế toán công nợ (accountant)",
    },
    {
      id: 5,
      code: "DL005",
      name: "Siêu thị Tiện ích 247 Đà Nẵng",
      taxCode: "0401726354",
      customerGroup: "KHACH_LE",
      debtLimit: 20000000,
      debtAmount: 0,
      region: "Đà Nẵng - Miền Trung",
      assignedUser: "Võ Minh Trí (KD)",
      phone: "0982 556 778",
      address: "88 Nguyễn Văn Linh, Hải Châu, Đà Nẵng",
      status: "INACTIVE",
      lockReason: null,
      lockedAt: null,
      lockedBy: null,
    },
  ];

  const DEFAULT_PRODUCTS = [
    { id: 101, code: "SP001", name: "Bia lon Hà Nội 330ml (Thùng 24 lon)", unit: "Thùng", price: 260000 },
    { id: 102, code: "SP002", name: "Bia lon Sài Gòn Special 330ml (Thùng 24 lon)", unit: "Thùng", price: 340000 },
    { id: 103, code: "SP003", name: "Nước ngọt Coca-Cola Sleek 330ml (Thùng 24 lon)", unit: "Thùng", price: 210000 },
    { id: 104, code: "SP004", name: "Nước tăng lực RedBull Sleek 250ml (Thùng 24 lon)", unit: "Thùng", price: 275000 },
    { id: 105, code: "SP005", name: "Sữa chua uống tiệt trùng Probi 65ml (Thùng 30 chai)", unit: "Thùng", price: 195000 },
  ];

  const DEFAULT_ORDERS = [
    {
      id: 1001,
      orderCode: "DH-20261005-001",
      dealerId: 2, // Thuộc DL002 (BỊ KHOÁ)
      dealerName: "Công ty CP Phân phối Toàn Cầu",
      createdAt: "05/10/2026 08:45",
      status: "PENDING_APPROVAL", // Đơn đang dở - Chờ duyệt
      totalAmount: 35500000,
      note: "Đơn hàng phát sinh trước thời điểm khoá giao dịch.",
      items: [
        { productName: "Bia lon Hà Nội 330ml (Thùng 24 lon)", unit: "Thùng", quantity: 50, price: 260000, total: 13000000 },
        { productName: "Bia lon Sài Gòn Special 330ml (Thùng 24 lon)", unit: "Thùng", quantity: 50, price: 340000, total: 17000000 },
        { productName: "Sữa chua uống Probi 65ml", unit: "Thùng", quantity: 28, price: 195000, total: 5500000 },
      ],
    },
    {
      id: 1002,
      orderCode: "DH-20261005-002",
      dealerId: 2, // Thuộc DL002 (BỊ KHOÁ)
      dealerName: "Công ty CP Phân phối Toàn Cầu",
      createdAt: "05/10/2026 11:15",
      status: "RECORDED", // Đơn đang dở - Đã ghi sổ, chờ xuất kho
      totalAmount: 18200000,
      note: "Đơn đang dở chờ thủ kho xuất hàng.",
      items: [
        { productName: "Nước ngọt Coca-Cola Sleek 330ml", unit: "Thùng", quantity: 40, price: 210000, total: 8400000 },
        { productName: "Nước tăng lực RedBull Sleek 250ml", unit: "Thùng", quantity: 35, price: 280000, total: 9800000 },
      ],
    },
    {
      id: 1003,
      orderCode: "DH-20261006-003",
      dealerId: 1, // Thuộc DL001 (ĐANG GIAO DỊCH)
      dealerName: "Công ty TNHH Thương mại Minh Đức",
      createdAt: "06/10/2026 09:20",
      status: "RECORDED", // Đã ghi sổ
      totalAmount: 52000000,
      note: "Giao hàng buổi chiều tại kho Láng.",
      items: [
        { productName: "Bia lon Hà Nội 330ml (Thùng 24 lon)", unit: "Thùng", quantity: 200, price: 260000, total: 52000000 },
      ],
    },
    {
      id: 1004,
      orderCode: "DH-20261004-004",
      dealerId: 4, // Thuộc DL004 (BỊ KHOÁ)
      dealerName: "Công ty CP Bán lẻ & Chuỗi Siêu thị Phương Nam",
      createdAt: "04/10/2026 15:30",
      status: "PROCESSING", // Đơn đang dở - Đang giao hàng
      totalAmount: 9800000,
      note: "Đơn đang xử lý dở tại thời điểm đại lý bị khoá.",
      items: [
        { productName: "Nước ngọt Coca-Cola Sleek 330ml", unit: "Thùng", quantity: 20, price: 210000, total: 4200000 },
        { productName: "Nước tăng lực RedBull Sleek 250ml", unit: "Thùng", quantity: 20, price: 280000, total: 5600000 },
      ],
    },
    {
      id: 1005,
      orderCode: "DH-20261003-005",
      dealerId: 3, // Thuộc DL003 (ĐANG GIAO DỊCH)
      dealerName: "Đại lý Bách Hoá Tổng Hợp Hải Phòng",
      createdAt: "03/10/2026 10:00",
      status: "COMPLETED", // Đã hoàn tất
      totalAmount: 26000000,
      note: "Đã giao hàng và thanh toán đủ.",
      items: [
        { productName: "Bia lon Hà Nội 330ml (Thùng 24 lon)", unit: "Thùng", quantity: 100, price: 260000, total: 26000000 },
      ],
    },
  ];

  /* ── 4. App State ─────────────────────────────────────────── */
  const state = {
    dealers: [],
    products: DEFAULT_PRODUCTS,
    orders: [],
    activeTab: "dealers", // "dealers" | "portal" | "orders"
    filterGroup: "",
    filterStatus: "",
    searchQuery: "",
    ordersFilterStatus: "",
    ordersFilterLocked: "",
    portalItems: [],
  };

  function loadState() {
    try {
      const savedDealers = localStorage.getItem(STORAGE_KEY_DEALERS);
      state.dealers = savedDealers ? JSON.parse(savedDealers) : [...DEFAULT_DEALERS];
    } catch {
      state.dealers = [...DEFAULT_DEALERS];
    }

    try {
      const savedOrders = localStorage.getItem(STORAGE_KEY_ORDERS);
      state.orders = savedOrders ? JSON.parse(savedOrders) : [...DEFAULT_ORDERS];
    } catch {
      state.orders = [...DEFAULT_ORDERS];
    }
  }

  function saveDealers() {
    try {
      localStorage.setItem(STORAGE_KEY_DEALERS, JSON.stringify(state.dealers));
    } catch (e) {
      console.warn("Could not save dealers to localStorage", e);
    }
  }

  function saveOrders() {
    try {
      localStorage.setItem(STORAGE_KEY_ORDERS, JSON.stringify(state.orders));
    } catch (e) {
      console.warn("Could not save orders to localStorage", e);
    }
  }

  /* ── 5. Sidebar & Layout Setup ────────────────────────────── */
  function initLayout() {
    const user = currentUser;

    const initials = (user.fullName || user.username || "SS")
      .split(" ")
      .map((w) => w[0])
      .slice(0, 2)
      .join("")
      .toUpperCase();
    if ($("user-avatar")) $("user-avatar").textContent = initials;
    if ($("user-name")) $("user-name").textContent = user.fullName || user.username;
    if ($("user-role")) {
      $("user-role").textContent =
        user.roles?.map((r) => MenuConfig.getRoleDisplayName(r)).join(", ") || "Kế toán";
    }

    // Sidebar navigation
    const nav = $("sidebar-nav");
    if (nav) {
      const items = MenuConfig.getMenuForUser(user);
      let lastSection = null;
      nav.innerHTML = "";
      items.forEach((item) => {
        if (item.section && item.section !== lastSection) {
          lastSection = item.section;
          const label = document.createElement("div");
          label.className = "sidebar__section-label";
          label.textContent = item.section;
          nav.appendChild(label);
        }
        const a = document.createElement("a");
        a.className = "sidebar__link" + (item.id === "dealers" ? " sidebar__link--active" : "");
        a.href = item.href;
        a.innerHTML = `<svg viewBox="0 0 24 24">${item.icon}</svg><span>${escHtml(item.label)}</span>`;
        nav.appendChild(a);
      });
    }

    // Logout events
    const doLogout = () => Auth.logout?.();
    $("btn-logout")?.addEventListener("click", doLogout);
    $("btn-logout-topbar")?.addEventListener("click", doLogout);

    // Mobile sidebar toggle
    $("sidebar-toggle")?.addEventListener("click", () => {
      $("sidebar")?.classList.toggle("sidebar--open");
      $("sidebar-overlay")?.classList.toggle("sidebar-overlay--visible");
    });
    $("sidebar-close")?.addEventListener("click", () => {
      $("sidebar")?.classList.remove("sidebar--open");
      $("sidebar-overlay")?.classList.remove("sidebar-overlay--visible");
    });
    $("sidebar-overlay")?.addEventListener("click", () => {
      $("sidebar")?.classList.remove("sidebar--open");
      $("sidebar-overlay")?.classList.remove("sidebar-overlay--visible");
    });

    // Permission button visibility
    if ($("btn-add-dealer")) {
      $("btn-add-dealer").hidden = !CAN_MANAGE_DEALERS && !CAN_LOCK_UNLOCK;
    }
  }

  /* ── 6. Metrics Calculation ───────────────────────────────── */
  function updateMetrics() {
    const total = state.dealers.length;
    const active = state.dealers.filter((d) => d.status === "ACTIVE").length;
    const locked = state.dealers.filter((d) => d.status === "LOCKED").length;

    // Đơn dở của đại lý bị khoá (PENDING_APPROVAL, RECORDED, PROCESSING)
    const lockedDealerIds = new Set(
      state.dealers.filter((d) => d.status === "LOCKED").map((d) => d.id)
    );
    const unfinishedStatuses = ["PENDING_APPROVAL", "RECORDED", "PROCESSING"];
    const pendingLockedOrders = state.orders.filter(
      (o) => lockedDealerIds.has(o.dealerId) && unfinishedStatuses.includes(o.status)
    ).length;

    if ($("metric-total-dealers")) $("metric-total-dealers").textContent = total;
    if ($("metric-active-dealers")) $("metric-active-dealers").textContent = active;
    if ($("metric-locked-dealers")) $("metric-locked-dealers").textContent = locked;
    if ($("metric-pending-locked-orders")) $("metric-pending-locked-orders").textContent = pendingLockedOrders;

    const warnChip = $("tab-chip-warn-count");
    if (warnChip) {
      warnChip.textContent = `${pendingLockedOrders} đơn có cảnh báo`;
      warnChip.hidden = pendingLockedOrders === 0;
    }
  }

  /* ── 7. Tabs Switcher ─────────────────────────────────────── */
  function switchTab(tabId) {
    state.activeTab = tabId;

    document.querySelectorAll(".dlr-tab").forEach((btn) => {
      const isTarget = btn.dataset.tab === tabId;
      btn.classList.toggle("dlr-tab--active", isTarget);
      btn.setAttribute("aria-selected", isTarget ? "true" : "false");
    });

    document.querySelectorAll(".tab-panel").forEach((panel) => {
      panel.classList.toggle("tab-panel--active", panel.id === `panel-${tabId}`);
    });

    if (tabId === "dealers") {
      renderDealersTable();
    } else if (tabId === "portal") {
      populatePortalDealersSelect();
      initPortalOrderCode();
    } else if (tabId === "orders") {
      renderOrdersTable();
    }
  }

  function initTabs() {
    document.querySelectorAll(".dlr-tab").forEach((btn) => {
      btn.addEventListener("click", () => switchTab(btn.dataset.tab));
    });

    $("btn-quick-portal")?.addEventListener("click", () => switchTab("portal"));
    $("btn-quick-orders")?.addEventListener("click", () => switchTab("orders"));

    // Check query params if any: ?tab=portal | ?tab=orders
    const urlParams = new URLSearchParams(window.location.search);
    const tabParam = urlParams.get("tab");
    if (tabParam && ["dealers", "portal", "orders"].includes(tabParam)) {
      switchTab(tabParam);
    }
  }

  /* ── 8. Render Panel 1: Dealers Table ─────────────────────── */
  const GROUP_LABELS = {
    DAI_LY_CAP_1: "Đại lý cấp 1",
    DAI_LY_CAP_2: "Đại lý cấp 2",
    KHACH_LE: "Khách lẻ",
  };

  function renderDealersTable() {
    const tbody = $("dlr-tbody");
    if (!tbody) return;

    const q = (state.searchQuery || "").trim().toLowerCase();
    const g = state.filterGroup;
    const s = state.filterStatus;

    const filtered = state.dealers.filter((d) => {
      const matchSearch =
        !q ||
        (d.code || "").toLowerCase().includes(q) ||
        (d.name || "").toLowerCase().includes(q) ||
        (d.taxCode || "").toLowerCase().includes(q) ||
        (d.phone || "").toLowerCase().includes(q);
      const matchGroup = !g || d.customerGroup === g;
      const matchStatus = !s || d.status === s;
      return matchSearch && matchGroup && matchStatus;
    });

    if (!filtered.length) {
      tbody.innerHTML = `
        <tr>
          <td colspan="8" style="text-align:center; padding: 40px 20px;">
            <div style="color: var(--ink-500); font-size: 0.9375rem;">
              Không tìm thấy đại lý phù hợp với bộ lọc tìm kiếm.
            </div>
          </td>
        </tr>`;
      return;
    }

    tbody.innerHTML = filtered
      .map((d) => {
        const isLocked = d.status === "LOCKED";
        const isActive = d.status === "ACTIVE";
        const isInactive = d.status === "INACTIVE";

        const debtLimit = Number(d.debtLimit) || 0;
        const debtAmount = Number(d.debtAmount) || 0;
        const debtPercent = debtLimit > 0 ? Math.min(Math.round((debtAmount / debtLimit) * 100), 100) : 0;
        const isOverLimit = debtAmount > debtLimit && debtLimit > 0;

        let statusBadge = "";
        if (isLocked) {
          statusBadge = `
            <div>
              <span class="badge badge--danger" title="Bị khoá: ${escHtml(d.lockReason || '')}">
                <svg viewBox="0 0 24 24" width="12" height="12"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                Bị khoá giao dịch
              </span>
              <div class="locked-reason-snippet" title="${escHtml(d.lockReason || '')}">
                <span>Lý do: ${escHtml(d.lockReason || 'Chưa rõ')}</span>
                <button type="button" class="btn-view-reason" data-action="view-reason" data-id="${d.id}">(Chi tiết)</button>
              </div>
            </div>`;
        } else if (isActive) {
          statusBadge = `<span class="badge badge--active"><span class="badge-dot"></span>Đang giao dịch</span>`;
        } else {
          statusBadge = `<span class="badge badge--muted">Ngừng giao dịch</span>`;
        }

        // Actions buttons
        let actions = '<div class="dlr-row-actions">';

        if (CAN_LOCK_UNLOCK) {
          if (isActive) {
            actions += `
              <button type="button" class="dlr-action-btn dlr-action-btn--lock" data-action="lock" data-id="${d.id}" title="Khoá giao dịch để dừng bán hàng">
                <svg viewBox="0 0 24 24" width="13" height="13"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                <span>Khoá</span>
              </button>`;
          } else if (isLocked) {
            actions += `
              <button type="button" class="dlr-action-btn dlr-action-btn--unlock" data-action="unlock" data-id="${d.id}" title="Mở khoá giao dịch">
                <svg viewBox="0 0 24 24" width="13" height="13"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 9.9-1"/></svg>
                <span>Mở khoá</span>
              </button>`;
          }
        }

        actions += `
          <button type="button" class="dlr-action-btn" data-action="order" data-id="${d.id}" title="Tạo đơn hàng trên Cổng đặt hàng">
            <svg viewBox="0 0 24 24" width="13" height="13"><circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/></svg>
            <span>Tạo đơn</span>
          </button>
          <button type="button" class="dlr-action-btn" data-action="view-orders" data-id="${d.id}" title="Xem đơn hàng của đại lý">
            <svg viewBox="0 0 24 24" width="13" height="13"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          </button>
        </div>`;

        return `
          <tr data-dealer-id="${d.id}">
            <td><span class="dlr-code">${escHtml(d.code)}</span></td>
            <td>
              <div class="dlr-name-cell">
                <span class="dlr-name-title">${escHtml(d.name)}</span>
                <span class="dlr-name-meta">${escHtml(d.address || d.region || '—')}</span>
              </div>
            </td>
            <td>${escHtml(d.taxCode || '—')}</td>
            <td>${escHtml(GROUP_LABELS[d.customerGroup] || d.customerGroup)}</td>
            <td>
              <div class="debt-cell">
                <div class="debt-numbers">
                  <span class="${isOverLimit ? 'text-danger' : ''}">Nợ: ${formatCurrency(debtAmount)}</span>
                  <span style="color:var(--ink-400)">/ ${formatNumber(debtLimit / 1000000)}tr</span>
                </div>
                <div class="debt-bar-wrap" title="${debtPercent}% hạn mức">
                  <div class="debt-bar ${isOverLimit ? 'debt-bar--danger' : debtPercent > 80 ? 'debt-bar--warn' : ''}" style="width: ${debtPercent}%;"></div>
                </div>
              </div>
            </td>
            <td>
              <div>${escHtml(d.assignedUser || '—')}</div>
              <small style="color:var(--ink-500)">${escHtml(d.phone || '')}</small>
            </td>
            <td>${statusBadge}</td>
            <td>${actions}</td>
          </tr>`;
      })
      .join("");

    bindDealersTableEvents();
  }

  function bindDealersTableEvents() {
    const tbody = $("dlr-tbody");
    if (!tbody) return;

    tbody.querySelectorAll('[data-action="lock"]').forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = Number(btn.dataset.id);
        const dealer = state.dealers.find((d) => d.id === id);
        if (dealer) openLockModal(dealer);
      });
    });

    tbody.querySelectorAll('[data-action="unlock"]').forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = Number(btn.dataset.id);
        const dealer = state.dealers.find((d) => d.id === id);
        if (dealer) openUnlockModal(dealer);
      });
    });

    tbody.querySelectorAll('[data-action="view-reason"]').forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = Number(btn.dataset.id);
        const dealer = state.dealers.find((d) => d.id === id);
        if (dealer) openViewReasonModal(dealer);
      });
    });

    tbody.querySelectorAll('[data-action="order"]').forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = Number(btn.dataset.id);
        switchTab("portal");
        if ($("portal-dealer-select")) {
          $("portal-dealer-select").value = String(id);
          onPortalDealerChange();
        }
      });
    });

    tbody.querySelectorAll('[data-action="view-orders"]').forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = Number(btn.dataset.id);
        const dealer = state.dealers.find((d) => d.id === id);
        switchTab("orders");
        if (dealer && dealer.status === "LOCKED") {
          if ($("orders-filter-locked")) $("orders-filter-locked").value = "LOCKED_ONLY";
          renderOrdersTable();
        }
      });
    });
  }

  /* ── 9. Modal Khoá Giao Dịch (BẮT BUỘC NHẬP LÝ DO) ────────── */
  function openLockModal(dealer) {
    if (!dealer) return;

    $("lock-dlr-id").value = dealer.id;
    $("lock-target-name").textContent = dealer.name;
    $("lock-target-code").textContent = dealer.code;
    $("lock-target-debt").textContent = formatCurrency(dealer.debtAmount);
    $("lock-target-limit").textContent = formatCurrency(dealer.debtLimit);

    const reasonEl = $("lock-reason");
    reasonEl.value = "";
    reasonEl.classList.remove("input-error");
    $("lock-reason-error").hidden = true;
    $("lock-form-msg").hidden = true;

    $("lock-modal").hidden = false;
    reasonEl.focus();
  }

  function closeLockModal() {
    $("lock-modal").hidden = true;
  }

  function handleConfirmLock(e) {
    e.preventDefault();

    const id = Number($("lock-dlr-id").value);
    const dealer = state.dealers.find((d) => d.id === id);
    if (!dealer) return;

    const reasonEl = $("lock-reason");
    const reason = reasonEl.value.trim();

    // ── QUY TẮC NGHIỆP VỤ: BẮT BUỘC NHẬP LÝ DO KHOÁ ──
    if (!reason) {
      reasonEl.classList.add("input-error");
      $("lock-reason-error").hidden = false;
      reasonEl.focus();
      return;
    }

    // Cập nhật trạng thái đại lý thành LOCKED
    dealer.status = "LOCKED";
    dealer.lockReason = reason;
    dealer.lockedAt = new Intl.DateTimeFormat("vi-VN", {
      dateStyle: "short",
      timeStyle: "short",
    }).format(new Date());
    dealer.lockedBy = currentUser.fullName || currentUser.username || "Kế toán công nợ";

    saveDealers();
    closeLockModal();

    updateMetrics();
    renderDealersTable();
    populatePortalDealersSelect();
    renderOrdersTable();

    Toast.show(
      `Đã khoá giao dịch đại lý ${dealer.name}. Đại lý này không thể tạo đơn mới!`,
      "success",
      5000
    );
  }

  /* ── 10. Modal Mở Khoá Giao Dịch ─────────────────────────── */
  function openUnlockModal(dealer) {
    if (!dealer) return;

    $("unlock-dlr-id").value = dealer.id;
    $("unlock-target-name").textContent = dealer.name;
    $("unlock-target-code").textContent = dealer.code;
    $("unlock-previous-reason").textContent = dealer.lockReason || "Không có thông tin lý do";
    $("unlock-previous-meta").textContent = `Khoá bởi: ${dealer.lockedBy || "Kế toán"} lúc ${dealer.lockedAt || "—"}`;
    $("unlock-note").value = "";

    $("unlock-modal").hidden = false;
  }

  function closeUnlockModal() {
    $("unlock-modal").hidden = true;
  }

  function handleConfirmUnlock(e) {
    e.preventDefault();

    const id = Number($("unlock-dlr-id").value);
    const dealer = state.dealers.find((d) => d.id === id);
    if (!dealer) return;

    // Cập nhật trạng thái đại lý thành ACTIVE
    dealer.status = "ACTIVE";
    dealer.unlockedAt = new Intl.DateTimeFormat("vi-VN", {
      dateStyle: "short",
      timeStyle: "short",
    }).format(new Date());
    dealer.unlockedBy = currentUser.fullName || currentUser.username || "Kế toán công nợ";
    dealer.unlockNote = $("unlock-note").value.trim() || null;

    saveDealers();
    closeUnlockModal();

    updateMetrics();
    renderDealersTable();
    populatePortalDealersSelect();
    renderOrdersTable();

    Toast.show(`Đã mở khoá giao dịch cho đại lý ${dealer.name}.`, "success", 4000);
  }

  /* ── 11. Modal Xem Lý Do Khoá Chi Tiết ────────────────────── */
  function openViewReasonModal(dealer) {
    if (!dealer) return;

    $("view-lock-name").textContent = dealer.name;
    $("view-lock-code").textContent = dealer.code;
    $("view-lock-reason-content").textContent = dealer.lockReason || "Không có chi tiết lý do.";
    $("view-lock-time").textContent = dealer.lockedAt || "—";
    $("view-lock-by").textContent = dealer.lockedBy || "—";

    $("btn-view-unlock-now").onclick = () => {
      $("view-lock-modal").hidden = true;
      openUnlockModal(dealer);
    };

    $("view-lock-modal").hidden = false;
  }

  /* ── 12. Panel 2: Cổng Đặt Hàng / Tạo Đơn Mới ──────────────── */
  function initPortalOrderCode() {
    const randomSuffix = Math.floor(100 + Math.random() * 900);
    const dateStr = new Date().toISOString().slice(0, 10).replace(/-/g, "");
    $("portal-order-code").value = `DH-${dateStr}-${randomSuffix}`;
  }

  function populatePortalDealersSelect() {
    const select = $("portal-dealer-select");
    if (!select) return;

    const currentVal = select.value;
    select.innerHTML = '<option value="">— Chọn đại lý đặt hàng —</option>';

    state.dealers.forEach((d) => {
      const isLocked = d.status === "LOCKED";
      const option = document.createElement("option");
      option.value = String(d.id);
      if (isLocked) {
        option.textContent = `[BỊ KHOÁ GIAO DỊCH] ${d.code} - ${d.name}`;
        option.style.color = "#dc2626";
        option.style.fontWeight = "bold";
      } else {
        option.textContent = `${d.code} - ${d.name} (${GROUP_LABELS[d.customerGroup] || d.customerGroup})`;
      }
      select.appendChild(option);
    });

    if (currentVal) {
      select.value = currentVal;
    }
  }

  function onPortalDealerChange() {
    const select = $("portal-dealer-select");
    const dealerId = Number(select?.value);
    const dealer = state.dealers.find((d) => d.id === dealerId);

    const lockBanner = $("portal-lock-banner");
    const submitBtn = $("btn-portal-submit");
    const debtAmountEl = $("portal-debt-amount");
    const debtLimitEl = $("portal-debt-limit");
    const statusTagEl = $("portal-dealer-status-tag");

    if (!dealer) {
      lockBanner.hidden = true;
      submitBtn.disabled = false;
      submitBtn.removeAttribute("title");
      debtAmountEl.textContent = "—";
      debtLimitEl.textContent = "—";
      statusTagEl.textContent = "—";
      return;
    }

    debtAmountEl.textContent = formatCurrency(dealer.debtAmount);
    debtLimitEl.textContent = formatCurrency(dealer.debtLimit);

    // ── QUY TẮC NGHIỆP VỤ: ĐẠI LÝ BỊ KHOÁ KHÔNG TẠO ĐƯỢC ĐƠN MỚI ──
    if (dealer.status === "LOCKED") {
      lockBanner.hidden = false;
      $("portal-locked-reason").textContent = dealer.lockReason || "Không rõ lý do";
      $("portal-locked-meta").textContent = `Khoá bởi: ${dealer.lockedBy || "Kế toán"} lúc ${dealer.lockedAt || "—"}`;

      statusTagEl.innerHTML = '<span class="badge badge--danger">Bị khoá giao dịch</span>';

      // Vô hiệu hoá nút gửi đơn hoàn toàn
      submitBtn.disabled = true;
      submitBtn.setAttribute(
        "title",
        "Đại lý đang bị khoá giao dịch! Không thể tạo đơn hàng mới, kể cả trên cổng đặt hàng."
      );

      Toast.show(
        `Đại lý ${dealer.code} đang bị KHOÁ GIAO DỊCH do: ${dealer.lockReason}. Chặn tạo đơn mới!`,
        "error",
        6000
      );
    } else {
      lockBanner.hidden = true;
      submitBtn.disabled = false;
      submitBtn.removeAttribute("title");
      statusTagEl.innerHTML = '<span class="badge badge--active">Đang giao dịch</span>';
    }
  }

  function addPortalItemRow(product = null, qty = 10) {
    const tbody = $("portal-items-body");
    if (!tbody) return;

    const row = document.createElement("tr");
    const defaultProduct = product || state.products[0];

    row.innerHTML = `
      <td>
        <select class="portal-item-select dlr-select">
          ${state.products
            .map(
              (p) =>
                `<option value="${p.id}" ${p.id === defaultProduct.id ? "selected" : ""}>
                  ${escHtml(p.code)} - ${escHtml(p.name)}
                </option>`
            )
            .join("")}
        </select>
      </td>
      <td class="portal-item-unit" style="color:var(--ink-600)">${escHtml(defaultProduct.unit)}</td>
      <td class="portal-item-price" style="font-weight:600">${formatNumber(defaultProduct.price)}</td>
      <td>
        <input type="number" min="1" max="9999" value="${qty}" class="portal-item-qty" />
      </td>
      <td class="portal-item-total" style="font-weight:700; color:var(--blue)">
        ${formatCurrency(defaultProduct.price * qty)}
      </td>
      <td>
        <button type="button" class="btn-remove-item" style="color:#dc2626; padding:4px;" title="Xoá dòng">
          <svg viewBox="0 0 24 24" width="16" height="16"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </td>`;

    const selectEl = row.querySelector(".portal-item-select");
    const qtyEl = row.querySelector(".portal-item-qty");
    const priceEl = row.querySelector(".portal-item-price");
    const unitEl = row.querySelector(".portal-item-unit");
    const totalEl = row.querySelector(".portal-item-total");
    const removeBtn = row.querySelector(".btn-remove-item");

    const updateRow = () => {
      const selectedProd = state.products.find((p) => p.id === Number(selectEl.value));
      const q = Math.max(1, Number(qtyEl.value) || 1);
      if (selectedProd) {
        unitEl.textContent = selectedProd.unit;
        priceEl.textContent = formatNumber(selectedProd.price);
        totalEl.textContent = formatCurrency(selectedProd.price * q);
      }
      recalculatePortalTotal();
    };

    selectEl.addEventListener("change", updateRow);
    qtyEl.addEventListener("input", updateRow);
    removeBtn.addEventListener("click", () => {
      row.remove();
      recalculatePortalTotal();
    });

    tbody.appendChild(row);
    recalculatePortalTotal();
  }

  function recalculatePortalTotal() {
    let grandTotal = 0;
    document.querySelectorAll("#portal-items-body tr").forEach((tr) => {
      const selectEl = tr.querySelector(".portal-item-select");
      const qtyEl = tr.querySelector(".portal-item-qty");
      const prod = state.products.find((p) => p.id === Number(selectEl?.value));
      const q = Number(qtyEl?.value) || 0;
      if (prod) grandTotal += prod.price * q;
    });

    if ($("portal-grand-total")) {
      $("portal-grand-total").textContent = formatCurrency(grandTotal);
    }
  }

  function handlePortalOrderSubmit(e) {
    e.preventDefault();

    const dealerId = Number($("portal-dealer-select")?.value);
    const dealer = state.dealers.find((d) => d.id === dealerId);

    if (!dealer) {
      Toast.show("Vui lòng chọn đại lý đặt hàng!", "error");
      $("portal-dealer-select")?.focus();
      return;
    }

    // ── QUY TẮC NGHIỆP VỤ BẮT BUỘC (S3-07): CHẶN TẠO ĐƠN MỚI KHI ĐẠI LÝ BỊ KHOÁ ──
    if (dealer.status === "LOCKED") {
      Toast.show(
        `⛔ CHẶN TẠO ĐƠN: Đại lý ${dealer.name} đang bị KHOÁ GIAO DỊCH (Lý do: ${dealer.lockReason}). Không thể tạo đơn mới!`,
        "error",
        6000
      );
      $("portal-lock-banner").scrollIntoView({ behavior: "smooth" });
      return;
    }

    // Thu thập các mặt hàng
    const items = [];
    let grandTotal = 0;
    document.querySelectorAll("#portal-items-body tr").forEach((tr) => {
      const selectEl = tr.querySelector(".portal-item-select");
      const qtyEl = tr.querySelector(".portal-item-qty");
      const prod = state.products.find((p) => p.id === Number(selectEl?.value));
      const q = Math.max(1, Number(qtyEl?.value) || 1);
      if (prod) {
        const lineTotal = prod.price * q;
        grandTotal += lineTotal;
        items.push({
          productName: prod.name,
          unit: prod.unit,
          quantity: q,
          price: prod.price,
          total: lineTotal,
        });
      }
    });

    if (!items.length) {
      Toast.show("Đơn hàng phải có ít nhất 1 sản phẩm!", "error");
      return;
    }

    const orderCode = $("portal-order-code").value.trim() || `DH-${Date.now()}`;
    const newOrder = {
      id: Date.now(),
      orderCode,
      dealerId: dealer.id,
      dealerName: dealer.name,
      createdAt: new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date()),
      status: "RECORDED", // Đã ghi sổ
      totalAmount: grandTotal,
      note: $("portal-order-note")?.value.trim() || null,
      items,
    };

    state.orders.unshift(newOrder);
    saveOrders();

    Toast.show(`Tạo đơn hàng ${orderCode} thành công cho đại lý ${dealer.name}!`, "success");

    // Reset portal form
    initPortalOrderCode();
    $("portal-items-body").innerHTML = "";
    addPortalItemRow(state.products[0], 20);
    $("portal-order-note").value = "";

    // Chuyển sang xem danh sách đơn hàng
    updateMetrics();
    switchTab("orders");
  }

  /* ── 13. Panel 3: Đơn Hàng & Xử Lý Đơn Dở (S3-07) ──────────── */
  const ORDER_STATUS_LABELS = {
    PENDING_APPROVAL: { text: "Chờ duyệt (Đang dở)", class: "badge--amber" },
    RECORDED: { text: "Đã ghi sổ (Đang dở)", class: "badge--active" },
    PROCESSING: { text: "Đang giao (Đang dở)", class: "badge--active" },
    COMPLETED: { text: "Đã hoàn tất", class: "badge--muted" },
    CANCELLED: { text: "Đã huỷ", class: "badge--danger" },
  };

  function renderOrdersTable() {
    const tbody = $("orders-tbody");
    if (!tbody) return;

    const statusFilter = state.ordersFilterStatus;
    const lockedFilter = state.ordersFilterLocked;

    const filtered = state.orders.filter((o) => {
      const dealer = state.dealers.find((d) => d.id === o.dealerId);
      const isDealerLocked = dealer && dealer.status === "LOCKED";

      const matchStatus = !statusFilter || o.status === statusFilter;
      let matchLocked = true;
      if (lockedFilter === "LOCKED_ONLY") matchLocked = isDealerLocked;
      if (lockedFilter === "ACTIVE_ONLY") matchLocked = !isDealerLocked;

      return matchStatus && matchLocked;
    });

    if (!filtered.length) {
      tbody.innerHTML = `
        <tr>
          <td colspan="8" style="text-align:center; padding: 40px 20px;">
            <div style="color: var(--ink-500); font-size: 0.9375rem;">
              Không có đơn hàng nào phù hợp với bộ lọc.
            </div>
          </td>
        </tr>`;
      return;
    }

    tbody.innerHTML = filtered
      .map((o) => {
        const dealer = state.dealers.find((d) => d.id === o.dealerId);
        const isDealerLocked = dealer && dealer.status === "LOCKED";
        const isUnfinished = ["PENDING_APPROVAL", "RECORDED", "PROCESSING"].includes(o.status);

        // Status badge
        const st = ORDER_STATUS_LABELS[o.status] || { text: o.status, class: "badge--muted" };
        const statusBadge = `<span class="badge ${st.class}">${st.text}</span>`;

        // Dealer lock status & Debt warning badge (Bullet 3)
        let dealerStatusBadge = "";
        let debtWarningBadge = "";

        if (isDealerLocked) {
          dealerStatusBadge = `<span class="badge badge--danger"><svg viewBox="0 0 24 24" width="12" height="12"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg> Bị khoá</span>`;
          debtWarningBadge = `
            <span class="order-warn-tag" title="Lý do khoá: ${escHtml(dealer.lockReason || '')}">
              <svg viewBox="0 0 24 24" width="14" height="14"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
              <span>Đại lý bị khoá (Lưu ý công nợ)</span>
            </span>`;
        } else {
          dealerStatusBadge = `<span class="badge badge--active">Đang giao dịch</span>`;
          debtWarningBadge = `<span style="color:var(--ink-400); font-size:0.75rem;">— Không có cảnh báo</span>`;
        }

        // ── QUY TẮC NGHIỆP VỤ (S3-07): ĐƠN ĐANG DỞ VẪN XỬ LÝ TIẾP ĐƯỢC ──
        let actions = '<div class="dlr-row-actions">';

        if (isUnfinished) {
          if (o.status === "PENDING_APPROVAL") {
            actions += `
              <button type="button" class="dlr-action-btn dlr-action-btn--primary" data-order-action="approve" data-id="${o.id}" title="Duyệt đơn hàng dở này">
                <span>Duyệt đơn</span>
              </button>`;
          } else if (o.status === "RECORDED") {
            actions += `
              <button type="button" class="dlr-action-btn dlr-action-btn--primary" data-order-action="dispatch" data-id="${o.id}" title="Xuất kho / Giao hàng">
                <span>Xuất kho / Giao</span>
              </button>`;
          } else if (o.status === "PROCESSING") {
            actions += `
              <button type="button" class="dlr-action-btn dlr-action-btn--unlock" data-order-action="complete" data-id="${o.id}" title="Hoàn tất đơn hàng">
                <span>Hoàn tất</span>
              </button>`;
          }

          actions += `
            <button type="button" class="dlr-action-btn dlr-action-btn--lock" data-order-action="cancel" data-id="${o.id}" title="Huỷ đơn">
              <span>Huỷ đơn</span>
            </button>`;
        }

        actions += `
          <button type="button" class="dlr-action-btn" data-order-action="detail" data-id="${o.id}" title="Xem chi tiết đơn">
            <span>Chi tiết</span>
          </button>
        </div>`;

        return `
          <tr class="${isDealerLocked && isUnfinished ? 'order-row--locked-dealer' : ''}">
            <td><span class="order-code-badge">${escHtml(o.orderCode)}</span></td>
            <td><small style="color:var(--ink-600)">${escHtml(o.createdAt)}</small></td>
            <td><strong>${escHtml(o.dealerName || (dealer ? dealer.name : '—'))}</strong></td>
            <td>${dealerStatusBadge}</td>
            <td style="font-weight:700; color:var(--navy-900)">${formatCurrency(o.totalAmount)}</td>
            <td>${statusBadge}</td>
            <td>${debtWarningBadge}</td>
            <td>${actions}</td>
          </tr>`;
      })
      .join("");

    bindOrdersTableEvents();
  }

  function bindOrdersTableEvents() {
    const tbody = $("orders-tbody");
    if (!tbody) return;

    tbody.querySelectorAll("[data-order-action]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const orderId = Number(btn.dataset.id);
        const action = btn.dataset.orderAction;
        const order = state.orders.find((o) => o.id === orderId);
        if (order) openOrderActionModal(order, action);
      });
    });
  }

  /* ── 14. Modal Xử Lý Đơn Hàng Đang Dở (Có cảnh báo) ───────── */
  function openOrderActionModal(order, action) {
    if (!order) return;

    const dealer = state.dealers.find((d) => d.id === order.dealerId);
    const isDealerLocked = dealer && dealer.status === "LOCKED";

    $("order-action-order-id").value = order.id;
    $("order-action-type").value = action;

    $("order-action-code").textContent = order.orderCode;
    $("order-action-date").textContent = order.createdAt;
    $("order-action-dealer").textContent = order.dealerName || (dealer ? dealer.name : "—");
    $("order-action-total").textContent = formatCurrency(order.totalAmount);

    const st = ORDER_STATUS_LABELS[order.status] || { text: order.status };
    $("order-action-status-badge").textContent = st.text;

    // ── CẢNH BÁO ĐẶC BIỆT KHI ĐẠI LÝ BỊ KHOÁ (Bullet 3) ──
    const warningBox = $("order-action-locked-warning");
    if (isDealerLocked) {
      warningBox.hidden = false;
      $("order-action-dealer-name").textContent = dealer.name;
      $("order-action-dealer-reason").textContent = dealer.lockReason || "Không rõ lý do";
    } else {
      warningBox.hidden = true;
    }

    // Tiêu đề & câu hỏi xác nhận theo từng hành động
    const confirmPrompt = $("order-action-confirm-text");
    const confirmBtn = $("btn-confirm-order-action");

    if (action === "approve") {
      $("order-action-title").textContent = "Duyệt Đơn Hàng Đang Dở";
      confirmPrompt.innerHTML = isDealerLocked
        ? `⚠️ <strong>Xác nhận duyệt đơn hàng dở?</strong> Đại lý này hiện đang bị khoá giao dịch do nợ xấu. Bạn có đồng ý tiếp tục xử lý?`
        : `Xác nhận duyệt đơn hàng ${order.orderCode}?`;
      confirmBtn.innerHTML = `<span>Duyệt đơn hàng</span>`;
      confirmBtn.className = "btn btn--primary";
      confirmBtn.hidden = false;
    } else if (action === "dispatch") {
      $("order-action-title").textContent = "Xuất Kho & Giao Hàng Cho Đơn Dở";
      confirmPrompt.innerHTML = isDealerLocked
        ? `⚠️ <strong>Lưu ý: Đại lý đang bị khoá giao dịch!</strong> Đơn đang dở vẫn được phép giao hàng theo chỉ đạo. Vui lòng đối soát công nợ trước khi xuất.`
        : `Xác nhận xuất kho và chuyển giao hàng cho đơn ${order.orderCode}?`;
      confirmBtn.innerHTML = `<span>Xác nhận xuất kho / Giao hàng</span>`;
      confirmBtn.className = "btn btn--primary";
      confirmBtn.hidden = false;
    } else if (action === "complete") {
      $("order-action-title").textContent = "Hoàn Tất Đơn Hàng";
      confirmPrompt.innerHTML = `Xác nhận đơn hàng ${order.orderCode} đã giao và thanh toán đầy đủ?`;
      confirmBtn.innerHTML = `<span>Hoàn tất đơn</span>`;
      confirmBtn.className = "btn btn--success";
      confirmBtn.hidden = false;
    } else if (action === "cancel") {
      $("order-action-title").textContent = "Huỷ Đơn Hàng";
      confirmPrompt.innerHTML = `Xác nhận huỷ đơn hàng ${order.orderCode}? Thao tác này không thể hoàn tác.`;
      confirmBtn.innerHTML = `<span>Xác nhận huỷ đơn</span>`;
      confirmBtn.className = "btn btn--danger";
      confirmBtn.hidden = false;
    } else {
      // Chi tiết đơn
      $("order-action-title").textContent = "Chi Tiết Đơn Hàng";
      confirmPrompt.textContent = "";
      confirmBtn.hidden = true;
    }

    // Render items list
    const itemsTbody = $("order-action-items-body");
    itemsTbody.innerHTML = (order.items || [])
      .map(
        (it) => `
        <tr>
          <td><strong>${escHtml(it.productName)}</strong></td>
          <td>${escHtml(it.unit || 'Thùng')}</td>
          <td style="text-align:right">${formatNumber(it.quantity)}</td>
          <td style="text-align:right">${formatNumber(it.price)}</td>
          <td style="text-align:right; font-weight:600">${formatCurrency(it.total)}</td>
        </tr>`
      )
      .join("");

    $("order-action-modal").hidden = false;
  }

  function handleConfirmOrderAction() {
    const orderId = Number($("order-action-order-id").value);
    const action = $("order-action-type").value;
    const order = state.orders.find((o) => o.id === orderId);

    if (!order) return;

    if (action === "approve") {
      order.status = "RECORDED";
      Toast.show(`Đã phê duyệt đơn hàng ${order.orderCode} thành công.`, "success");
    } else if (action === "dispatch") {
      order.status = "PROCESSING";
      Toast.show(`Đã xuất kho đơn hàng ${order.orderCode}. Đang chuyển giao hàng!`, "success");
    } else if (action === "complete") {
      order.status = "COMPLETED";
      Toast.show(`Đã hoàn tất đơn hàng ${order.orderCode}.`, "success");
    } else if (action === "cancel") {
      order.status = "CANCELLED";
      Toast.show(`Đã huỷ đơn hàng ${order.orderCode}.`, "warning");
    }

    saveOrders();
    $("order-action-modal").hidden = true;
    updateMetrics();
    renderOrdersTable();
  }

  /* ── 15. Modal Thêm / Sửa Đại Lý ─────────────────────────── */
  function openAddDealerModal() {
    $("dealer-modal-title").textContent = "Thêm Đại Lý Mới";
    $("dlr-id").value = "";
    $("dealer-form").reset();
    $("dealer-form-msg").hidden = true;
    $("dealer-modal").hidden = false;
  }

  function handleSaveDealer(e) {
    e.preventDefault();

    const code = $("dlr-code").value.trim().toUpperCase();
    const name = $("dlr-name").value.trim();
    const group = $("dlr-group").value;
    const debtLimit = Number($("dlr-debt-limit").value) || 0;

    if (!code || !name || !group) {
      Toast.show("Vui lòng điền đầy đủ Mã, Tên và Nhóm khách hàng!", "error");
      return;
    }

    const id = $("dlr-id").value ? Number($("dlr-id").value) : Date.now();
    const existingIdx = state.dealers.findIndex((d) => d.id === id);

    const dealerData = {
      id,
      code,
      name,
      taxCode: $("dlr-tax").value.trim(),
      customerGroup: group,
      debtLimit,
      debtAmount: existingIdx >= 0 ? state.dealers[existingIdx].debtAmount : 0,
      region: $("dlr-region").value.trim(),
      assignedUser: $("dlr-owner").value.trim() || currentUser.fullName || "Nhân viên KD",
      phone: $("dlr-phone").value.trim(),
      address: $("dlr-address").value.trim(),
      status: existingIdx >= 0 ? state.dealers[existingIdx].status : "ACTIVE",
      lockReason: existingIdx >= 0 ? state.dealers[existingIdx].lockReason : null,
      lockedAt: existingIdx >= 0 ? state.dealers[existingIdx].lockedAt : null,
      lockedBy: existingIdx >= 0 ? state.dealers[existingIdx].lockedBy : null,
    };

    if (existingIdx >= 0) {
      state.dealers[existingIdx] = dealerData;
    } else {
      state.dealers.unshift(dealerData);
    }

    saveDealers();
    $("dealer-modal").hidden = true;
    updateMetrics();
    renderDealersTable();
    populatePortalDealersSelect();

    Toast.show(`Đã lưu đại lý ${name} thành công!`, "success");
  }

  /* ── 16. Event Listeners Initialization ───────────────────── */
  function initEventListeners() {
    // Search & filters in Dealers tab
    $("dlr-search")?.addEventListener("input", (e) => {
      state.searchQuery = e.target.value;
      renderDealersTable();
    });

    $("dlr-filter-group")?.addEventListener("change", (e) => {
      state.filterGroup = e.target.value;
      renderDealersTable();
    });

    $("dlr-filter-status")?.addEventListener("change", (e) => {
      state.filterStatus = e.target.value;
      renderDealersTable();
    });

    // Orders filters
    $("orders-filter-status")?.addEventListener("change", (e) => {
      state.ordersFilterStatus = e.target.value;
      renderOrdersTable();
    });

    $("orders-filter-locked")?.addEventListener("change", (e) => {
      state.ordersFilterLocked = e.target.value;
      renderOrdersTable();
    });

    // Lock Modal
    $("lock-form")?.addEventListener("submit", handleConfirmLock);
    document.querySelectorAll("[data-close-lock]").forEach((el) => {
      el.addEventListener("click", closeLockModal);
    });

    // Unlock Modal
    $("unlock-form")?.addEventListener("submit", handleConfirmUnlock);
    document.querySelectorAll("[data-close-unlock]").forEach((el) => {
      el.addEventListener("click", closeUnlockModal);
    });

    // View Lock details Modal
    document.querySelectorAll("[data-close-view-lock]").forEach((el) => {
      el.addEventListener("click", () => {
        $("view-lock-modal").hidden = true;
      });
    });

    // Order Action Modal
    $("btn-confirm-order-action")?.addEventListener("click", handleConfirmOrderAction);
    document.querySelectorAll("[data-close-order-action]").forEach((el) => {
      el.addEventListener("click", () => {
        $("order-action-modal").hidden = true;
      });
    });

    // Dealer modal (Add / Edit)
    $("btn-add-dealer")?.addEventListener("click", openAddDealerModal);
    $("dealer-form")?.addEventListener("submit", handleSaveDealer);
    document.querySelectorAll("[data-close-dealer]").forEach((el) => {
      el.addEventListener("click", () => {
        $("dealer-modal").hidden = true;
      });
    });

    // Portal Form events
    $("portal-dealer-select")?.addEventListener("change", onPortalDealerChange);
    $("btn-portal-add-item")?.addEventListener("click", () => addPortalItemRow());
    $("portal-order-form")?.addEventListener("submit", handlePortalOrderSubmit);
    $("btn-portal-reset")?.addEventListener("click", () => {
      $("portal-dealer-select").value = "";
      onPortalDealerChange();
      initPortalOrderCode();
      $("portal-items-body").innerHTML = "";
      addPortalItemRow(state.products[0], 10);
    });
  }

  /* ── 17. Sync with Backend API if Available ───────────────── */
  async function syncBackendData() {
    try {
      // Sync debt limits from backend if endpoint is live
      const res = await Auth.apiFetch("/finance/debt");
      if (res.ok) {
        const json = await res.json();
        if (json.success && Array.isArray(json.data) && json.data.length > 0) {
          json.data.forEach((backendItem) => {
            const match = state.dealers.find((d) => d.id === backendItem.dealerId);
            if (match) {
              match.debtLimit = Number(backendItem.debtLimit) || match.debtLimit;
              match.debtAmount = Number(backendItem.debtAmount) || match.debtAmount;
            }
          });
          saveDealers();
          renderDealersTable();
          updateMetrics();
        }
      }
    } catch {
      // Offline or mock mode, localStorage is primary
    }
  }

  /* ── 18. Initialize Page ──────────────────────────────────── */
  loadState();
  initLayout();
  initTabs();
  initEventListeners();
  updateMetrics();
  renderDealersTable();

  // Pre-populate portal initial row
  addPortalItemRow(state.products[0], 25);
  addPortalItemRow(state.products[2], 15);

  syncBackendData();
})();
