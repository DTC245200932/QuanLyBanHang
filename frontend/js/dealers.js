/**
 * dealers.js — S3-06 Phân công nhân viên kinh doanh theo đại lý và khu vực.
 *
 * Tính năng:
 * - Xem danh sách đại lý (nhân viên chỉ thấy đại lý mình phụ trách)
 * - Thêm / sửa đại lý, gán nhân viên phụ trách
 * - Đổi nhân viên phụ trách đơn lẻ (có ghi lịch sử + lý do)
 * - Chuyển giao hàng loạt khi nhân viên nghỉ (có ghi lịch sử)
 * - Xem lịch sử phân công
 * - Đổi trạng thái hoạt động
 */
(() => {
  if (!Auth.requireLogin()) return;
  if (!Auth.hasAnyRole("ROLE_ADMIN", "ROLE_SALES_MANAGER", "ROLE_SALES")) {
    location.href = "/error?status=403";
    return;
  }

  const isManagerOrAdmin = Auth.hasAnyRole("ROLE_ADMIN", "ROLE_SALES_MANAGER");

  // ── Utilities ──────────────────────────────────────────────────────────────

  const $ = (id) => document.getElementById(id);
  const escapeHtml = (v) =>
    String(v ?? "").replace(/[&<>'"]/g, (c) => ({"&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;",'"':"&quot;"})[c]);

  function formatDate(iso) {
    if (!iso) return "—";
    try {
      return new Date(iso).toLocaleDateString("vi-VN", {
        day: "2-digit", month: "2-digit", year: "numeric",
      });
    } catch { return iso; }
  }

  function showMsg(id, text, isSuccess = false) {
    const el = $(id);
    el.textContent = text;
    el.className = "form-message" + (isSuccess ? " form-message--success" : "");
    el.hidden = !text;
  }

  function toast(message, type = "success") {
    if (typeof Toast !== "undefined") {
      Toast[type]?.(message) || Toast.show?.(message, type);
    }
  }

  // ── State ──────────────────────────────────────────────────────────────────

  const state = {
    page: 0,
    totalPages: 1,
    totalElements: 0,
    dealers: [],
    staffList: [],
    territories: [],
    selectedIds: new Set(),
  };

  // ── Sidebar / nav (shared pattern) ────────────────────────────────────────

  (function initSidebar() {
    const user = Auth.getUser();
    if (!user) return;

    $("user-name").textContent = user.fullName || user.username;
    $("user-role").textContent = MenuConfig.getRoleDisplayName(user.roles?.[0] || "");
    $("user-avatar").textContent = (user.fullName || user.username).trim().charAt(0).toUpperCase();

    const nav = $("sidebar-nav");
    const items = MenuConfig.getMenuForUser(user);
    let lastSection = null;
    nav.innerHTML = items.map((item) => {
      let html = "";
      if (item.section && item.section !== lastSection) {
        lastSection = item.section;
        html += `<div class="sidebar__section-label">${escapeHtml(item.section)}</div>`;
      }
      const active = location.pathname === item.href || location.pathname.startsWith(item.href + "?") ? " sidebar__link--active" : "";
      html += `<a class="sidebar__link${active}" href="${escapeHtml(item.href)}">
        <svg viewBox="0 0 24 24">${item.icon}</svg>
        <span>${escapeHtml(item.label)}</span>
      </a>`;
      return html;
    }).join("");

    const toggle = $("sidebar-toggle");
    const sidebar = $("sidebar");
    const overlay = $("sidebar-overlay");
    const closeBtn = $("sidebar-close");

    function openSidebar() { sidebar.classList.add("sidebar--open"); overlay.classList.add("sidebar-overlay--visible"); }
    function closeSidebar() { sidebar.classList.remove("sidebar--open"); overlay.classList.remove("sidebar-overlay--visible"); }

    toggle?.addEventListener("click", openSidebar);
    closeBtn?.addEventListener("click", closeSidebar);
    overlay?.addEventListener("click", closeSidebar);

    if (user.avatarThumbnailUrl) {
      const img = document.createElement("img");
      Auth.setProtectedImage(img, user.avatarThumbnailUrl).then((ok) => {
        if (ok) {
          const avatar = $("user-avatar");
          avatar.innerHTML = "";
          avatar.appendChild(img);
        }
      });
    }

    ["btn-logout", "btn-logout-topbar"].forEach((id) => {
      $(id)?.addEventListener("click", () => Auth.logout());
    });
  })();

  // ── Load options (staff + territories) ────────────────────────────────────

  async function loadOptions() {
    try {
      const [staffRes, terRes] = await Promise.all([
        Auth.apiFetch("/dealers/staff-options"),
        Auth.apiFetch("/dealers/territory-options"),
      ]);
      const staffData = await staffRes.json();
      const terData = await terRes.json();

      state.staffList = staffData.data || [];
      state.territories = terData.data || [];

      // Populate filter selects
      const staffOpts = state.staffList.map(
        (s) => `<option value="${escapeHtml(s.id)}">${escapeHtml(s.fullName || s.username)}</option>`
      ).join("");
      $("dlr-filter-staff").insertAdjacentHTML("beforeend", staffOpts);

      const terOpts = state.territories.map(
        (t) => `<option value="${escapeHtml(t.id)}">${escapeHtml(t.code + " · " + t.name)}</option>`
      ).join("");
      $("dlr-filter-territory").insertAdjacentHTML("beforeend", terOpts);

      // Populate modal selects
      populateModalSelects();
    } catch (e) {
      console.warn("Không tải được tuỳ chọn:", e.message);
    }
  }

  function populateModalSelects() {
    const staffOpts = state.staffList.map(
      (s) => `<option value="${escapeHtml(s.id)}">${escapeHtml(s.fullName || s.username)}</option>`
    ).join("");
    const terOpts = state.territories.map(
      (t) => `<option value="${escapeHtml(t.id)}">${escapeHtml(t.code + " · " + t.name)}</option>`
    ).join("");

    // Dealer form
    $("dlr-territory").insertAdjacentHTML("beforeend", terOpts);
    $("dlr-staff").insertAdjacentHTML("beforeend", staffOpts);

    // Assign modal
    $("assign-staff").insertAdjacentHTML("beforeend", staffOpts);

    // Bulk modal
    $("bulk-from").insertAdjacentHTML("beforeend", staffOpts);
    $("bulk-to").insertAdjacentHTML("beforeend", staffOpts);
  }

  // ── Load dealers ───────────────────────────────────────────────────────────

  async function loadDealers() {
    $("dlr-tbody").innerHTML = `<tr class="dlr-loading-row"><td colspan="8"><div class="dlr-loading">Đang tải dữ liệu…</div></td></tr>`;
    state.selectedIds.clear();
    $("select-all").checked = false;

    const params = new URLSearchParams({ page: state.page, size: 20 });
    if ($("dlr-search").value.trim()) params.set("search", $("dlr-search").value.trim());
    if ($("dlr-filter-staff").value) params.set("staffId", $("dlr-filter-staff").value);
    if ($("dlr-filter-territory").value) params.set("territoryId", $("dlr-filter-territory").value);
    if ($("dlr-filter-status").value) params.set("status", $("dlr-filter-status").value);

    try {
      const res = await Auth.apiFetch(`/dealers?${params}`);
      const result = await res.json();
      if (!res.ok) throw new Error(result.message || "Không tải được dữ liệu");

      const page = result.data;
      state.dealers = page.content || [];
      state.totalPages = page.totalPages || 1;
      state.totalElements = page.totalElements || 0;

      renderDealers();
      renderPagination();
    } catch (e) {
      $("dlr-tbody").innerHTML = `<tr><td colspan="8" class="dlr-loading">${escapeHtml(e.message || "Không tải được dữ liệu")}</td></tr>`;
    }
  }

  function renderDealers() {
    if (!state.dealers.length) {
      $("dlr-tbody").innerHTML = `
        <tr><td colspan="8">
          <div class="dlr-empty">
            <svg width="48" height="48" viewBox="0 0 24 24">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            <p>Không tìm thấy đại lý phù hợp.</p>
          </div>
        </td></tr>`;
      $("dlr-pagination").hidden = true;
      return;
    }

    $("dlr-tbody").innerHTML = state.dealers.map((d) => {
      const isActive = d.status === "ACTIVE" || d.active;
      const staffName = d.assignedStaff?.fullName || d.assignedStaff?.username || null;
      const initial = staffName ? staffName.trim().charAt(0).toUpperCase() : "";

      const staffCell = staffName
        ? `<span class="staff-pill">
             <span class="staff-pill__avatar">${escapeHtml(initial)}</span>
             ${escapeHtml(staffName)}
           </span>`
        : `<span class="staff-pill staff-pill--none">Chưa phân công</span>`;

      const statusCell = isActive
        ? `<span class="dlr-status-badge dlr-status-badge--active"><span class="dlr-status-badge__dot"></span>Hoạt động</span>`
        : `<span class="dlr-status-badge dlr-status-badge--inactive"><span class="dlr-status-badge__dot"></span>Ngưng</span>`;

      const territoryName = d.territory?.name || d.territoryName || "—";

      // Action buttons — limited for ROLE_SALES (read only)
      const managerActions = isManagerOrAdmin ? `
        <button class="dlr-action-btn dlr-action-btn--transfer" data-reassign="${d.id}" title="Đổi nhân viên phụ trách">
          <svg viewBox="0 0 24 24"><path d="M17 1l4 4-4 4"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/>
          <path d="M7 23l-4-4 4-4"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
        </button>
        <button class="dlr-action-btn ${isActive ? "dlr-action-btn--danger" : "dlr-action-btn--success"}"
          data-toggle="${d.id}" data-active="${isActive}"
          title="${isActive ? "Ngưng hoạt động" : "Kích hoạt lại"}">
          <svg viewBox="0 0 24 24">${isActive
            ? '<circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>'
            : '<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>'
          }</svg>
        </button>` : "";

      return `<tr data-id="${d.id}">
        <td class="col-check">
          <input type="checkbox" class="dlr-row-check" data-id="${d.id}" ${state.selectedIds.has(String(d.id)) ? "checked" : ""} />
        </td>
        <td><span class="dlr-code">${escapeHtml(d.code)}</span></td>
        <td>
          <div class="dlr-name">
            ${escapeHtml(d.name)}
            ${d.contactName ? `<small>${escapeHtml(d.contactName)}${d.contactPhone ? " · " + escapeHtml(d.contactPhone) : ""}</small>` : ""}
          </div>
        </td>
        <td><span class="territory-badge">${escapeHtml(territoryName)}</span></td>
        <td>${staffCell}</td>
        <td><span class="dlr-date">${formatDate(d.assignedAt)}</span></td>
        <td>${statusCell}</td>
        <td>
          <div class="dlr-actions">
            ${isManagerOrAdmin ? `<button class="dlr-action-btn" data-edit="${d.id}" title="Sửa thông tin">
              <svg viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            </button>` : ""}
            <button class="dlr-action-btn dlr-action-btn--history" data-history="${d.id}" title="Xem lịch sử phân công">
              <svg viewBox="0 0 24 24"><path d="M3 3v5h5"/><path d="M3.05 13A9 9 0 1 0 5.18 7.18L3 9"/>
              <path d="M12 7v5l3 2"/></svg>
            </button>
            ${managerActions}
          </div>
        </td>
      </tr>`;
    }).join("");

    // Hide manager-only buttons for ROLE_SALES
    if (!isManagerOrAdmin) {
      $("btn-add-dealer").hidden = true;
      $("btn-bulk-transfer").hidden = true;
    }
  }

  function renderPagination() {
    const pg = $("dlr-pagination");
    if (state.totalPages <= 1 && state.totalElements <= 20) {
      pg.hidden = true;
      return;
    }
    pg.hidden = false;
    $("dlr-page-info").textContent = `${state.totalElements} đại lý · Trang ${state.page + 1}/${state.totalPages}`;

    const controls = $("dlr-page-controls");
    let html = `<button class="dlr-page-btn" id="pg-prev" ${state.page === 0 ? "disabled" : ""}>
      <svg viewBox="0 0 24 24" width="14" height="14"><polyline points="15 18 9 12 15 6"/></svg>
    </button>`;
    for (let i = 0; i < state.totalPages; i++) {
      if (state.totalPages > 7 && Math.abs(i - state.page) > 2 && i !== 0 && i !== state.totalPages - 1) {
        if (i === state.page - 3 || i === state.page + 3) html += `<span style="padding:0 4px;color:var(--ink-500)">…</span>`;
        continue;
      }
      html += `<button class="dlr-page-btn ${i === state.page ? "dlr-page-btn--active" : ""}" data-page="${i}">${i + 1}</button>`;
    }
    html += `<button class="dlr-page-btn" id="pg-next" ${state.page >= state.totalPages - 1 ? "disabled" : ""}>
      <svg viewBox="0 0 24 24" width="14" height="14"><polyline points="9 18 15 12 9 6"/></svg>
    </button>`;
    controls.innerHTML = html;

    controls.addEventListener("click", (e) => {
      const btn = e.target.closest("[data-page]");
      const prev = e.target.closest("#pg-prev");
      const next = e.target.closest("#pg-next");
      if (btn) { state.page = Number(btn.dataset.page); loadDealers(); }
      if (prev && state.page > 0) { state.page--; loadDealers(); }
      if (next && state.page < state.totalPages - 1) { state.page++; loadDealers(); }
    });
  }

  // ── Select all / bulk ──────────────────────────────────────────────────────

  $("select-all").addEventListener("change", (e) => {
    const checked = e.target.checked;
    document.querySelectorAll(".dlr-row-check").forEach((cb) => {
      cb.checked = checked;
      if (checked) state.selectedIds.add(cb.dataset.id);
      else state.selectedIds.delete(cb.dataset.id);
    });
  });

  $("dlr-tbody").addEventListener("change", (e) => {
    const cb = e.target.closest(".dlr-row-check");
    if (!cb) return;
    if (cb.checked) state.selectedIds.add(cb.dataset.id);
    else state.selectedIds.delete(cb.dataset.id);
  });

  // ── Search / filter ────────────────────────────────────────────────────────

  let searchTimer;
  $("dlr-search").addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { state.page = 0; loadDealers(); }, 350);
  });

  ["dlr-filter-staff", "dlr-filter-territory", "dlr-filter-status"].forEach((id) => {
    $(id).addEventListener("change", () => { state.page = 0; loadDealers(); });
  });

  // ── Table row actions ──────────────────────────────────────────────────────

  $("dlr-tbody").addEventListener("click", (e) => {
    const editBtn    = e.target.closest("[data-edit]");
    const reassign   = e.target.closest("[data-reassign]");
    const history    = e.target.closest("[data-history]");
    const toggle     = e.target.closest("[data-toggle]");

    if (editBtn) {
      const dealer = state.dealers.find((d) => String(d.id) === editBtn.dataset.edit);
      openDealerModal(dealer);
    }
    if (reassign) {
      const dealer = state.dealers.find((d) => String(d.id) === reassign.dataset.reassign);
      openAssignModal(dealer);
    }
    if (history) {
      const dealer = state.dealers.find((d) => String(d.id) === history.dataset.history);
      openHistoryModal(dealer);
    }
    if (toggle) {
      const dealer = state.dealers.find((d) => String(d.id) === toggle.dataset.toggle);
      const goActive = toggle.dataset.active === "true" ? false : true;
      openStatusModal(dealer, goActive);
    }
  });

  // ── Dealer modal (add / edit) ──────────────────────────────────────────────

  function openDealerModal(dealer = null) {
    $("dealer-form").reset();
    showMsg("dealer-form-message", "");
    $("dlr-id").value = dealer?.id || "";

    if (dealer) {
      $("dealer-modal-title").textContent = "Sửa đại lý";
      $("dealer-modal-sub").textContent = `Cập nhật thông tin đại lý ${dealer.name}.`;
      $("dlr-code").value = dealer.code || "";
      $("dlr-code").disabled = true; // Code immutable after creation
      $("dlr-name").value = dealer.name || "";
      $("dlr-territory").value = dealer.territory?.id || dealer.territoryId || "";
      $("dlr-contact-name").value = dealer.contactName || "";
      $("dlr-contact-phone").value = dealer.contactPhone || "";
      $("dlr-address").value = dealer.address || "";
      $("dlr-staff").value = dealer.assignedStaff?.id || dealer.assignedStaffId || "";
      $("dlr-notes").value = dealer.notes || "";
    } else {
      $("dealer-modal-title").textContent = "Thêm đại lý";
      $("dealer-modal-sub").textContent = "Khai báo thông tin đại lý và phân công nhân viên phụ trách.";
      $("dlr-code").disabled = false;
    }

    $("dealer-modal").hidden = false;
    $("dlr-name").focus();
  }

  function closeDealerModal() { $("dealer-modal").hidden = true; }

  $("dealer-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const id = $("dlr-id").value;
    const payload = {
      code: $("dlr-code").value.trim(),
      name: $("dlr-name").value.trim(),
      territoryId: $("dlr-territory").value || null,
      contactName: $("dlr-contact-name").value.trim(),
      contactPhone: $("dlr-contact-phone").value.trim(),
      address: $("dlr-address").value.trim(),
      assignedStaffId: $("dlr-staff").value || null,
      notes: $("dlr-notes").value.trim(),
    };
    if (!payload.name) return showMsg("dealer-form-message", "Tên đại lý không được để trống.");
    if (!payload.assignedStaffId) return showMsg("dealer-form-message", "Vui lòng chọn nhân viên phụ trách.");

    const btn = $("btn-save-dealer");
    btn.disabled = true;
    try {
      const res = await Auth.apiFetch(
        id ? `/dealers/${id}` : "/dealers",
        { method: id ? "PUT" : "POST", body: JSON.stringify(payload) }
      );
      const result = await res.json();
      if (!res.ok || !result.success) throw new Error(result.message || "Không lưu được đại lý");
      closeDealerModal();
      toast(id ? "Đã cập nhật đại lý." : "Đã thêm đại lý mới.", "success");
      await loadDealers();
    } catch (err) {
      showMsg("dealer-form-message", err.message);
    } finally {
      btn.disabled = false;
    }
  });

  document.querySelectorAll("[data-close-dealer]").forEach((b) => b.addEventListener("click", closeDealerModal));
  $("btn-add-dealer").addEventListener("click", () => openDealerModal());

  // ── Assign modal (đổi nhân viên phụ trách) ────────────────────────────────

  function openAssignModal(dealer) {
    $("assign-dealer-id").value = dealer.id;
    $("assign-staff").value = dealer.assignedStaff?.id || "";
    $("assign-reason").value = "";
    $("assign-modal-sub").textContent = `Đổi nhân viên phụ trách đại lý "${dealer.name}".`;
    showMsg("assign-form-message", "");
    $("assign-modal").hidden = false;
    $("assign-staff").focus();
  }

  function closeAssignModal() { $("assign-modal").hidden = true; }

  document.querySelectorAll("[data-close-assign]").forEach((b) => b.addEventListener("click", closeAssignModal));

  $("btn-confirm-assign").addEventListener("click", async () => {
    const dealerId = $("assign-dealer-id").value;
    const staffId = $("assign-staff").value;
    const reason = $("assign-reason").value.trim();

    if (!staffId) return showMsg("assign-form-message", "Vui lòng chọn nhân viên phụ trách mới.");
    if (!reason) return showMsg("assign-form-message", "Vui lòng nhập lý do thay đổi.");

    const btn = $("btn-confirm-assign");
    btn.disabled = true;
    try {
      const res = await Auth.apiFetch(`/dealers/${dealerId}/assign`, {
        method: "POST",
        body: JSON.stringify({ staffId, reason }),
      });
      const result = await res.json();
      if (!res.ok || !result.success) throw new Error(result.message || "Không phân công được");
      closeAssignModal();
      toast("Đã đổi nhân viên phụ trách.", "success");
      await loadDealers();
    } catch (err) {
      showMsg("assign-form-message", err.message);
    } finally {
      btn.disabled = false;
    }
  });

  // ── Bulk transfer modal ────────────────────────────────────────────────────

  $("btn-bulk-transfer").addEventListener("click", () => {
    $("bulk-from").value = "";
    $("bulk-to").value = "";
    $("bulk-reason").value = "";
    $("bulk-preview").hidden = true;
    $("btn-confirm-bulk").disabled = true;
    showMsg("bulk-form-message", "");
    $("bulk-modal").hidden = false;
  });

  document.querySelectorAll("[data-close-bulk]").forEach((b) =>
    b.addEventListener("click", () => { $("bulk-modal").hidden = true; })
  );

  $("btn-preview-bulk").addEventListener("click", async () => {
    const fromId = $("bulk-from").value;
    const toId = $("bulk-to").value;
    if (!fromId) return showMsg("bulk-form-message", "Vui lòng chọn nhân viên bàn giao.");
    if (!toId) return showMsg("bulk-form-message", "Vui lòng chọn nhân viên tiếp nhận.");
    if (fromId === toId) return showMsg("bulk-form-message", "Nhân viên bàn giao và tiếp nhận phải khác nhau.");
    showMsg("bulk-form-message", "");

    try {
      const res = await Auth.apiFetch(`/dealers/bulk-transfer/preview?fromStaffId=${fromId}&toStaffId=${toId}`);
      const result = await res.json();
      if (!res.ok || !result.success) throw new Error(result.message || "Không xem trước được");
      const count = result.data?.dealerCount ?? result.data ?? 0;
      const fromName = state.staffList.find((s) => String(s.id) === fromId)?.fullName || "—";
      const toName = state.staffList.find((s) => String(s.id) === toId)?.fullName || "—";
      $("bulk-preview-inner").innerHTML = `
        <strong class="bulk-preview__count">${count} đại lý</strong>
        sẽ được chuyển từ <strong>${escapeHtml(fromName)}</strong>
        sang <strong>${escapeHtml(toName)}</strong>.
        ${count === 0 ? '<br><span style="color:var(--ink-500);font-size:.8125rem">Nhân viên này chưa phụ trách đại lý nào.</span>' : ""}
      `;
      $("bulk-preview").hidden = false;
      $("btn-confirm-bulk").disabled = count === 0;
    } catch (err) {
      showMsg("bulk-form-message", err.message);
    }
  });

  $("btn-confirm-bulk").addEventListener("click", async () => {
    const fromId = $("bulk-from").value;
    const toId = $("bulk-to").value;
    const reason = $("bulk-reason").value.trim();
    if (!reason) return showMsg("bulk-form-message", "Vui lòng nhập lý do bàn giao.");

    const btn = $("btn-confirm-bulk");
    btn.disabled = true;
    try {
      const res = await Auth.apiFetch("/dealers/bulk-transfer", {
        method: "POST",
        body: JSON.stringify({ fromStaffId: fromId, toStaffId: toId, reason }),
      });
      const result = await res.json();
      if (!res.ok || !result.success) throw new Error(result.message || "Không chuyển giao được");
      $("bulk-modal").hidden = true;
      toast(`Đã chuyển giao ${result.data?.transferred ?? ""} đại lý thành công.`, "success");
      await loadDealers();
    } catch (err) {
      showMsg("bulk-form-message", err.message);
      btn.disabled = false;
    }
  });

  // ── History modal ─────────────────────────────────────────────────────────

  async function openHistoryModal(dealer) {
    $("history-modal-title").textContent = "Lịch sử phân công";
    $("history-modal-sub").textContent = `Đại lý: ${dealer.name}`;
    $("history-tbody").innerHTML = `<tr><td colspan="5" class="dlr-loading">Đang tải…</td></tr>`;
    $("history-modal").hidden = false;

    try {
      const res = await Auth.apiFetch(`/dealers/${dealer.id}/history`);
      const result = await res.json();
      if (!res.ok || !result.success) throw new Error(result.message || "Không tải được lịch sử");
      const logs = result.data || [];
      if (!logs.length) {
        $("history-tbody").innerHTML = `<tr><td colspan="5" class="dlr-loading">Chưa có lịch sử phân công.</td></tr>`;
        return;
      }
      $("history-tbody").innerHTML = logs.map((log) => `
        <tr>
          <td class="history-date">${formatDate(log.createdAt)}</td>
          <td>${escapeHtml(log.fromStaffName || "—")}</td>
          <td>${escapeHtml(log.toStaffName || "—")}</td>
          <td class="history-reason">${escapeHtml(log.reason || "—")}</td>
          <td>${escapeHtml(log.performedByName || "—")}</td>
        </tr>`).join("");
    } catch (err) {
      $("history-tbody").innerHTML = `<tr><td colspan="5" class="dlr-loading">${escapeHtml(err.message)}</td></tr>`;
    }
  }

  document.querySelectorAll("[data-close-history]").forEach((b) =>
    b.addEventListener("click", () => { $("history-modal").hidden = true; })
  );

  // ── Status modal ──────────────────────────────────────────────────────────

  function openStatusModal(dealer, activate) {
    $("status-dealer-id").value = dealer.id;
    $("status-target").value = activate ? "ACTIVE" : "INACTIVE";
    $("status-modal-title").textContent = activate ? "Kích hoạt lại đại lý" : "Ngưng hoạt động đại lý";
    $("status-modal-sub").textContent = dealer.name;
    $("status-modal-body").textContent = activate
      ? `Kích hoạt lại đại lý "${dealer.name}"?`
      : `Ngưng hoạt động đại lý "${dealer.name}"? Nhân viên phụ trách vẫn được giữ nguyên.`;
    showMsg("status-form-message", "");
    $("status-modal").hidden = false;
  }

  document.querySelectorAll("[data-close-status]").forEach((b) =>
    b.addEventListener("click", () => { $("status-modal").hidden = true; })
  );

  $("btn-confirm-status").addEventListener("click", async () => {
    const id = $("status-dealer-id").value;
    const target = $("status-target").value;
    const btn = $("btn-confirm-status");
    btn.disabled = true;
    try {
      const res = await Auth.apiFetch(`/dealers/${id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status: target }),
      });
      const result = await res.json();
      if (!res.ok || !result.success) throw new Error(result.message || "Không đổi được trạng thái");
      $("status-modal").hidden = true;
      toast(target === "ACTIVE" ? "Đã kích hoạt lại đại lý." : "Đã ngưng hoạt động đại lý.", "success");
      await loadDealers();
    } catch (err) {
      showMsg("status-form-message", err.message);
    } finally {
      btn.disabled = false;
    }
  });

  // ── Close modals on overlay click ────────────────────────────────────────

  document.querySelectorAll(".modal-overlay").forEach((overlay) => {
    overlay.addEventListener("click", (e) => {
      if (e.target === overlay) {
        overlay.hidden = true;
        // Reset any in-progress forms
        $("dealer-modal").hidden = true;
        $("assign-modal").hidden = true;
        $("bulk-modal").hidden = true;
        $("history-modal").hidden = true;
        $("status-modal").hidden = true;
      }
    });
  });

  // ── Init ──────────────────────────────────────────────────────────────────

  loadOptions().then(() => loadDealers()).catch((e) => {
    $("dlr-tbody").innerHTML = `<tr><td colspan="8" class="dlr-loading">${escapeHtml(e.message)}</td></tr>`;
  });
})();

