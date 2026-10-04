/**
 * audit-logs.js — Tra cứu Nhật ký thao tác (S2-04).
 * Dữ liệu chỉ được đọc từ API; giao diện không tạo hoặc lưu bản ghi nhật ký giả.
 */
(() => {
  "use strict";

  if (!Auth.requireLogin()) return;
  if (!Auth.hasRole("ROLE_ADMIN")) {
    location.href = "../error.html?status=403";
    return;
  }

  const TYPE_CONFIG = {
    INVENTORY: { label: "Tồn kho", badgeClass: "entity-tag--INVENTORY" },
    PRICE: { label: "Giá bán", badgeClass: "entity-tag--PRICE" },
    DEBT_LIMIT: { label: "Hạn mức công nợ", badgeClass: "entity-tag--DEBT_LIMIT" },
    INVOICE: { label: "Hoá đơn", badgeClass: "entity-tag--INVOICE" },
  };

  const ROLE_NAMES = {
    ROLE_ADMIN: "Quản trị hệ thống",
    ROLE_SALES_MANAGER: "Quản lý kinh doanh",
    ROLE_WAREHOUSE_MANAGER: "Quản lý kho",
    ROLE_SALES: "Nhân viên kinh doanh",
    ROLE_WAREHOUSE_STAFF: "Thủ kho",
    ROLE_ACCOUNTANT: "Kế toán",
    ROLE_VIEWER: "Chỉ xem",
  };

  const ACTION_NAMES = {
    CREATE: "Tạo mới",
    UPDATE: "Cập nhật",
    DELETE: "Xoá",
    IMPORT: "Nhập kho",
    EXPORT: "Xuất kho",
    ADJUST: "Điều chỉnh",
  };

  const state = {
    logs: [],
    filteredLogs: [],
    users: [],
    page: 0,
    pageSize: 10,
    totalPages: 1,
    activePreset: "all",
    loading: true,
    loadError: "",
  };

  const $ = (id) => document.getElementById(id);
  const escapeHtml = (value) => String(value ?? "").replace(/[&<>'"]/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "'": "&#39;",
    '"': "&quot;",
  }[char]));
  const normalizeRole = (role) => role && role.startsWith("ROLE_") ? role : `ROLE_${role || "VIEWER"}`;

  function releaseAvatarImages(root) {
    root?.querySelectorAll(".actor-avatar img[data-object-url]").forEach((image) => {
      URL.revokeObjectURL(image.dataset.objectUrl);
      delete image.dataset.objectUrl;
    });
  }

  async function hydrateAvatarImages(root) {
    const avatars = [...(root?.querySelectorAll(".actor-avatar[data-avatar-url]") || [])];
    await Promise.all(avatars.map(async (avatar) => {
      const image = avatar.querySelector("img");
      const fallback = avatar.querySelector("span");
      const loaded = await Auth.setProtectedImage(image, avatar.dataset.avatarUrl);
      if (!avatar.isConnected || !root.contains(avatar)) {
        if (image.dataset.objectUrl) URL.revokeObjectURL(image.dataset.objectUrl);
        return;
      }
      if (loaded) {
        image.hidden = false;
        fallback.hidden = true;
      }
    }));
  }

  function actorAvatarMarkup(actor, extraClass = "") {
    const initial = (actor?.fullName || actor?.username || "U").trim().charAt(0).toUpperCase();
    const avatarAttribute = actor?.avatarThumbnailUrl
      ? ` data-avatar-url="${escapeHtml(actor.avatarThumbnailUrl)}"`
      : "";
    return `<span class="actor-avatar${extraClass ? ` ${extraClass}` : ""}"${avatarAttribute}>
      <span>${escapeHtml(initial)}</span>
      <img alt="" hidden>
    </span>`;
  }

  function roleLabel(role) {
    const normalized = normalizeRole(role);
    return ROLE_NAMES[normalized] || normalized;
  }

  function setPaginationEmpty(summary = "Hiển thị 0 bản ghi") {
    $("audit-pagination-summary").textContent = summary;
    $("audit-page-indicator").textContent = "Trang 1 / 1";
    ["btn-page-first", "btn-page-prev", "btn-page-next", "btn-page-last"].forEach((id) => {
      $(id).disabled = true;
    });
  }

  function renderUserFilter() {
    const currentValue = $("filter-user").value;
    $("filter-user").innerHTML = '<option value="">Tất cả người dùng</option>' + state.users
      .map((user) => `<option value="${escapeHtml(user.username)}">${escapeHtml(user.fullName)} (${escapeHtml(user.username)} · ${escapeHtml(roleLabel(user.role))})</option>`)
      .join("");
    if (state.users.some((user) => user.username === currentValue)) {
      $("filter-user").value = currentValue;
    }
  }

  async function loadUsersList() {
    try {
      const apiUsers = [];
      let page = 0;
      let totalPages = 1;
      while (page < totalPages) {
        const response = await Auth.apiFetch(`/admin/users?page=${page}&size=100`);
        const result = await response.json().catch(() => ({}));
        if (!response.ok || result.success === false) {
          throw new Error(result.message || "Không tải được danh sách người dùng");
        }
        apiUsers.push(...(result?.data?.content || []));
        totalPages = Math.max(Number(result?.data?.totalPages) || 1, 1);
        page += 1;
      }

      state.users = apiUsers.map((user) => ({
        username: user.username,
        fullName: user.fullName || user.username,
        role: normalizeRole(user.roles?.[0]),
        avatarThumbnailUrl: user.avatarThumbnailUrl || null,
      }));
    } catch (error) {
      console.warn("Không tải được danh sách người dùng cho bộ lọc", error);
      state.users = [];
    }
    renderUserFilter();
  }

  function parseNumber(value) {
    const normalized = String(value ?? "").replace(/[^\d.-]/g, "");
    const number = Number.parseFloat(normalized);
    return Number.isFinite(number) ? number : null;
  }

  function targetName(entityType, entityId) {
    if (entityType === "DEBT_LIMIT") return `Đại lý #${entityId}`;
    if (entityType === "INVENTORY") return `Hàng hoá #${entityId}`;
    if (entityType === "INVOICE") return `Hoá đơn #${entityId}`;
    if (entityType === "PRICE") return `Sản phẩm #${entityId}`;
    return `Đối tượng #${entityId}`;
  }

  function normalizeApiLog(log) {
    const actor = state.users.find((user) => user.username === log.username);
    const beforeNumber = parseNumber(log.oldValue);
    const afterNumber = parseNumber(log.newValue);
    const delta = beforeNumber !== null && afterNumber !== null ? afterNumber - beforeNumber : null;

    return {
      id: String(log.id),
      timestamp: log.createdAt,
      targetType: log.entityType,
      action: ACTION_NAMES[log.action] || log.action,
      targetCode: String(log.entityId ?? "—"),
      targetName: targetName(log.entityType, log.entityId),
      context: "Dữ liệu nghiệp vụ trên hệ thống",
      actor: {
        username: log.username,
        fullName: actor?.fullName || log.username,
        role: actor?.role || "ROLE_VIEWER",
        avatarThumbnailUrl: actor?.avatarThumbnailUrl || null,
      },
      valBefore: log.oldValue ?? "—",
      valAfter: log.newValue ?? "—",
      deltaText: delta === null ? "Đã thay đổi" : `${delta > 0 ? "+" : ""}${delta}`,
      deltaType: delta === null || delta === 0 ? "neutral" : delta > 0 ? "positive" : "negative",
      isDiscrepancy: log.entityType === "INVENTORY" && delta !== null && delta < 0,
      reason: "Ghi tự động từ thao tác nghiệp vụ đã xác thực.",
    };
  }

  function syncUsersFromLogs() {
    const userMap = new Map(state.users.map((user) => [user.username, user]));
    state.logs.forEach((log) => {
      if (!userMap.has(log.actor.username)) {
        userMap.set(log.actor.username, {
          username: log.actor.username,
          fullName: log.actor.fullName,
          role: log.actor.role,
        });
      }
    });
    state.users = Array.from(userMap.values()).sort((a, b) => a.fullName.localeCompare(b.fullName, "vi"));
    renderUserFilter();
  }

  async function fetchAuditPages() {
    const pageSize = 100;
    const firstResponse = await Auth.apiFetch(`/admin/audit-logs?page=0&size=${pageSize}`);
    const firstResult = await firstResponse.json().catch(() => ({}));
    if (!firstResponse.ok || !Array.isArray(firstResult.data)) {
      throw new Error(firstResult.message || "Máy chủ không trả về dữ liệu nhật ký hợp lệ");
    }

    const rows = [...firstResult.data];
    const totalPages = Number(firstResult.totalPages) || 1;
    for (let page = 1; page < totalPages; page += 1) {
      const response = await Auth.apiFetch(`/admin/audit-logs?page=${page}&size=${pageSize}`);
      const result = await response.json().catch(() => ({}));
      if (!response.ok || !Array.isArray(result.data)) {
        throw new Error(result.message || `Không tải được trang nhật ký ${page + 1}`);
      }
      rows.push(...result.data);
    }
    return rows;
  }

  async function loadLogs() {
    state.loading = true;
    state.loadError = "";
    renderTable();

    try {
      const rows = await fetchAuditPages();
      state.logs = rows.map(normalizeApiLog);
      state.loading = false;
      syncUsersFromLogs();
      applyFilters();
    } catch (error) {
      console.error("Không tải được nhật ký thao tác", error);
      state.logs = [];
      state.filteredLogs = [];
      state.loading = false;
      state.loadError = "Không tải được nhật ký thao tác. Kiểm tra kết nối máy chủ và thử lại.";
      updateKPIs();
      renderTable();
      showToast(state.loadError, "error");
    }
  }

  function updateKPIs() {
    const inventoryLogs = state.logs.filter((log) => log.targetType === "INVENTORY");
    const discrepancyLogs = state.logs.filter((log) => log.isDiscrepancy);
    $("kpi-total-logs").textContent = state.logs.length;
    $("kpi-inventory-count").textContent = inventoryLogs.length;
    $("kpi-debt-count").textContent = state.logs.filter((log) => log.targetType === "DEBT_LIMIT").length;
    $("kpi-discrepancy-count").textContent = discrepancyLogs.length;
    $("kpi-inventory-desc").textContent = `${discrepancyLogs.length} lần lệch kiểm kê âm`;
  }

  function applyFilters() {
    const username = $("filter-user").value;
    const entityType = $("filter-type").value;
    const fromDate = $("filter-from-date").value;
    const toDate = $("filter-to-date").value;
    const keyword = $("filter-search").value.trim().toLocaleLowerCase("vi");

    let filtered = [...state.logs];
    if (username) filtered = filtered.filter((log) => log.actor.username === username);
    if (entityType) filtered = filtered.filter((log) => log.targetType === entityType);
    if (fromDate) {
      const start = new Date(`${fromDate}T00:00:00`).getTime();
      filtered = filtered.filter((log) => new Date(log.timestamp).getTime() >= start);
    }
    if (toDate) {
      const end = new Date(`${toDate}T23:59:59.999`).getTime();
      filtered = filtered.filter((log) => new Date(log.timestamp).getTime() <= end);
    }
    if (keyword) {
      filtered = filtered.filter((log) => [
        log.targetCode,
        log.targetName,
        log.action,
        log.reason,
        log.actor.fullName,
        log.actor.username,
      ].some((value) => String(value ?? "").toLocaleLowerCase("vi").includes(keyword)));
    }
    if (state.activePreset === "discrepancy") {
      filtered = filtered.filter((log) => log.isDiscrepancy);
    }

    filtered.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
    state.filteredLogs = filtered;
    state.totalPages = Math.max(1, Math.ceil(filtered.length / state.pageSize));
    state.page = Math.min(state.page, state.totalPages - 1);
    updateKPIs();
    renderTable();
  }

  function formatDateTime(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return { formatted: String(value ?? "—"), rel: "" };

    const formatted = new Intl.DateTimeFormat("vi-VN", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }).format(date);
    const diffMinutes = Math.max(0, Math.floor((Date.now() - date.getTime()) / 60000));
    let rel = "Vừa xong";
    if (diffMinutes >= 1440) rel = `${Math.floor(diffMinutes / 1440)} ngày trước`;
    else if (diffMinutes >= 60) rel = `${Math.floor(diffMinutes / 60)} giờ trước`;
    else if (diffMinutes > 0) rel = `${diffMinutes} phút trước`;
    return { formatted, rel };
  }

  function renderTable() {
    const tbody = $("audit-table-body");
    releaseAvatarImages(tbody);

    if (state.loading) {
      tbody.innerHTML = '<tr><td colspan="7" class="audit-load-state"><span class="audit-loading-dot" aria-hidden="true"></span><strong>Đang tải nhật ký thao tác…</strong></td></tr>';
      setPaginationEmpty("Đang tải dữ liệu…");
      return;
    }
    if (state.loadError) {
      tbody.innerHTML = `
        <tr>
          <td colspan="7" class="audit-load-state audit-load-state--error">
            <strong>${escapeHtml(state.loadError)}</strong>
            <span>Dữ liệu cũ không được dùng thay thế khi API gặp lỗi.</span>
            <button type="button" class="audit-retry-button" data-action="retry">Thử tải lại</button>
          </td>
        </tr>`;
      setPaginationEmpty("Chưa tải được dữ liệu");
      return;
    }

    const total = state.filteredLogs.length;
    if (!total) {
      tbody.innerHTML = `
        <tr>
          <td colspan="7" class="audit-load-state">
            <strong>Không tìm thấy bản ghi nhật ký phù hợp</strong>
            <span>Thay đổi tiêu chí lọc hoặc bấm “Đặt lại”.</span>
          </td>
        </tr>`;
      setPaginationEmpty("Hiển thị 0 trong tổng số 0 bản ghi");
      return;
    }

    const start = state.page * state.pageSize;
    const end = Math.min(start + state.pageSize, total);
    tbody.innerHTML = state.filteredLogs.slice(start, end).map((log) => {
      const time = formatDateTime(log.timestamp);
      const type = TYPE_CONFIG[log.targetType] || { label: log.targetType, badgeClass: "" };
      const deltaClass = `diff-delta--${log.deltaType}`;
      const deltaPrefix = log.deltaType === "negative" ? "↓ " : log.deltaType === "positive" ? "↑ " : "";
      const discrepancy = log.isDiscrepancy ? '<div class="discrepancy-flag">⚠ Lệch tồn kiểm kê</div>' : "";

      return `
        <tr data-log-id="${escapeHtml(log.id)}">
          <td class="audit-time-cell"><span class="audit-time-exact">${escapeHtml(time.formatted)}</span><span class="audit-time-rel">${escapeHtml(time.rel)}</span></td>
          <td><div class="actor-badge">${actorAvatarMarkup(log.actor)}<div class="actor-info"><span class="actor-name">${escapeHtml(log.actor.fullName)}</span><span class="actor-sub">${escapeHtml(log.actor.username)} · ${escapeHtml(roleLabel(log.actor.role))}</span></div></div></td>
          <td><span class="entity-tag ${escapeHtml(type.badgeClass)}">${escapeHtml(type.label)}</span><span class="action-title">${escapeHtml(log.action)}</span></td>
          <td class="target-cell"><span class="target-code">${escapeHtml(log.targetCode)}</span><span class="target-name">${escapeHtml(log.targetName)}</span><span class="target-context">${escapeHtml(log.context)}</span></td>
          <td class="diff-cell"><div class="diff-box"><span class="diff-val diff-val--before">${escapeHtml(log.valBefore)}</span><span class="diff-arrow">➔</span><span class="diff-val diff-val--after">${escapeHtml(log.valAfter)}</span></div><div style="margin-top:5px"><span class="diff-delta ${deltaClass}">${deltaPrefix}${escapeHtml(log.deltaText)}</span></div>${discrepancy}</td>
          <td class="reason-cell"><div class="reason-text">${escapeHtml(log.reason)}</div></td>
          <td style="text-align:right"><button class="btn-view-detail" type="button" data-action="view" data-id="${escapeHtml(log.id)}">Xem</button></td>
        </tr>`;
    }).join("");

    hydrateAvatarImages(tbody);
    $("audit-pagination-summary").textContent = `Hiển thị ${start + 1} - ${end} trong tổng số ${total} bản ghi`;
    $("audit-page-indicator").textContent = `Trang ${state.page + 1} / ${state.totalPages}`;
    $("btn-page-first").disabled = state.page === 0;
    $("btn-page-prev").disabled = state.page === 0;
    $("btn-page-next").disabled = state.page >= state.totalPages - 1;
    $("btn-page-last").disabled = state.page >= state.totalPages - 1;
  }

  function openDetailModal(logId) {
    const log = state.logs.find((item) => item.id === logId);
    if (!log) return;
    const time = formatDateTime(log.timestamp);
    const type = TYPE_CONFIG[log.targetType] || { label: log.targetType, badgeClass: "" };

    $("audit-detail-title").textContent = `Chi tiết thao tác: ${log.action}`;
    const detailBody = $("audit-detail-body");
    releaseAvatarImages(detailBody);
    $("audit-detail-subtitle").textContent = `Mã bản ghi: #${log.id}`;
    detailBody.innerHTML = `
      <div class="audit-detail-grid">
        <div class="detail-block"><div class="detail-block__label">Người thực hiện</div><div class="detail-block__content detail-actor">${actorAvatarMarkup(log.actor, "actor-avatar--detail")}<div><strong>${escapeHtml(log.actor.fullName)}</strong><div>${escapeHtml(log.actor.username)} · ${escapeHtml(roleLabel(log.actor.role))}</div></div></div></div>
        <div class="detail-block"><div class="detail-block__label">Thời điểm & phân hệ</div><div class="detail-block__content"><strong>${escapeHtml(time.formatted)}</strong><div style="margin-top:5px"><span class="entity-tag ${escapeHtml(type.badgeClass)}">${escapeHtml(type.label)}</span></div></div></div>
      </div>
      <div class="detail-block"><div class="detail-block__label">Đối tượng chịu tác động</div><div class="detail-block__content"><span class="target-code">${escapeHtml(log.targetCode)}</span> <strong>${escapeHtml(log.targetName)}</strong></div></div>
      <table class="diff-comparison-table"><thead><tr><th>Chỉ tiêu</th><th>Giá trị trước</th><th>Giá trị sau</th><th>Chênh lệch</th></tr></thead><tbody><tr><td>Dữ liệu ghi nhận</td><td>${escapeHtml(log.valBefore)}</td><td>${escapeHtml(log.valAfter)}</td><td><strong>${escapeHtml(log.deltaText)}</strong></td></tr></tbody></table>
      <div class="detail-block"><div class="detail-block__label">Nguồn ghi nhận</div><div class="detail-block__content">${escapeHtml(log.reason)}</div></div>`;
    hydrateAvatarImages(detailBody);
    $("modal-audit-detail").hidden = false;
  }

  function closeDetailModal() {
    $("modal-audit-detail").hidden = true;
    releaseAvatarImages($("audit-detail-body"));
  }

  function exportToExcel() {
    if (typeof XLSX === "undefined") {
      showToast("Chưa tải được thư viện xuất Excel. Vui lòng thử lại.", "error");
      return;
    }
    if (!state.filteredLogs.length) {
      showToast("Không có dữ liệu để xuất Excel.", "error");
      return;
    }

    const rows = state.filteredLogs.map((log, index) => ({
      STT: index + 1,
      "Mã bản ghi": log.id,
      "Thời điểm": formatDateTime(log.timestamp).formatted,
      "Người thực hiện": log.actor.fullName,
      "Tài khoản": log.actor.username,
      "Vai trò": roleLabel(log.actor.role),
      "Phân hệ": TYPE_CONFIG[log.targetType]?.label || log.targetType,
      "Thao tác": log.action,
      "Mã đối tượng": log.targetCode,
      "Giá trị trước": log.valBefore,
      "Giá trị sau": log.valAfter,
      "Chênh lệch": log.deltaText,
      "Lệch kiểm kê": log.isDiscrepancy ? "Có" : "Không",
    }));

    try {
      const worksheet = XLSX.utils.json_to_sheet(rows);
      const workbook = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(workbook, worksheet, "NhatKyThaoTac");
      const stamp = new Date().toISOString().slice(0, 10).replaceAll("-", "");
      XLSX.writeFile(workbook, `Nhat_ky_thao_tac_SaleStock_${stamp}.xlsx`);
      showToast(`Đã xuất ${rows.length} bản ghi.`, "success");
    } catch (error) {
      console.error("Không xuất được Excel", error);
      showToast("Không xuất được tệp Excel. Vui lòng thử lại.", "error");
    }
  }

  function showToast(message, type = "success") {
    const toast = document.createElement("div");
    toast.className = `audit-toast audit-toast--${type}`;
    toast.textContent = message;
    $("audit-toast-container").appendChild(toast);
    requestAnimationFrame(() => toast.classList.add("audit-toast--visible"));
    setTimeout(() => {
      toast.classList.remove("audit-toast--visible");
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  }

  function formatDateInput(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  }

  function handlePresetClick(preset) {
    state.activePreset = preset;
    document.querySelectorAll(".filter-chip").forEach((chip) => {
      chip.classList.toggle("filter-chip--active", chip.dataset.preset === preset);
    });
    $("filter-type").value = "";
    $("filter-from-date").value = "";
    $("filter-to-date").value = "";

    const now = new Date();
    if (preset === "discrepancy" || preset === "type-inventory") $("filter-type").value = "INVENTORY";
    else if (preset === "type-debt") $("filter-type").value = "DEBT_LIMIT";
    else if (preset === "type-price") $("filter-type").value = "PRICE";
    else if (preset === "type-invoice") $("filter-type").value = "INVOICE";
    else if (preset === "today") {
      $("filter-from-date").value = formatDateInput(now);
      $("filter-to-date").value = formatDateInput(now);
    } else if (preset === "last7days") {
      $("filter-from-date").value = formatDateInput(new Date(now.getTime() - 6 * 86400000));
      $("filter-to-date").value = formatDateInput(now);
    } else if (preset === "thismonth") {
      $("filter-from-date").value = formatDateInput(new Date(now.getFullYear(), now.getMonth(), 1));
      $("filter-to-date").value = formatDateInput(now);
    }
    state.page = 0;
    applyFilters();
  }

  function changePage(page) {
    state.page = Math.max(0, Math.min(page, state.totalPages - 1));
    renderTable();
  }

  function setupEventListeners() {
    $("audit-filters-form").addEventListener("submit", (event) => {
      event.preventDefault();
      state.page = 0;
      applyFilters();
    });
    $("btn-reset-filter").addEventListener("click", () => {
      $("audit-filters-form").reset();
      handlePresetClick("all");
    });
    document.querySelectorAll(".filter-chip").forEach((chip) => {
      chip.addEventListener("click", () => handlePresetClick(chip.dataset.preset));
    });
    $("btn-quick-discrepancy").addEventListener("click", () => {
      handlePresetClick("discrepancy");
      $("audit-filters-form").scrollIntoView({ behavior: "smooth", block: "start" });
    });
    $("audit-table-body").addEventListener("click", (event) => {
      const retryButton = event.target.closest("[data-action='retry']");
      if (retryButton) {
        loadLogs();
        return;
      }
      const detailButton = event.target.closest("[data-action='view']");
      if (detailButton) openDetailModal(detailButton.dataset.id);
    });
    document.querySelectorAll("[data-close-detail]").forEach((button) => button.addEventListener("click", closeDetailModal));
    $("modal-audit-detail").addEventListener("click", (event) => {
      if (event.target === $("modal-audit-detail")) closeDetailModal();
    });
    $("btn-print-audit").addEventListener("click", () => window.print());
    $("btn-export-excel").addEventListener("click", exportToExcel);
    $("btn-page-first").addEventListener("click", () => changePage(0));
    $("btn-page-prev").addEventListener("click", () => changePage(state.page - 1));
    $("btn-page-next").addEventListener("click", () => changePage(state.page + 1));
    $("btn-page-last").addEventListener("click", () => changePage(state.totalPages - 1));
    window.addEventListener("keydown", (event) => {
      if (event.key === "Escape") closeDetailModal();
    });
  }

  async function init() {
    setupEventListeners();
    await loadUsersList();
    await loadLogs();
  }

  init();
})();
