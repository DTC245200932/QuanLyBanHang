(() => {
  if (!Auth.requireLogin()) return;

  const user = Auth.getUser();
  const sidebar = document.getElementById("sidebar");
  const nav = document.getElementById("sidebar-nav");
  const overlay = document.createElement("div");
  overlay.className = "sidebar-overlay";
  document.body.appendChild(overlay);

  const escapeHtml = (value) => String(value ?? "").replace(/[&<>'"]/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;",
  })[char]);

  function openSidebar(open) {
    sidebar.classList.toggle("sidebar--open", open);
    overlay.classList.toggle("sidebar-overlay--visible", open);
  }

  document.getElementById("sidebar-toggle")?.addEventListener("click", () => openSidebar(true));
  document.getElementById("sidebar-close")?.addEventListener("click", () => openSidebar(false));
  overlay.addEventListener("click", () => openSidebar(false));

  let lastSection = null;
  nav.innerHTML = MenuConfig.getMenuForUser(user).map((item) => {
    const section = item.section && item.section !== lastSection
      ? `<div class="sidebar__section-label">${escapeHtml(item.section)}</div>` : "";
    if (item.section) lastSection = item.section;
    return `${section}<a class="sidebar__link ${item.id === "dashboard" ? "sidebar__link--active" : ""}"
      href="${item.href}" data-page="${item.id}"><svg viewBox="0 0 24 24">${item.icon}</svg>
      <span>${escapeHtml(item.label)}</span></a>`;
  }).join("");

  document.getElementById("user-name").textContent = user.fullName || user.username;
  document.getElementById("user-avatar").textContent = (user.fullName || user.username || "U").trim().charAt(0).toUpperCase();
  const roleNames = (user.roles || []).map(MenuConfig.getRoleDisplayName);
  const scope = [...(user.warehouses || []), ...(user.territories || [])].map((item) => item.name);
  document.getElementById("user-role").textContent = [roleNames.join(", "), scope.join(", ")].filter(Boolean).join(" · ");
  document.getElementById("welcome-greeting").textContent = `Chào ${user.fullName || user.username}`;
  document.getElementById("current-time").textContent = new Intl.DateTimeFormat("vi-VN", {
    weekday: "long", day: "2-digit", month: "2-digit", year: "numeric",
  }).format(new Date());

  const statsByRole = {
    ROLE_ADMIN: [["Tài khoản", "Quản trị", "7 vai trò nghiệp vụ", "teal"], ["Phân quyền", "Mặc định từ chối", "Kiểm tra tại server", "blue"]],
    ROLE_SALES_MANAGER: [["Kinh doanh", "Đơn hàng", "Theo dõi đội ngũ", "teal"], ["Hiệu quả", "Giá vốn", "Biên lợi nhuận", "blue"]],
    ROLE_WAREHOUSE_MANAGER: [["Kho phụ trách", String((user.warehouses || []).length), "Phê duyệt nhập / xuất", "teal"]],
    ROLE_WAREHOUSE_STAFF: [["Kho làm việc", String((user.warehouses || []).length), "Nhập và xuất hàng", "teal"]],
    ROLE_SALES: [["Địa bàn", String((user.territories || []).length), "Đơn hàng và khách hàng", "teal"]],
    ROLE_ACCOUNTANT: [["Kế toán", "Công nợ", "Báo cáo tài chính", "blue"]],
    ROLE_VIEWER: [["Quyền truy cập", "Chỉ xem", "Không thay đổi dữ liệu", "amber"]],
  };
  const stats = [...new Map((user.roles || []).flatMap((role) => statsByRole[role] || []).map((s) => [s[0], s])).values()];
  document.getElementById("stats-grid").innerHTML = stats.map(([label, value, sub, color]) => `
    <article class="stat-card"><div class="stat-card__icon stat-card__icon--${color}">
      <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="m8 12 2.5 2.5L16 9"/></svg></div>
      <div class="stat-card__label">${escapeHtml(label)}</div><div class="stat-card__value">${escapeHtml(value)}</div>
      <div class="stat-card__sub">${escapeHtml(sub)}</div></article>`).join("");

  function showToast(message, type = "error") {
    let container = document.getElementById("toast-container");
    if (!container) { container = document.createElement("div"); container.id = "toast-container"; document.body.appendChild(container); }
    const toast = document.createElement("div");
    toast.className = `toast toast--${type}`;
    toast.innerHTML = `<span class="toast__message">${escapeHtml(message)}</span>`;
    container.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add("toast--visible"));
    setTimeout(() => { toast.classList.remove("toast--visible"); toast.remove(); }, 4200);
  }

  document.querySelectorAll("#btn-logout, #btn-logout-topbar").forEach((button) => button.addEventListener("click", Auth.logout));
  const modal = document.getElementById("modal-change-password");
  function setModal(open) { modal.hidden = !open; if (open) document.getElementById("cp-current")?.focus(); }
  document.getElementById("btn-change-password")?.addEventListener("click", () => setModal(true));
  document.querySelectorAll("[data-close-modal]").forEach((button) => button.addEventListener("click", () => setModal(false)));

  document.getElementById("form-change-password")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const currentPassword = document.getElementById("cp-current").value;
    const newPassword = document.getElementById("cp-new").value;
    const confirm = document.getElementById("cp-confirm").value;
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(newPassword)) return showToast("Mật khẩu mới cần ít nhất 8 ký tự, có chữ và số.");
    if (newPassword !== confirm) return showToast("Xác nhận mật khẩu chưa khớp.");
    const button = document.getElementById("btn-cp-submit");
    button.disabled = true;
    try {
      const response = await Auth.apiFetch("/auth/change-password", { method: "POST", body: JSON.stringify({ currentPassword, newPassword }) });
      const result = await response.json();
      if (!response.ok || !result.success) throw new Error(result.message || "Không đổi được mật khẩu");
      showToast(result.message, "success");
      setTimeout(() => { Auth.clearSession(); window.location.href = "index.html"; }, 1100);
    } catch (error) { showToast(error.message); }
    finally { button.disabled = false; }
  });

  if (user.mustChangePassword) {
    showToast("Bạn đang dùng mật khẩu tạm. Hãy đổi mật khẩu để tiếp tục.", "warning");
    setModal(true);
  }

  nav.querySelectorAll('a[href^="#"]').forEach((link) => link.addEventListener("click", (event) => {
    event.preventDefault();
    nav.querySelectorAll(".sidebar__link").forEach((item) => item.classList.remove("sidebar__link--active"));
    link.classList.add("sidebar__link--active");
    document.getElementById("page-title").textContent = link.textContent.trim();
    document.getElementById("content-placeholder").querySelector("p").textContent = "Chức năng này sẽ được triển khai ở sprint nghiệp vụ tiếp theo.";
    openSidebar(false);
  }));
})();
