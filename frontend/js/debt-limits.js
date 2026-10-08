/** S3-05: read real limits and validate declarations; saving awaits a complete backend contract. */
(() => {
  "use strict";
  if (new URLSearchParams(location.search).get("view") !== "debt-limits") return;
  if (!Auth.requireLogin()) return;
  if (!MenuConfig.getMenuForUser(Auth.getUser()).some(item => item.id === "debt-limits")) {
    location.href = "/error?status=403";
    return;
  }

  const canDeclare = Auth.hasRole("ROLE_ACCOUNTANT") || Auth.hasRole("ROLE_SALES_MANAGER");
  const $ = id => document.getElementById(id);
  const escapeHtml = value => String(value ?? "").replace(/[&<>'"]/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;",
  })[char]);
  const currency = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 2 });
  const pageSize = 10;
  const state = { rows: [], page: 0, loading: false, error: "", selected: null };
  const modal = $("debt-limit-modal");
  const form = $("debt-limit-form");
  let previousFocus;
  let previousOverflow;

  document.title = "Hạn mức công nợ | SaleStock";
  $("page-title").textContent = "Hạn mức công nợ";
  $("welcome-card").hidden = true;
  document.querySelector(".dashboard-work").hidden = true;
  $("debt-limits-view").hidden = false;
  $("debt-limits-readonly").hidden = canDeclare;
  document.querySelectorAll(".sidebar__link").forEach(link => {
    const active = link.dataset.page === "debt-limits";
    link.classList.toggle("sidebar__link--active", active);
    if (active) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  });

  function amountNumber(value) {
    if (value == null || String(value).trim() === "") return null;
    const number = Number(value);
    return Number.isFinite(number) && number >= 0 ? number : null;
  }

  function formatAmount(value) {
    const number = amountNumber(value);
    return number === null ? "Chưa có dữ liệu" : currency.format(number);
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
      <td><span class="debt-limits__missing">Chưa có dữ liệu</span></td>
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
      // debtAmount is currently a backend placeholder; do not turn it into a risk metric.
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
    form.querySelectorAll('[aria-invalid="true"]').forEach(field => field.removeAttribute("aria-invalid"));
  }

  function openDeclaration(row) {
    if (!canDeclare) return;
    state.selected = row;
    previousFocus = document.activeElement;
    previousOverflow = document.body.style.overflow;
    form.reset();
    clearDeclarationCheck();
    $("debt-limit-modal-dealer").textContent = `${row.customerName || "Đại lý"} · ID ${row.dealerId}`;
    $("debt-limit-current").textContent = `Hạn mức tiền hiện tại: ${formatAmount(row.debtLimit)}`;
    $("debt-limit-amount").value = amountNumber(row.debtLimit) === null ? "" : String(row.debtLimit);
    // No debt-day value is returned by the existing API; never substitute a guessed default.
    $("debt-limit-days").value = "";
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
    // Do not POST/PUT an incomplete declaration: the current DTO accepts only debtLimit.
    // Saving and audit of reason/debt days must be connected once the backend supports S3-05.
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
})();
