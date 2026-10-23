/**
 * auth.js — Module chung cho xác thực và quản lý phiên (S1-02).
 *
 * Tính năng:
 * - Auto-refresh token khi còn hoạt động
 * - Kiểm tra phiên hết hạn → redirect login
 * - Đăng xuất (gọi API + xoá phiên trình duyệt)
 * - API helper gắn token tự động
 */
const Auth = (() => {
  const API_BASE = "http://localhost:8080/api";

  // Thời gian trước khi access token hết hạn để refresh (5 phút)
  const REFRESH_BEFORE_MS = 5 * 60 * 1000;

  // Interval kiểm tra token (mỗi 60 giây)
  const CHECK_INTERVAL_MS = 60 * 1000;

  let refreshTimer = null;
  let refreshRequest = null;
  let sessionExpiryHandled = false;
  let permissionRequest = null;
  const permissionBindings = [];

  function permissionKey(user) {
    return JSON.stringify([(user?.roles || []).slice().sort(), (user?.permissions || []).slice().sort()]);
  }

  function applyPermissionBindings() {
    for (const binding of permissionBindings) {
      document.querySelectorAll(binding.selector).forEach(element => {
        const allowed = binding.permissions.every(hasPermission);
        if (!allowed) {
          if (!element.hasAttribute("data-permission-hidden")) {
            element.dataset.permissionWasHidden = String(element.hidden);
          }
          element.setAttribute("data-permission-hidden", "");
          element.hidden = true;
        } else if (element.hasAttribute("data-permission-hidden")) {
          element.hidden = element.dataset.permissionWasHidden === "true";
          element.removeAttribute("data-permission-hidden");
        }
      });
    }
  }

  function bindPermissions(selector, ...permissions) {
    permissionBindings.push({ selector, permissions });
    applyPermissionBindings();
  }

  async function syncPermissions() {
    if (!isLoggedIn()) return false;
    if (permissionRequest) return permissionRequest;
    permissionRequest = (async () => {
      try {
        const response = await fetch(`${API_BASE}/auth/permissions`, {
          headers: { Authorization: `Bearer ${getAccessToken()}` },
          cache: "no-store",
        });
        if (!response.ok) return false;
        const result = await response.json();
        if (!result.success || !Array.isArray(result.data?.permissions)) return false;
        const previous = getUser();
        const next = { ...previous, ...result.data };
        sessionStorage.setItem("user", JSON.stringify(next));
        if (permissionKey(previous) !== permissionKey(next)) {
          applyPermissionBindings();
          window.dispatchEvent(new CustomEvent("auth:permissions-changed", { detail: next }));
        }
        return true;
      } catch {
        return false;
      } finally {
        permissionRequest = null;
      }
    })();
    return permissionRequest;
  }

  // ── Token helpers ──

  function getAccessToken() {
    return sessionStorage.getItem("accessToken");
  }

  function getRefreshToken() {
    return sessionStorage.getItem("refreshToken");
  }

  function getUser() {
    try {
      return JSON.parse(sessionStorage.getItem("user"));
    } catch {
      return null;
    }
  }

  function isLoggedIn() {
    return !!getAccessToken() && !!getUser();
  }

  function clearSession() {
    sessionStorage.removeItem("accessToken");
    sessionStorage.removeItem("refreshToken");
    sessionStorage.removeItem("user");
    stopAutoRefresh();
  }

  // ── Parse JWT expiration ──

  function getTokenExpiration(token) {
    try {
      const payload = JSON.parse(atob(token.split(".")[1]));
      return payload.exp * 1000; // Convert to ms
    } catch {
      return 0;
    }
  }

  function isTokenExpiringSoon(token) {
    const expiresAt = getTokenExpiration(token);
    return Date.now() > expiresAt - REFRESH_BEFORE_MS;
  }

  function isTokenExpired(token) {
    const expiresAt = getTokenExpiration(token);
    return Date.now() > expiresAt;
  }

  // ── Auto-refresh (S1-02) ──

  async function refreshAccessToken() {
    if (refreshRequest) return refreshRequest;

    refreshRequest = performTokenRefresh();
    try {
      return await refreshRequest;
    } finally {
      refreshRequest = null;
    }
  }

  // Đồng bộ quyền hiện tại từ DB khi mở trang. Access token có thể vẫn còn hạn
  // nhưng ma trận quyền đã được quản trị viên thay đổi.
  async function refreshPermissions() {
    return syncPermissions();
  }

  async function performTokenRefresh() {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      handleSessionExpired();
      return false;
    }

    try {
      const response = await fetch(`${API_BASE}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });

      if (response.ok) {
        const data = await response.json();
        const previousUser = getUser();
        sessionStorage.setItem("accessToken", data.accessToken);
        sessionStorage.setItem(
          "user",
          JSON.stringify({
            id: data.id,
            username: data.username,
            email: data.email,
            fullName: data.fullName,
            roles: data.roles,
            permissions: data.permissions || [],
            warehouses: data.warehouses || [],
            territories: data.territories || [],
            mustChangePassword: Boolean(data.mustChangePassword),
            avatarUrl: data.avatarUrl || null,
            avatarThumbnailUrl: data.avatarThumbnailUrl || null,
          }),
        );
        const nextUser = getUser();
        if (permissionKey(previousUser) !== permissionKey(nextUser)) {
          applyPermissionBindings();
          window.dispatchEvent(new CustomEvent("auth:permissions-changed", { detail: nextUser }));
        }
        return true;
      }

      // Chỉ kết luận phiên hết hạn khi server từ chối refresh token.
      if (response.status >= 400 && response.status < 500) {
        handleSessionExpired();
        return false;
      }

      console.warn("Token refresh failed because the server is unavailable");
      return null;
    } catch {
      // Lỗi mạng không đồng nghĩa phiên đã hết hạn; giữ phiên để thử lại sau.
      console.warn("Token refresh failed, will retry");
      return null;
    }
  }

  function startAutoRefresh() {
    stopAutoRefresh();
    refreshTimer = setInterval(async () => {
      const token = getAccessToken();
      if (token && isTokenExpiringSoon(token)) {
        await refreshAccessToken();
      }
      await syncPermissions();
    }, Math.min(CHECK_INTERVAL_MS, 15000));
  }

  function stopAutoRefresh() {
    if (refreshTimer) {
      clearInterval(refreshTimer);
      refreshTimer = null;
    }
  }

  // ── Session expiry (S1-02) ──

  function handleSessionExpired() {
    if (sessionExpiryHandled) return;
    sessionExpiryHandled = true;
    clearSession();
    // Store message to show on login page
    sessionStorage.setItem(
      "sessionMessage",
      "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
    );
    window.location.href = getLoginPageUrl();
  }

  // ── Logout (S1-02) ──

  async function logout() {
    const token = getAccessToken();
    const refreshToken = getRefreshToken();
    if (token || refreshToken) {
      try {
        const headers = { "Content-Type": "application/json" };
        if (token) headers.Authorization = `Bearer ${token}`;

        await fetch(`${API_BASE}/auth/logout`, {
          method: "POST",
          headers,
          body: JSON.stringify({ refreshToken }),
        });
      } catch {
        // Ignore errors — still clear local session
      }
    }

    clearSession();
    window.location.href = getLoginPageUrl();
  }

  // ── Authenticated fetch helper ──

  async function apiFetch(url, options = {}) {
    let token = getAccessToken();

    // Check if token needs refresh first
    if (token && isTokenExpiringSoon(token)) {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        token = getAccessToken();
      } else if (refreshed === false) {
        throw new Error("Session expired");
      }
    }

    if (!token) {
      handleSessionExpired();
      throw new Error("No access token");
    }

    const isFormData = typeof FormData !== "undefined"
      && options.body instanceof FormData;
    const headers = {
      Authorization: `Bearer ${token}`,
      ...options.headers,
    };
    if (!isFormData && !headers["Content-Type"]) {
      headers["Content-Type"] = "application/json";
    }

    const requestUrl = url.startsWith("http")
      ? url
      : url.startsWith("/api/")
        ? `${API_BASE.replace(/\/api$/, "")}${url}`
        : `${API_BASE}${url}`;
    const response = await fetch(
      requestUrl,
      { ...options, headers },
    );

    // Cập nhật giao diện cả khi token chưa hết hạn nhưng quyền vừa bị thu hồi.
    if (response.status === 403) await syncPermissions();

    // Handle 401 — try refresh once
    if (response.status === 401) {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        const retryToken = getAccessToken();
        const retryResponse = await fetch(
          requestUrl,
          {
            ...options,
            headers: { ...headers, Authorization: `Bearer ${retryToken}` },
          },
        );
        return retryResponse;
      } else if (refreshed === false) {
        throw new Error("Session expired");
      }

      throw new Error("Không thể gia hạn phiên do mất kết nối máy chủ");
    }

    if (response.status === 403) {
      const message = await response.clone().json().catch(() => null);
      sessionStorage.setItem(
        "errorMessage",
        message?.message || "Bạn không có quyền truy cập chức năng này.",
      );
      window.location.href = getErrorPageUrl(403);
    }

    return response;
  }

  async function setProtectedImage(image, url) {
    if (!image || !url) return false;
    try {
      const response = await apiFetch(url);
      if (!response.ok) return false;
      const blob = await response.blob();
      if (!blob.type.startsWith("image/")) return false;
      const previousUrl = image.dataset.objectUrl;
      const objectUrl = URL.createObjectURL(blob);
      image.src = objectUrl;
      image.dataset.objectUrl = objectUrl;
      if (previousUrl) URL.revokeObjectURL(previousUrl);
      return true;
    } catch {
      return false;
    }
  }

  // ── Guard: require login ──

  function requireLogin() {
    if (!isLoggedIn()) {
      window.location.href = getLoginPageUrl();
      return false;
    }

    const token = getAccessToken();
    if (isTokenExpired(token)) {
      // Refresh sẽ tự chuyển về đăng nhập nếu server xác nhận phiên đã hết hạn.
      refreshAccessToken();
    }

    startAutoRefresh();
    return true;
  }

  // ── Guard: require specific role ──

  function hasRole(role) {
    const user = getUser();
    return user?.roles?.includes(role) || false;
  }

  function hasAnyRole(...roles) {
    const user = getUser();
    return roles.some((r) => user?.roles?.includes(r));
  }

  function hasPermission(permission) {
    const user = getUser();
    return user?.roles?.includes("ROLE_ADMIN")
      || user?.permissions?.includes(permission)
      || false;
  }

  function hasAnyPermission(...permissions) {
    return permissions.some((permission) => hasPermission(permission));
  }

  function getErrorPageUrl(status) {
    return `/error?status=${status}`;
  }

  function getLoginPageUrl() {
    return "/login";
  }

  if (typeof window !== "undefined") {
    window.addEventListener("focus", syncPermissions);
    if (typeof MutationObserver !== "undefined" && typeof document !== "undefined") {
      new MutationObserver(applyPermissionBindings).observe(document.documentElement, { childList: true, subtree: true });
    }
  }

  // ── Public API ──
  return {
    getAccessToken,
    getRefreshToken,
    getUser,
    isLoggedIn,
    logout,
    apiFetch,
    setProtectedImage,
    requireLogin,
    refreshPermissions,
    syncPermissions,
    bindPermissions,
    get ready() { return syncPermissions(); },
    hasRole,
    hasAnyRole,
    hasPermission,
    hasAnyPermission,
    clearSession,
    API_BASE,
  };
})();
