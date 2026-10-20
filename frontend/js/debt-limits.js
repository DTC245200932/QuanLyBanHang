/** S3-05: tra cứu và khai báo hạn mức công nợ theo đại lý. */
 (async () => {
  "use strict";
  if (new URLSearchParams(location.search).get("view") !== "debt-limits") return;
  if (!Auth.requireLogin()) return;
  // Quyền có thể vừa được cập nhật trong ma trận khi tab này vẫn đang mở.
  // Đồng bộ phiên trước khi kiểm tra menu và quyền khai báo để tránh hiện giao diện
  // nhưng gọi API bằng permissions cũ.
  await Auth.refreshPermissions();
  if (!MenuConfig.getMenuForUser(Auth.getUser()).some(item => item.id === "debt-limits")) {
    location.href = "/error?status=403";
    return;
  }

  // Quản trị viên có toàn quyền theo ma trận quyền và backend FinanceController.
  let canDeclare = Auth.hasPermission("FINANCE_MANAGE");
  const $ = id => document.getElementById(id);
  const escapeHtml = value => String(value ?? "").replace(/[&<>'"]/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;",
  })[char]);
  const currency = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 2 });
  const pageSize = 10;
  const state = {
    rows: [], page: 0, loading: false, error: "", selected: null,
    pendingDeclaration: null, saving: false,
  };
  const modal = $("debt-limit-modal");
  const form = $("debt-limit-form");
  let previousFocus;
  let previousOverflow;

  document.title = "Hạn mức công nợ | SaleStock";
  $("welcome-card").hidden = true;
  document.querySelector(".dashboard-work").hidden = true;
  $("debt-limits-view").hidden = false;
  $("debt-limits-readonly").hidden = canDeclare;

  function amountNumber(value) {
    if (value == null || String(value).trim() === "") return null;
    const number = Number(value);
    return Number.isFinite(number) && number >= 0 ? number : null;
  }

  function formatAmount(value) {
    const number = amountNumber(value);
    return number === null ? "Chưa có dữ liệu" : currency.format(number);
  }

  function formatDays(value) {
    const number = Number(value);
    return Number.isInteger(number) && number >= 0
      ? `${number.toLocaleString("vi-VN")} ngày`
      : "Chưa có dữ liệu";
  }

  function showToast(message, type = "error") {
    let container = $("toast-container");
    if (!container) {
      container = document.createElement("div");
      container.id = "toast-container";
      document.body.appendChild(container);
    }
    const toast = document.createElement("div");
    toast.className = `toast toast--${type}`;
    toast.setAttribute("role", "alert");
    toast.textContent = String(message ?? "");
    container.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add("toast--visible"));
    setTimeout(() => {
      toast.classList.remove("toast--visible");
      toast.classList.add("toast--exit");
      setTimeout(() => toast.remove(), 300);
    }, 4200);
  }

  function filteredRows() {
    const keyword = $("debt-limit-search").value.trim().toLocaleLowerCase("vi");
    const filter = $("debt-limit-filter").value;
    return state.rows.filter(row => {
      const matchesKeyword = !keyword || [row.customerName, row.dealerId].some(value =>
        String(value ?? "").toLocaleLowerCase("vi").includes(keyword));
      const amount = amountNumber(row.debtLimit);
      return matchesKeyword && (filter === "all" || (filter === "positive" && amount !== null && amount > 0)
        || (filter === "zero" && amount === 0));
    });
  }

  function render() {
    const rows = filteredRows();
    const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
    state.page = Math.min(state.page, totalPages - 1);
    $("reload-debt-limits").disabled = state.loading;
    $("debt-limits-view").setAttribute("aria-busy", String(state.loading));
    $("debt-limits-prev").disabled = state.loading || !!state.error || state.page === 0;
    $("debt-limits-next").disabled = state.loading || !!state.error || state.page >= totalPages - 1;
    $("debt-limits-page").textContent = state.loading || state.error ? "—" : `Trang ${state.page + 1} / ${totalPages}`;
    if (state.loading || state.error || !rows.length) {
      const title = state.loading ? "Đang tải hạn mức công nợ…" : state.error ? "Không tải được danh sách đại lý"
        : state.rows.length ? "Không tìm thấy đại lý phù hợp" : "Chưa có đại lý để khai báo hạn mức";
      $("debt-limits-summary").textContent = state.loading ? "Đang tải dữ liệu…"
        : state.error ? "Chưa tải được dữ liệu" : "0 đại lý";
      $("debt-limits-rows").innerHTML = `<tr><td colspan="4" class="debt-limits__empty">
        <strong>${title}</strong>
        ${state.error ? `<span>${escapeHtml(state.error)}</span><button class="btn btn--secondary" type="button" data-retry-debt-limits>Thử tải lại</button>` : ""}
      </td></tr>`;
      return;
    }
    const start = state.page * pageSize;
    const end = Math.min(start + pageSize, rows.length);
    $("debt-limits-summary").textContent = `${start + 1}–${end} / ${rows.length.toLocaleString("vi-VN")} đại lý`;
    $("debt-limits-rows").innerHTML = rows.slice(start, end).map(row => `<tr>
      <td><span class="debt-limits__name">${escapeHtml(row.customerName || "Chưa có tên đại lý")}</span><span class="debt-limits__id">ID: ${escapeHtml(row.dealerId)}</span></td>
      <td><span class="debt-limits__amount">${escapeHtml(formatAmount(row.debtLimit))}</span></td>
      <td><span class="debt-limits__days">${escapeHtml(formatDays(row.maxDebtDays))}</span></td>
      <td>${canDeclare ? `<button class="btn btn--secondary" type="button" data-declare-debt-limit="${escapeHtml(row.dealerId)}" aria-label="${escapeHtml(`Khai báo hạn mức cho ${row.customerName || row.dealerId}`)}">Khai báo</button>` : '<span class="debt-limits__missing">Chỉ xem</span>'}</td>
    </tr>`).join("");
  }

  async function loadLimits() {
    if (state.loading) return;
    state.loading = true;
    state.error = "";
    state.rows = [];
    state.page = 0;
    render();
    try {
      const response = await Auth.apiFetch("/finance/debt");
      const result = await response.json().catch(() => null);
      if (!response.ok || !result?.success || !Array.isArray(result.data)
        || result.data.some(row => !row || row.dealerId == null)) {
        throw new Error(result?.message || "Máy chủ không trả về danh sách hạn mức hợp lệ.");
      }
      state.rows = result.data;
    } catch (error) {
      state.error = error.message || "Không thể kết nối máy chủ. Vui lòng thử lại.";
    } finally {
      state.loading = false;
      render();
    }
  }

  function clearDeclarationCheck() {
    $("debt-limit-form-message").hidden = true;
    $("debt-limit-preview").hidden = true;
    $("save-debt-limit").disabled = true;
    form.querySelectorAll('[aria-invalid="true"]').forEach(field => field.removeAttribute("aria-invalid"));
    state.pendingDeclaration = null;
  }

  function openDeclaration(row) {
    if (!canDeclare) return;
    state.selected = row;
    previousFocus = document.activeElement;
    previousOverflow = document.body.style.overflow;
    form.reset();
    clearDeclarationCheck();
    $("debt-limit-modal-dealer").textContent = `${row.customerName || "Đại lý"} · ID ${row.dealerId}`;
    $("debt-limit-current").textContent = `Hạn mức hiện tại: ${formatAmount(row.debtLimit)} · Số ngày nợ: ${formatDays(row.maxDebtDays)}`;
    $("debt-limit-amount").value = amountNumber(row.debtLimit) === null ? "" : String(row.debtLimit);
    $("debt-limit-days").value = Number.isInteger(Number(row.maxDebtDays)) ? String(row.maxDebtDays) : "";
    $("debt-limit-days-help").textContent = `Giá trị hiện tại: ${formatDays(row.maxDebtDays)}. Nhập số ngày nguyên không âm.`;
    modal.hidden = false;
    document.querySelector(".app-shell").inert = true;
    document.body.style.overflow = "hidden";
    $("debt-limit-amount").focus();
  }

  function closeDeclaration() {
    if (modal.hidden) return;
    modal.hidden = true;
    document.querySelector(".app-shell").inert = false;
    document.body.style.overflow = previousOverflow;
    state.selected = null;
    state.pendingDeclaration = null;
    state.saving = false;
    form.reset();
    clearDeclarationCheck();
    previousFocus?.focus();
  }

  form.addEventListener("submit", event => {
    event.preventDefault();
    if (!canDeclare || !state.selected) return;
    clearDeclarationCheck();
    const amountField = $("debt-limit-amount");
    const daysField = $("debt-limit-days");
    const reasonField = $("debt-limit-reason");
    const amount = amountField.value.trim();
    const days = daysField.value.trim();
    const reason = reasonField.value.trim();
    const errors = [];
    if (!amount || amountNumber(amount) === null || !/^\d+(?:\.\d{1,2})?$/.test(amount)) {
      errors.push([amountField, "Nhập hạn mức tiền không âm, tối đa 2 chữ số thập phân."]);
    }
    if (!days || !/^\d+$/.test(days) || !Number.isSafeInteger(Number(days))) {
      errors.push([daysField, "Nhập số ngày nợ tối đa là số nguyên không âm."]);
    }
    if (!reason) errors.push([reasonField, "Vui lòng nhập lý do khai báo hoặc thay đổi hạn mức."]);
    if (errors.length) {
      errors.forEach(([field]) => field.setAttribute("aria-invalid", "true"));
      $("debt-limit-form-message").textContent = errors.map(([, message]) => message).join(" ");
      $("debt-limit-form-message").hidden = false;
      errors[0][0].focus();
      return;
    }
    const values = [
      ["Hạn mức tiền", `${formatAmount(state.selected.debtLimit)} → ${formatAmount(amount)}`],
      ["Số ngày nợ tối đa", `${Number(days).toLocaleString("vi-VN")} ngày`],
      ["Lý do", reason],
    ];
    $("debt-limit-preview-values").innerHTML = values.map(([label, value]) =>
      `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`).join("");
    $("debt-limit-preview").hidden = false;
    state.pendingDeclaration = {
      debtLimit: Number(amount),
      maxDebtDays: Number(days),
      reason,
    };
    $("save-debt-limit").disabled = false;
  });

  $("save-debt-limit").addEventListener("click", async () => {
    if (!canDeclare || !state.selected || !state.pendingDeclaration || state.saving) return;
    const button = $("save-debt-limit");
    state.saving = true;
    button.disabled = true;
    button.textContent = "Đang lưu…";
    $("debt-limit-form-message").hidden = true;

    try {
      const response = await Auth.apiFetch(`/finance/debt/${state.selected.dealerId}/limit`, {
        method: "PUT",
        body: JSON.stringify(state.pendingDeclaration),
      });
      const result = await response.json().catch(() => null);
      if (!response.ok || !result?.success) {
        throw new Error(result?.message || "Không lưu được hạn mức công nợ.");
      }

      const saved = result.data || state.pendingDeclaration;
      const row = state.rows.find(item => String(item.dealerId) === String(state.selected.dealerId));
      if (row) {
        row.debtLimit = saved.debtLimit;
        row.maxDebtDays = saved.maxDebtDays;
      }
      closeDeclaration();
      render();
      showToast(result.message || "Đã cập nhật hạn mức công nợ.", "success");
    } catch (error) {
      $("debt-limit-form-message").textContent = error.message || "Không thể lưu hạn mức công nợ.";
      $("debt-limit-form-message").hidden = false;
    } finally {
      state.saving = false;
      button.disabled = !state.pendingDeclaration;
      button.textContent = "Lưu khai báo";
    }
  });

  form.addEventListener("input", clearDeclarationCheck);
  $("debt-limits-rows").addEventListener("click", event => {
    if (event.target.closest("[data-retry-debt-limits]")) { loadLimits(); return; }
    const button = event.target.closest("[data-declare-debt-limit]");
    if (!button) return;
    const row = state.rows.find(item => String(item.dealerId) === button.dataset.declareDebtLimit);
    if (row) openDeclaration(row);
  });
  $("debt-limits-filters").addEventListener("submit", event => event.preventDefault());
  [$("debt-limit-search"), $("debt-limit-filter")].forEach(control => {
    control.addEventListener(control.tagName === "INPUT" ? "input" : "change", () => { state.page = 0; render(); });
  });
  $("reset-debt-limit-filters").addEventListener("click", () => { $("debt-limits-filters").reset(); state.page = 0; render(); });
  $("reload-debt-limits").addEventListener("click", loadLimits);
  $("debt-limits-prev").addEventListener("click", () => { if (state.page > 0) { state.page--; render(); } });
  $("debt-limits-next").addEventListener("click", () => {
    if ((state.page + 1) * pageSize < filteredRows().length) { state.page++; render(); }
  });
  modal.querySelectorAll("[data-close-debt-limit]").forEach(button => button.addEventListener("click", closeDeclaration));
  modal.addEventListener("click", event => { if (event.target === modal) closeDeclaration(); });
  document.addEventListener("keydown", event => {
    if (modal.hidden) return;
    if (event.key === "Escape") closeDeclaration();
    if (event.key !== "Tab") return;
    const controls = [...modal.querySelectorAll('button:not(:disabled), input:not(:disabled), textarea')]
      .filter(element => element.getClientRects().length);
    const first = controls[0];
    const last = controls[controls.length - 1];
    if ((event.shiftKey && document.activeElement === first) || (!event.shiftKey && document.activeElement === last)) {
      event.preventDefault();
      (event.shiftKey ? last : first)?.focus();
    }
  });
  loadLimits();
window.addEventListener("auth:permissions-changed", () => {
    canDeclare = Auth.hasPermission("FINANCE_MANAGE");
    $("debt-limits-readonly").hidden = canDeclare;
    if (!canDeclare) closeDeclaration();
    render();
  });
})();
