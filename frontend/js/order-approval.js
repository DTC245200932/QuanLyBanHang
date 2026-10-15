/**
 * order-approval.js — S4-05 Duyệt đơn vượt hạn mức / dưới giá sàn.
 *
 * Vai trò: Quản lý kinh doanh (ROLE_SALES_MANAGER, ROLE_ADMIN)
 * Quyền: ORDER_APPROVE (kết nối ma trận phân quyền)
 *
 * Tiêu chí chấp nhận:
 * 1. Danh sách đơn chờ duyệt hiển thị rõ lý do cần duyệt và mức vi phạm.
 * 2. Ba hành động: Duyệt, Từ chối, Trả lại sửa — hai hành động sau bắt buộc nhập ý kiến.
 * 3. Đơn được duyệt mới chuyển sang giữ chỗ tồn và chờ xuất kho.
 * 4. Lịch sử duyệt không sửa và không xoá được.
 */
(() => {
  if (typeof Auth !== "undefined" && !Auth.requireLogin()) return;

  const user = typeof Auth !== "undefined" ? Auth.getUser() : null;

  // ── Role & Permission guard (Ma trận phân quyền) ──
  const isManagerOrAdmin = typeof Auth !== "undefined" && Auth.hasAnyRole("ROLE_ADMIN", "ROLE_SALES_MANAGER");
  const hasApprovePerm = typeof Auth !== "undefined" && Auth.hasPermission("ORDER_APPROVE");

  if (!isManagerOrAdmin || !hasApprovePerm) {
    if (typeof sessionStorage !== "undefined") {
      sessionStorage.setItem("errorMessage", "Bạn không có quyền truy cập chức năng duyệt đơn hàng.");
    }
    if (typeof window !== "undefined") {
      window.location.href = "/error?status=403";
    }
    return;
  }

  // ── DOM boilerplate ──
  const sidebar = document.getElementById("sidebar");
  const overlay = document.getElementById("sidebar-overlay");

  function openSidebar(open) {
    if (sidebar) sidebar.classList.toggle("sidebar--open", open);
    if (overlay) overlay.classList.toggle("sidebar-overlay--visible", open);
  }

  document.getElementById("sidebar-toggle")?.addEventListener("click", () => openSidebar(true));
  document.getElementById("sidebar-close")?.addEventListener("click", () => openSidebar(false));
  overlay?.addEventListener("click", () => openSidebar(false));

  if (user) {
    const userNameEl = document.getElementById("user-name");
    if (userNameEl) userNameEl.textContent = user.fullName || user.username || "—";
    const userAvatarEl = document.getElementById("user-avatar");
    if (userAvatarEl) {
      userAvatarEl.textContent = (user.fullName || user.username || "U").trim().charAt(0).toUpperCase();
    }
    const userRoleEl = document.getElementById("user-role");
    if (userRoleEl && typeof MenuConfig !== "undefined") {
      const roleNames = (user.roles || []).map(MenuConfig.getRoleDisplayName);
      userRoleEl.textContent = roleNames.join(", ");
    }
  }

  document.querySelectorAll("#btn-logout, #btn-logout-topbar").forEach((btn) =>
    btn.addEventListener("click", () => typeof Auth !== "undefined" && Auth.logout()),
  );

  // ── Formatting Helpers ──
  const escapeHtml = (value) =>
    String(value ?? "").replace(/[&<>'"]/g, (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[c],
    );

  function formatCurrency(amount) {
    if (amount == null || isNaN(Number(amount))) return "—";
    return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount);
  }

  function formatMoney(amount) {
    if (amount == null || isNaN(Number(amount))) return "0 ₫";
    return new Intl.NumberFormat("vi-VN").format(amount) + " ₫";
  }

  function formatDate(dateStr) {
    if (!dateStr) return "—";
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return String(dateStr);
    return new Intl.DateTimeFormat("vi-VN", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    }).format(d);
  }

  function formatDateTime(dateStr) {
    if (!dateStr) return "—";
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return String(dateStr);
    return new Intl.DateTimeFormat("vi-VN", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }).format(d);
  }

  function showToast(message, type = "error") {
    let container = document.getElementById("toast-container");
    if (!container) {
      container = document.createElement("div");
      container.id = "toast-container";
      document.body.appendChild(container);
    }
    const toast = document.createElement("div");
    toast.className = `toast toast--${type}`;
    toast.innerHTML = `<span class="toast__message">${escapeHtml(message)}</span>`;
    container.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add("toast--visible"));
    setTimeout(() => {
      toast.classList.remove("toast--visible");
      toast.addEventListener("transitionend", () => toast.remove());
    }, 4500);
  }

  // ── Reason & Status Mapping ──
  const REASON_CONFIG = {
    OVER_DEBT_LIMIT: {
      label: "Vượt hạn mức công nợ",
      tagClass: "oa-reason-tag--debt",
    },
    BELOW_FLOOR_PRICE: {
      label: "Bán dưới giá sàn",
      tagClass: "oa-reason-tag--price",
    },
    OTHER: {
      label: "Cần phê duyệt",
      tagClass: "oa-reason-tag--debt",
    },
  };

  const STATUS_CONFIG = {
    PENDING_APPROVAL: { label: "Chờ duyệt", cssClass: "oa-status-badge--pending" },
    RECORDED: { label: "Đã duyệt (Ghi sổ)", cssClass: "oa-status-badge--approved" },
    APPROVED: { label: "Đã duyệt", cssClass: "oa-status-badge--approved" },
    REJECTED: { label: "Từ chối", cssClass: "oa-status-badge--rejected" },
    RETURNED: { label: "Trả lại sửa", cssClass: "oa-status-badge--returned" },
    CANCELLED: { label: "Đã huỷ", cssClass: "oa-status-badge--rejected" },
  };

  // ── Intelligent Parsing of Reasons & Violations (S4-05) ──
  function parseOrderApprovalInfo(order) {
    const reasons = [];
    const violations = [];
    const rawReason = String(order.approvalReason || "").trim();

    // 1. Kiểm tra Vượt hạn mức công nợ
    const hasDebtFlag = Boolean(order.exceedsDebtLimit);
    const hasDebtText = rawReason.toLowerCase().includes("hạn mức công nợ");

    if (hasDebtFlag || hasDebtText) {
      reasons.push(REASON_CONFIG.OVER_DEBT_LIMIT);

      let diffVal = null;
      let limitVal = order.debtLimit != null ? Number(order.debtLimit) : null;
      let projectedVal = null;

      if (order.currentDebt != null && limitVal != null && limitVal > 0) {
        const curDebt = Number(order.currentDebt || 0);
        const total = Number(order.totalAmount || 0);
        projectedVal = curDebt + total;
        diffVal = projectedVal - limitVal;
      }

      // Nếu không có số từ DTO, parse từ chuỗi approvalReason
      // Định dạng: "Vượt hạn mức công nợ: Công nợ hiện tại (15.000.000 ₫) + Đơn mới (6.000.000 ₫) = 21.000.000 ₫ vượt hạn mức (20.000.000 ₫)"
      let displayValue = "";
      let desc = "";

      if (diffVal != null && diffVal > 0) {
        displayValue = `+${formatCurrency(diffVal)}`;
        desc = `Dự kiến: ${formatCurrency(projectedVal)} · Hạn mức: ${formatCurrency(limitVal)}`;
      } else if (rawReason) {
        const match = rawReason.match(/=\s*([^=]+?)\s*vượt hạn mức\s*\(([^)]+)\)/i);
        if (match) {
          displayValue = `Vượt hạn mức (${match[2].trim()})`;
          desc = `Nợ dự kiến: ${match[1].trim()} · Hạn mức: ${match[2].trim()}`;
        } else {
          displayValue = "Vượt hạn mức công nợ";
          desc = "Tổng nợ vượt hạn mức tín dụng được cấp";
        }
      } else {
        displayValue = "Vượt hạn mức công nợ";
        desc = "Đơn làm công nợ vượt hạn mức";
      }

      violations.push({
        type: "debt",
        title: "Vượt hạn mức công nợ đại lý",
        displayValue,
        description: desc,
        amount: diffVal,
      });
    }

    // 2. Kiểm tra Bán dưới giá sàn
    const items = Array.isArray(order.items) ? order.items : [];
    const belowFloorItems = items.filter(
      (it) => it.belowFloorPrice || (it.floorPrice != null && Number(it.price) < Number(it.floorPrice)),
    );
    const hasPriceText = rawReason.toLowerCase().includes("giá sàn");

    if (belowFloorItems.length > 0 || hasPriceText) {
      reasons.push(REASON_CONFIG.BELOW_FLOOR_PRICE);

      if (belowFloorItems.length > 0) {
        belowFloorItems.forEach((it) => {
          const price = Number(it.price || 0);
          const floor = Number(it.floorPrice || 0);
          const diff = floor - price;
          const unitStr = it.unit ? `/${it.unit}` : "";
          violations.push({
            type: "price",
            title: "Bán dưới giá sàn quy định",
            productName: it.productName || it.productCode || "Sản phẩm",
            displayValue: `-${formatCurrency(diff)}${unitStr}`,
            description: `${it.productName || it.productCode}: ${formatCurrency(price)} < Sàn ${formatCurrency(floor)}${unitStr}`,
            price,
            floorPrice: floor,
            diff,
            unit: it.unit,
          });
        });
      } else if (rawReason) {
        // Regex parse: Sản phẩm 'Nước ngọt Sprite' bán giá 180,000 < giá sàn 200,000 (thùng)
        const regex = /Sản phẩm '([^']+)' bán giá ([0-9,.]+)\s*<\s*giá sàn ([0-9,.]+)(?:\s*\(([^)]+)\))?/gi;
        let match;
        let foundAny = false;
        while ((match = regex.exec(rawReason)) !== null) {
          foundAny = true;
          const pName = match[1];
          const pPrice = match[2];
          const pFloor = match[3];
          const pUnit = match[4] ? `/${match[4]}` : "";
          violations.push({
            type: "price",
            title: "Bán dưới giá sàn quy định",
            productName: pName,
            displayValue: `< Sàn ${pFloor} ₫`,
            description: `${pName}: ${pPrice} ₫ < Sàn ${pFloor} ₫${pUnit}`,
          });
        }
        if (!foundAny) {
          violations.push({
            type: "price",
            title: "Bán dưới giá sàn quy định",
            displayValue: "Dưới giá sàn",
            description: rawReason,
          });
        }
      }
    }

    // 3. Fallback nếu đơn có cờ requiresApproval mà chưa nhận diện được lý do cụ thể
    if (reasons.length === 0 && (order.requiresApproval || rawReason)) {
      reasons.push(REASON_CONFIG.OTHER);
      violations.push({
        type: "other",
        title: "Lý do cần duyệt",
        displayValue: rawReason || "Cần quản lý duyệt",
        description: rawReason || "Theo điều kiện quy chế bán hàng",
      });
    }

    return { reasons, violations };
  }

  // ── State ──
  let allOrders = [];
  let filteredOrders = [];
  let currentPage = 0;
  const pageSize = 15;
  let searchTimeout = null;
  let activeDetailOrder = null;

  // ── DOM Elements ──
  const tbody = document.getElementById("oa-tbody");
  const paginationEl = document.getElementById("oa-pagination");
  const countText = document.getElementById("oa-count-text");
  const searchInput = document.getElementById("oa-search");
  const searchClear = document.getElementById("oa-search-clear");
  const filterReason = document.getElementById("oa-filter-reason");
  const filterStatus = document.getElementById("oa-filter-status");
  const btnResetFilters = document.getElementById("btn-reset-oa-filters");

  // ── Load & Filter Data ──
  async function loadOrders() {
    tbody.innerHTML = `<tr class="oa-loading-row"><td colspan="8"><div class="oa-loading">Đang tải dữ liệu đơn hàng…</div></td></tr>`;
    if (paginationEl) paginationEl.hidden = true;

    try {
      const response = await Auth.apiFetch("/orders");
      if (!response.ok) throw new Error("Không thể tải danh sách đơn hàng.");

      const result = await response.json();
      const rawList = Array.isArray(result)
        ? result
        : Array.isArray(result.data)
          ? result.data
          : Array.isArray(result.content)
            ? result.content
            : [];

      allOrders = rawList;
      applyFiltersAndRender();
    } catch (error) {
      tbody.innerHTML = `<tr><td colspan="8"><div class="oa-empty">
        <svg viewBox="0 0 24 24" width="36" height="36" stroke="var(--danger)" fill="none" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        <span>${escapeHtml(error.message)}</span>
      </div></td></tr>`;
      if (countText) countText.textContent = "Lỗi khi tải dữ liệu";
    }
  }

  function applyFiltersAndRender() {
    const selectedStatus = filterStatus ? filterStatus.value : "PENDING_APPROVAL";
    const selectedReason = filterReason ? filterReason.value : "";
    const searchVal = searchInput ? searchInput.value.trim().toLowerCase() : "";

    filteredOrders = allOrders.filter((order) => {
      // 1. Lọc theo trạng thái
      const orderStatus = (order.status || "").toUpperCase();
      if (selectedStatus === "PENDING_APPROVAL") {
        if (orderStatus !== "PENDING_APPROVAL") return false;
      } else if (selectedStatus) {
        if (orderStatus !== selectedStatus.toUpperCase()) return false;
      }

      // 2. Lọc theo lý do cần duyệt
      const { reasons } = parseOrderApprovalInfo(order);
      if (selectedReason) {
        if (selectedReason === "OVER_DEBT_LIMIT") {
          const hasDebt = reasons.some((r) => r === REASON_CONFIG.OVER_DEBT_LIMIT);
          if (!hasDebt) return false;
        } else if (selectedReason === "BELOW_FLOOR_PRICE") {
          const hasPrice = reasons.some((r) => r === REASON_CONFIG.BELOW_FLOOR_PRICE);
          if (!hasPrice) return false;
        } else if (selectedReason === "OVER_QUANTITY_LIMIT") {
          const raw = String(order.approvalReason || "").toLowerCase();
          if (!raw.includes("số lượng")) return false;
        }
      }

      // 3. Tìm kiếm theo mã đơn, đại lý / khách hàng
      if (searchVal) {
        const code = String(order.orderCode || order.id || "").toLowerCase();
        const customer = String(order.customerName || order.dealerName || "").toLowerCase();
        const note = String(order.note || "").toLowerCase();
        if (!code.includes(searchVal) && !customer.includes(searchVal) && !note.includes(searchVal)) {
          return false;
        }
      }

      return true;
    });

    // Sắp xếp: đơn Chờ duyệt lên trước, sau đó theo ngày tạo mới nhất
    filteredOrders.sort((a, b) => {
      const aPending = a.status === "PENDING_APPROVAL" ? 1 : 0;
      const bPending = b.status === "PENDING_APPROVAL" ? 1 : 0;
      if (aPending !== bPending) return bPending - aPending;
      const tA = new Date(a.createdAt || 0).getTime();
      const tB = new Date(b.createdAt || 0).getTime();
      return tB - tA;
    });

    const totalPages = Math.ceil(filteredOrders.length / pageSize) || 1;
    if (currentPage >= totalPages) currentPage = Math.max(0, totalPages - 1);

    const startIdx = currentPage * pageSize;
    const pageItems = filteredOrders.slice(startIdx, startIdx + pageSize);

    renderTable(pageItems);
    renderPagination(totalPages);
    updateCountText();
    updateResetButtonVisibility();
  }

  function updateCountText() {
    if (!countText) return;
    const total = filteredOrders.length;
    const stVal = filterStatus ? filterStatus.value : "PENDING_APPROVAL";
    let label = "đơn hàng";
    if (stVal === "PENDING_APPROVAL") label = "đơn hàng chờ duyệt";
    else if (stVal === "RECORDED" || stVal === "APPROVED") label = "đơn hàng đã duyệt";
    else if (stVal === "REJECTED") label = "đơn hàng bị từ chối";
    else if (stVal === "RETURNED") label = "đơn hàng trả lại sửa";

    countText.textContent = `${total} ${label}`;
  }

  function updateResetButtonVisibility() {
    if (!btnResetFilters) return;
    const hasSearch = searchInput && searchInput.value.trim() !== "";
    const hasReason = filterReason && filterReason.value !== "";
    const hasStatus = filterStatus && filterStatus.value !== "PENDING_APPROVAL";
    btnResetFilters.hidden = !(hasSearch || hasReason || hasStatus);
  }

  // ── Render Table Rows ──
  function renderTable(orders) {
    if (!orders.length) {
      tbody.innerHTML = `<tr><td colspan="8"><div class="oa-empty">
        <svg viewBox="0 0 24 24" width="40" height="40" stroke="var(--ink-400)" fill="none" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/></svg>
        <span>Không có đơn hàng nào phù hợp với bộ lọc hiện tại.</span>
      </div></td></tr>`;
      return;
    }

    tbody.innerHTML = orders
      .map((order) => {
        const orderId = escapeHtml(order.id);
        const orderCode = escapeHtml(order.orderCode || `#${order.id}`);
        const customer = escapeHtml(order.customerName || order.dealerName || "—");
        const amount = formatCurrency(order.totalAmount);
        const dateStr = formatDate(order.createdAt);
        const status = order.status || "PENDING_APPROVAL";
        const statusInfo = STATUS_CONFIG[status] || {
          label: status,
          cssClass: "oa-status-badge--pending",
        };
        const isPending = status === "PENDING_APPROVAL";

        // Phân tích lý do & mức vi phạm
        const { reasons, violations } = parseOrderApprovalInfo(order);

        const reasonTagsHtml = reasons
          .map((r) => `<span class="oa-reason-tag ${r.tagClass}">${escapeHtml(r.label)}</span>`)
          .join(" ");

        let violationHtml = "";
        if (violations.length > 0) {
          violationHtml = violations
            .map(
              (v) => `
            <div class="oa-violation-detail">
              <span class="oa-violation-value ${v.type === "price" ? "oa-violation-value--price" : ""}">${escapeHtml(v.displayValue)}</span>
              <span class="oa-violation-label">${escapeHtml(v.description)}</span>
            </div>`,
            )
            .join("");
        } else {
          violationHtml = `<span style="color:var(--ink-400); font-size:0.75rem;">Theo quy chế</span>`;
        }

        const actionsHtml = isPending
          ? `<div class="oa-actions-wrap">
              <button type="button" class="oa-action-btn oa-action-btn--approve" data-action="approve" data-id="${orderId}" title="Duyệt đơn hàng">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                Duyệt
              </button>
              <button type="button" class="oa-action-btn oa-action-btn--reject" data-action="reject" data-id="${orderId}" title="Từ chối đơn hàng">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                Từ chối
              </button>
              <button type="button" class="oa-action-btn oa-action-btn--return" data-action="return" data-id="${orderId}" title="Trả lại sửa">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>
                Trả lại
              </button>
            </div>`
          : `<button type="button" class="btn btn--secondary" data-action="detail" data-id="${orderId}" style="padding:4px 10px; font-size:0.75rem;">
              Chi tiết
            </button>`;

        return `<tr data-order-id="${orderId}">
          <td>
            <button type="button" class="oa-order-code" data-action="detail" data-id="${orderId}" title="Xem chi tiết đơn hàng">
              ${orderCode}
            </button>
          </td>
          <td>
            <div class="oa-dealer-name">${customer}</div>
            ${order.customerGroup ? `<span style="font-size:0.6875rem; color:var(--ink-500);">${escapeHtml(order.customerGroup)}</span>` : ""}
          </td>
          <td class="oa-amount"><strong>${amount}</strong></td>
          <td>${reasonTagsHtml || "—"}</td>
          <td>${violationHtml}</td>
          <td>${dateStr}</td>
          <td>
            <span class="oa-status-badge ${statusInfo.cssClass}">
              <span class="oa-status-badge__dot"></span>
              ${escapeHtml(statusInfo.label)}
            </span>
          </td>
          <td>${actionsHtml}</td>
        </tr>`;
      })
      .join("");
  }

  // ── Pagination ──
  function renderPagination(totalPages) {
    if (!paginationEl) return;
    if (totalPages <= 1) {
      paginationEl.hidden = true;
      return;
    }
    paginationEl.hidden = false;

    let html = `<button type="button" data-page="${currentPage - 1}" ${currentPage === 0 ? "disabled" : ""} aria-label="Trang trước">&laquo;</button>`;

    const maxVisible = 5;
    let start = Math.max(0, currentPage - Math.floor(maxVisible / 2));
    let end = Math.min(totalPages, start + maxVisible);
    if (end - start < maxVisible) start = Math.max(0, end - maxVisible);

    for (let i = start; i < end; i++) {
      html += `<button type="button" data-page="${i}" class="${i === currentPage ? "active" : ""}" aria-label="Trang ${i + 1}">${i + 1}</button>`;
    }

    html += `<button type="button" data-page="${currentPage + 1}" ${currentPage >= totalPages - 1 ? "disabled" : ""} aria-label="Trang sau">&raquo;</button>`;
    paginationEl.innerHTML = html;
  }

  paginationEl?.addEventListener("click", (e) => {
    const btn = e.target.closest("button[data-page]");
    if (!btn || btn.disabled) return;
    currentPage = parseInt(btn.dataset.page, 10);
    applyFiltersAndRender();
  });

  // ── Search & Filter Listeners ──
  searchInput?.addEventListener("input", () => {
    if (searchClear) searchClear.hidden = !searchInput.value;
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => {
      currentPage = 0;
      applyFiltersAndRender();
    }, 250);
  });

  searchClear?.addEventListener("click", () => {
    if (searchInput) searchInput.value = "";
    searchClear.hidden = true;
    currentPage = 0;
    applyFiltersAndRender();
  });

  filterReason?.addEventListener("change", () => {
    currentPage = 0;
    applyFiltersAndRender();
  });

  filterStatus?.addEventListener("change", () => {
    currentPage = 0;
    applyFiltersAndRender();
  });

  btnResetFilters?.addEventListener("click", () => {
    if (searchInput) searchInput.value = "";
    if (searchClear) searchClear.hidden = true;
    if (filterReason) filterReason.value = "";
    if (filterStatus) filterStatus.value = "PENDING_APPROVAL";
    currentPage = 0;
    applyFiltersAndRender();
  });

  // ── Table Action Delegation ──
  tbody?.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-action]");
    if (!btn) return;
    const action = btn.dataset.action;
    const orderId = btn.dataset.id;
    if (!orderId) return;

    if (action === "detail") openOrderDetail(orderId);
    else if (action === "approve") openApproveModal(orderId);
    else if (action === "reject") openRejectModal(orderId);
    else if (action === "return") openReturnModal(orderId);
  });

  // ── Order Detail Modal ──
  const detailModal = document.getElementById("order-detail-modal");
  const detailActions = document.getElementById("order-detail-actions");

  document.querySelectorAll("[data-close-order-detail]").forEach((btn) =>
    btn.addEventListener("click", () => {
      if (detailModal) detailModal.hidden = true;
    }),
  );

  async function openOrderDetail(orderId) {
    if (!detailModal) return;
    detailModal.hidden = false;
    document.getElementById("order-detail-sub").textContent = "Đang tải dữ liệu…";
    document.getElementById("order-detail-info").innerHTML = "";
    document.getElementById("order-detail-violations").innerHTML = "";
    document.getElementById("order-detail-items").innerHTML = "";
    document.getElementById("order-detail-total").innerHTML = "";
    document.getElementById("order-detail-history").innerHTML = "";
    if (detailActions) detailActions.hidden = true;

    try {
      const response = await Auth.apiFetch(`/orders/${orderId}`);
      if (!response.ok) throw new Error("Không thể tải chi tiết đơn hàng.");
      const res = await response.json();
      const order = res.data || res;
      activeDetailOrder = order;
      renderOrderDetail(order);
    } catch (err) {
      document.getElementById("order-detail-sub").textContent = err.message;
    }
  }

  function renderOrderDetail(order) {
    document.getElementById("order-detail-sub").textContent = `Mã đơn hàng: ${order.orderCode || `#${order.id}`}`;

    // 1. Thông tin chung
    const infoEl = document.getElementById("order-detail-info");
    infoEl.innerHTML = `
      <div class="order-detail-field">
        <span class="order-detail-field__label">Mã đơn hàng</span>
        <span class="order-detail-field__value"><strong>${escapeHtml(order.orderCode || `#${order.id}`)}</strong></span>
      </div>
      <div class="order-detail-field">
        <span class="order-detail-field__label">Đại lý / Khách hàng</span>
        <span class="order-detail-field__value">${escapeHtml(order.customerName || order.dealerName || "—")}</span>
      </div>
      <div class="order-detail-field">
        <span class="order-detail-field__label">Nhóm khách hàng</span>
        <span class="order-detail-field__value">${escapeHtml(order.customerGroup || "—")}</span>
      </div>
      <div class="order-detail-field">
        <span class="order-detail-field__label">Bảng giá áp dụng</span>
        <span class="order-detail-field__value">${escapeHtml(order.priceListCode || "Bảng giá chuẩn")}</span>
      </div>
      <div class="order-detail-field">
        <span class="order-detail-field__label">Ngày tạo</span>
        <span class="order-detail-field__value">${formatDateTime(order.createdAt)}</span>
      </div>
      <div class="order-detail-field">
        <span class="order-detail-field__label">Người tạo</span>
        <span class="order-detail-field__value">${escapeHtml(order.createdBy || "—")}</span>
      </div>
      ${
        order.note
          ? `<div class="order-detail-field" style="grid-column: 1 / -1;">
              <span class="order-detail-field__label">Ghi chú</span>
              <span class="order-detail-field__value">${escapeHtml(order.note)}</span>
            </div>`
          : ""
      }
    `;

    // 2. Vi phạm & Lý do cần duyệt (S4-05)
    const { violations } = parseOrderApprovalInfo(order);
    const violationsEl = document.getElementById("order-detail-violations");

    if (violations.length > 0) {
      let cardsHtml = `<h3>Lý do cần duyệt &amp; Mức vi phạm</h3>`;
      cardsHtml += violations
        .map((v) => {
          const isPrice = v.type === "price";
          const cardClass = isPrice ? "oa-violation-card--price" : "oa-violation-card--debt";
          const icon = isPrice
            ? '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>'
            : '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>';

          return `
          <div class="oa-violation-card ${cardClass}">
            <div class="oa-violation-card__icon">${icon}</div>
            <div class="oa-violation-card__text">
              <div class="oa-violation-card__title">${escapeHtml(v.title)}</div>
              <div class="oa-violation-card__desc">${escapeHtml(v.description)}</div>
            </div>
            <div class="oa-violation-card__value">${escapeHtml(v.displayValue)}</div>
          </div>`;
        })
        .join("");
      violationsEl.innerHTML = cardsHtml;
    } else {
      violationsEl.innerHTML = "";
    }

    // 3. Danh sách dòng sản phẩm
    const items = Array.isArray(order.items) ? order.items : [];
    const itemsTbody = document.getElementById("order-detail-items");
    if (items.length > 0) {
      itemsTbody.innerHTML = items
        .map((it) => {
          const floor = Number(it.floorPrice || 0);
          const price = Number(it.price || 0);
          const isBelow = it.belowFloorPrice || (floor > 0 && price < floor);
          const qty = it.quantity != null ? it.quantity : 0;
          const unit = it.unit ? ` ${it.unit}` : "";
          const discountPct = it.discountPercent ? `${it.discountPercent}%` : "—";
          const lineTotal = it.totalAmount != null ? formatCurrency(it.totalAmount) : "—";

          return `
          <tr class="${isBelow ? "oa-row-below-floor" : ""}">
            <td>
              <strong>${escapeHtml(it.productName || it.productCode || "—")}</strong>
              ${it.productCode ? `<div style="font-size:0.6875rem; color:var(--ink-500);">${escapeHtml(it.productCode)}</div>` : ""}
            </td>
            <td>${qty}${unit}</td>
            <td class="${isBelow ? "oa-price-below" : ""}">
              ${formatCurrency(price)}
              ${isBelow ? '<span class="oa-tag-below">Dưới sàn</span>' : ""}
            </td>
            <td>${floor > 0 ? formatCurrency(floor) : "—"}</td>
            <td>${discountPct}</td>
            <td class="oa-amount"><strong>${lineTotal}</strong></td>
          </tr>`;
        })
        .join("");
    } else {
      itemsTbody.innerHTML = `<tr><td colspan="6" style="text-align:center; color:var(--ink-500); padding:16px;">Không có dòng sản phẩm.</td></tr>`;
    }

    // 4. Tổng tiền
    const subtotal = order.subtotalAmount != null ? order.subtotalAmount : order.totalAmount;
    const discount = order.discountAmount || 0;
    const grandTotal = order.totalAmount || 0;

    document.getElementById("order-detail-total").innerHTML = `
      <div class="order-detail-total__block">
        <div class="order-detail-total__row">
          <span>Tổng tiền hàng</span>
          <span>${formatCurrency(subtotal)}</span>
        </div>
        ${
          discount > 0
            ? `<div class="order-detail-total__row">
                <span>Chiết khấu sản lượng</span>
                <span>-${formatCurrency(discount)}</span>
              </div>`
            : ""
        }
        <div class="order-detail-total__row order-detail-total__row--grand">
          <span>Tổng thanh toán</span>
          <span>${formatCurrency(grandTotal)}</span>
        </div>
      </div>
    `;

    // 5. Lịch sử duyệt (Không sửa và không xoá được)
    const historyEl = document.getElementById("order-detail-history");
    let historyHtml = `<h3>Lịch sử duyệt &amp; Xử lý</h3><div class="oa-history-list">`;

    // Dòng 1: Tạo đơn
    historyHtml += `
      <div class="oa-history-item oa-history-item--pending">
        <div class="oa-history-item__icon">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 14 14"/></svg>
        </div>
        <div class="oa-history-item__body">
          <div class="oa-history-item__header">
            <span class="oa-history-item__action">Tạo đơn &amp; Gửi duyệt</span>
            <span class="oa-history-item__time">${formatDateTime(order.createdAt)}</span>
          </div>
          <div class="oa-history-item__user">Người gửi: ${escapeHtml(order.createdBy || "Nhân viên kinh doanh")}</div>
          ${order.approvalReason ? `<div class="oa-history-item__comment">Lý do duyệt: ${escapeHtml(order.approvalReason)}</div>` : ""}
        </div>
      </div>
    `;

    // Dòng 2: Quyết định duyệt hoặc từ chối nếu có
    if (order.approvedAt || (order.status && order.status !== "PENDING_APPROVAL")) {
      const isApproved = order.status === "RECORDED" || order.status === "APPROVED";
      const isRejected = order.status === "REJECTED";
      const isReturned = order.status === "RETURNED";

      const actLabel = isApproved ? "Phê duyệt đơn hàng" : isRejected ? "Từ chối duyệt đơn" : isReturned ? "Trả lại sửa" : order.status;
      const actCss = isApproved ? "approved" : isRejected ? "rejected" : isReturned ? "returned" : "pending";
      const actIcon = isApproved
        ? '<polyline points="20 6 9 17 4 12"/>'
        : isRejected
          ? '<line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>'
          : '<polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>';

      let commentText = "";
      if (order.note && order.note.includes("Lý do từ chối:")) {
        commentText = order.note.split("Lý do từ chối:")[1]?.trim();
      } else if (order.note && order.note.includes("Ý kiến:")) {
        commentText = order.note.split("Ý kiến:")[1]?.trim();
      }

      historyHtml += `
        <div class="oa-history-item oa-history-item--${actCss}">
          <div class="oa-history-item__icon">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">${actIcon}</svg>
          </div>
          <div class="oa-history-item__body">
            <div class="oa-history-item__header">
              <span class="oa-history-item__action">${escapeHtml(actLabel)}</span>
              <span class="oa-history-item__time">${formatDateTime(order.approvedAt || order.recordedAt)}</span>
            </div>
            <div class="oa-history-item__user">Người thực hiện: ${escapeHtml(order.approvedBy || "Quản lý kinh doanh")}</div>
            ${commentText ? `<div class="oa-history-item__comment">Ý kiến: ${escapeHtml(commentText)}</div>` : ""}
          </div>
        </div>
      `;
    }

    historyHtml += `</div>`;
    historyHtml += `
      <div class="oa-history-notice">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
        Lịch sử duyệt không sửa và không xoá được.
      </div>
    `;
    historyEl.innerHTML = historyHtml;

    // 6. Action buttons trong Modal
    if (detailActions) {
      if (order.status === "PENDING_APPROVAL") {
        detailActions.hidden = false;
        document.getElementById("detail-btn-approve").onclick = () => {
          detailModal.hidden = true;
          openApproveModal(order.id);
        };
        document.getElementById("detail-btn-reject").onclick = () => {
          detailModal.hidden = true;
          openRejectModal(order.id);
        };
        document.getElementById("detail-btn-return").onclick = () => {
          detailModal.hidden = true;
          openReturnModal(order.id);
        };
      } else {
        detailActions.hidden = true;
      }
    }
  }

  // ── Modal 1: Duyệt đơn hàng (Approve Modal) ──
  const approveModal = document.getElementById("approve-modal");
  const approveForm = document.getElementById("approve-form");

  document.querySelectorAll("[data-close-approve]").forEach((btn) =>
    btn.addEventListener("click", () => {
      if (approveModal) approveModal.hidden = true;
    }),
  );

  function openApproveModal(orderId) {
    const order = allOrders.find((o) => String(o.id) === String(orderId)) || activeDetailOrder;
    document.getElementById("approve-order-id").value = orderId;
    document.getElementById("approve-modal-sub").textContent = order ? `Mã đơn: ${order.orderCode || `#${orderId}`} · ${escapeHtml(order.customerName || "")}` : `Đơn hàng #${orderId}`;
    document.getElementById("approve-comment").value = "";
    const msgEl = document.getElementById("approve-form-message");
    if (msgEl) msgEl.hidden = true;
    const btn = document.getElementById("btn-submit-approve");
    if (btn) {
      btn.disabled = false;
      btn.textContent = "Duyệt đơn hàng";
    }
    if (approveModal) approveModal.hidden = false;
  }

  approveForm?.addEventListener("submit", async (e) => {
    e.preventDefault();
    const orderId = document.getElementById("approve-order-id").value;
    const comment = document.getElementById("approve-comment").value.trim();
    const btn = document.getElementById("btn-submit-approve");
    const msgEl = document.getElementById("approve-form-message");

    btn.disabled = true;
    btn.textContent = "Đang phê duyệt…";
    if (msgEl) msgEl.hidden = true;

    try {
      // Backend OrderController: PUT /api/orders/{id}/approve
      const response = await Auth.apiFetch(`/orders/${orderId}/approve`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: comment ? JSON.stringify({ note: comment }) : undefined,
      });

      const result = await response.json().catch(() => null);
      if (!response.ok || (result && result.success === false)) {
        throw new Error(result?.message || "Không thể phê duyệt đơn hàng.");
      }

      if (approveModal) approveModal.hidden = true;
      showToast("Đã duyệt đơn hàng. Đơn chuyển sang giữ chỗ tồn và chờ xuất kho.", "success");
      await loadOrders();
    } catch (error) {
      if (msgEl) {
        msgEl.textContent = error.message;
        msgEl.className = "form-message form-message--error";
        msgEl.hidden = false;
      }
    } finally {
      btn.disabled = false;
      btn.textContent = "Duyệt đơn hàng";
    }
  });

  // ── Modal 2: Từ chối đơn hàng (Reject Modal — Bắt buộc ý kiến) ──
  const rejectModal = document.getElementById("reject-modal");
  const rejectForm = document.getElementById("reject-form");

  document.querySelectorAll("[data-close-reject]").forEach((btn) =>
    btn.addEventListener("click", () => {
      if (rejectModal) rejectModal.hidden = true;
    }),
  );

  function openRejectModal(orderId) {
    const order = allOrders.find((o) => String(o.id) === String(orderId)) || activeDetailOrder;
    document.getElementById("reject-order-id").value = orderId;
    document.getElementById("reject-modal-sub").textContent = order ? `Mã đơn: ${order.orderCode || `#${orderId}`} · ${escapeHtml(order.customerName || "")}` : `Đơn hàng #${orderId}`;
    const commentInput = document.getElementById("reject-comment");
    if (commentInput) commentInput.value = "";
    const msgEl = document.getElementById("reject-form-message");
    if (msgEl) msgEl.hidden = true;
    const btn = document.getElementById("btn-submit-reject");
    if (btn) {
      btn.disabled = false;
      btn.textContent = "Từ chối đơn hàng";
    }
    if (rejectModal) rejectModal.hidden = false;
    setTimeout(() => commentInput?.focus(), 50);
  }

  rejectForm?.addEventListener("submit", async (e) => {
    e.preventDefault();
    const orderId = document.getElementById("reject-order-id").value;
    const comment = document.getElementById("reject-comment").value.trim();
    const btn = document.getElementById("btn-submit-reject");
    const msgEl = document.getElementById("reject-form-message");

    // Tiêu chí chấp nhận: Từ chối bắt buộc nhập ý kiến!
    if (!comment) {
      if (msgEl) {
        msgEl.textContent = "Vui lòng nhập lý do từ chối (bắt buộc).";
        msgEl.className = "form-message form-message--error";
        msgEl.hidden = false;
      }
      document.getElementById("reject-comment")?.focus();
      return;
    }

    if (comment.length > 500) {
      if (msgEl) {
        msgEl.textContent = "Lý do từ chối tối đa 500 ký tự.";
        msgEl.className = "form-message form-message--error";
        msgEl.hidden = false;
      }
      return;
    }

    btn.disabled = true;
    btn.textContent = "Đang xử lý…";
    if (msgEl) msgEl.hidden = true;

    try {
      // Backend OrderController: PUT /api/orders/{id}/reject?reason=...
      const response = await Auth.apiFetch(`/orders/${orderId}/reject?reason=${encodeURIComponent(comment)}`, {
        method: "PUT",
      });

      const result = await response.json().catch(() => null);
      if (!response.ok || (result && result.success === false)) {
        throw new Error(result?.message || "Không thể từ chối đơn hàng.");
      }

      if (rejectModal) rejectModal.hidden = true;
      showToast("Đã từ chối đơn hàng.", "success");
      await loadOrders();
    } catch (error) {
      if (msgEl) {
        msgEl.textContent = error.message;
        msgEl.className = "form-message form-message--error";
        msgEl.hidden = false;
      }
    } finally {
      btn.disabled = false;
      btn.textContent = "Từ chối đơn hàng";
    }
  });

  // ── Modal 3: Trả lại sửa (Return Modal — Bắt buộc ý kiến) ──
  const returnModal = document.getElementById("return-modal");
  const returnForm = document.getElementById("return-form");

  document.querySelectorAll("[data-close-return]").forEach((btn) =>
    btn.addEventListener("click", () => {
      if (returnModal) returnModal.hidden = true;
    }),
  );

  function openReturnModal(orderId) {
    const order = allOrders.find((o) => String(o.id) === String(orderId)) || activeDetailOrder;
    document.getElementById("return-order-id").value = orderId;
    document.getElementById("return-modal-sub").textContent = order ? `Mã đơn: ${order.orderCode || `#${orderId}`} · ${escapeHtml(order.customerName || "")}` : `Đơn hàng #${orderId}`;
    const commentInput = document.getElementById("return-comment");
    if (commentInput) commentInput.value = "";
    const msgEl = document.getElementById("return-form-message");
    if (msgEl) msgEl.hidden = true;
    const btn = document.getElementById("btn-submit-return");
    if (btn) {
      btn.disabled = false;
      btn.textContent = "Trả lại sửa";
    }
    if (returnModal) returnModal.hidden = false;
    setTimeout(() => commentInput?.focus(), 50);
  }

  returnForm?.addEventListener("submit", async (e) => {
    e.preventDefault();
    const orderId = document.getElementById("return-order-id").value;
    const comment = document.getElementById("return-comment").value.trim();
    const btn = document.getElementById("btn-submit-return");
    const msgEl = document.getElementById("return-form-message");

    // Tiêu chí chấp nhận: Trả lại sửa bắt buộc nhập ý kiến!
    if (!comment) {
      if (msgEl) {
        msgEl.textContent = "Vui lòng nhập ý kiến chỉnh sửa (bắt buộc).";
        msgEl.className = "form-message form-message--error";
        msgEl.hidden = false;
      }
      document.getElementById("return-comment")?.focus();
      return;
    }

    if (comment.length > 500) {
      if (msgEl) {
        msgEl.textContent = "Ý kiến chỉnh sửa tối đa 500 ký tự.";
        msgEl.className = "form-message form-message--error";
        msgEl.hidden = false;
      }
      return;
    }

    btn.disabled = true;
    btn.textContent = "Đang xử lý…";
    if (msgEl) msgEl.hidden = true;

    try {
      // Gửi yêu cầu trả lại sửa tới API backend
      const response = await Auth.apiFetch(`/orders/${orderId}/return?reason=${encodeURIComponent(comment)}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: comment }),
      });

      const result = await response.json().catch(() => null);
      if (!response.ok || (result && result.success === false)) {
        // Nếu backend chưa có endpoint /return (S4-05 backend chưa hoàn thiện), thông báo rõ ràng cho người dùng
        if (response.status === 404 || response.status === 405 || response.status === 500) {
          throw new Error("Chức năng 'Trả lại sửa' đã hoàn thiện phía frontend và đang chờ backend bổ sung API hoàn tất (S4-05).");
        }
        throw new Error(result?.message || "Không thể trả lại đơn hàng.");
      }

      if (returnModal) returnModal.hidden = true;
      showToast("Đã trả lại đơn hàng để nhân viên chỉnh sửa.", "success");
      await loadOrders();
    } catch (error) {
      if (msgEl) {
        msgEl.textContent = error.message;
        msgEl.className = "form-message form-message--error";
        msgEl.hidden = false;
      }
    } finally {
      btn.disabled = false;
      btn.textContent = "Trả lại sửa";
    }
  });

  // ── Close Modals on Overlay / Escape ──
  document.querySelectorAll(".modal-overlay").forEach((modalOv) => {
    modalOv.addEventListener("click", (e) => {
      if (e.target === modalOv) modalOv.hidden = true;
    });
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      [detailModal, approveModal, rejectModal, returnModal].forEach((m) => {
        if (m && !m.hidden) m.hidden = true;
      });
    }
  });

  // ── Khởi động ──
  loadOrders();
})();
