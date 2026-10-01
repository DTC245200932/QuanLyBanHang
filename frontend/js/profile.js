(() => {
  if (!Auth.requireLogin()) return;
  const $ = (id) => document.getElementById(id);
  const roleName = (role) => MenuConfig.getRoleDisplayName(role);

  function showMessage(message, error = false) {
    const element = $("profile-message");
    element.textContent = message;
    element.className = `profile-message${error ? " profile-message--error" : ""}`;
    element.hidden = !message;
  }

  function names(items) {
    return items?.length ? items.map((item) => item.name).join(", ") : "Không áp dụng";
  }

  async function loadProfile() {
    const response = await Auth.apiFetch("/users/profile");
    const result = await response.json().catch(() => ({}));
    if (!response.ok || !result.success) throw new Error(result.message || "Không tải được hồ sơ.");
    const profile = result.data;
    $("profile-avatar").textContent = (profile.fullName || profile.username || "U").trim().charAt(0).toUpperCase();
    $("profile-account").textContent = `${profile.fullName || profile.username} · ${profile.email}`;
    $("profile-username").value = profile.username || "";
    $("profile-email").value = profile.email || "";
    $("profile-full-name").value = profile.fullName || "";
    $("profile-phone").value = profile.phone || "";
    $("profile-roles").textContent = (profile.roles || []).map(roleName).join(", ") || "—";
    $("profile-warehouses").textContent = names(profile.warehouses);
    $("profile-territories").textContent = names(profile.territories);
  }

  $("profile-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const fullName = $("profile-full-name").value.trim();
    const phone = $("profile-phone").value.trim();
    if (!/^(0|\+84|84)[35789]\d{8}$/.test(phone)) {
      showMessage("Số điện thoại không đúng định dạng Việt Nam.", true);
      return;
    }
    const button = $("profile-save");
    button.disabled = true;
    showMessage("");
    try {
      const response = await Auth.apiFetch("/users/profile", {
        method: "PUT",
        body: JSON.stringify({ fullName, phone }),
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok || !result.success) throw new Error(result.message || "Không cập nhật được hồ sơ.");
      const cached = Auth.getUser() || {};
      localStorage.setItem("user", JSON.stringify({ ...cached, fullName, phone }));
      showMessage(result.message || "Đã cập nhật hồ sơ.");
      await loadProfile();
    } catch (error) {
      showMessage(error.message || "Không thể kết nối máy chủ.", true);
    } finally {
      button.disabled = false;
    }
  });

  loadProfile().catch((error) => showMessage(error.message, true));
})();
