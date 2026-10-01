/**
 * auth.js — Module chung cho xác thực và quản lý phiên (S1-02).
 *
 * Tính năng:
 * - Auto-refresh token khi còn hoạt động
 * - Kiểm tra phiên hết hạn → redirect login
 * - Đăng xuất (gọi API + xoá localStorage)
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

  // ── Token helpers ──

  function getAccessToken() {
    return localStorage.getItem("accessToken");
  }

  function getRefreshToken() {
    return localStorage.getItem("refreshToken");
  }

  function getUser() {
    try {
      return JSON.parse(localStorage.getItem("user"));
    } catch {
      return null;
    }
  }

  function isLoggedIn() {
    return !!getAccessToken() && !!getUser();
  }

  function clearSession() {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");
    localStorage.removeItem("user");
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
        localStorage.setItem("accessToken", data.accessToken);
        localStorage.setItem(
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
    }, CHECK_INTERVAL_MS);
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

    const response = await fetch(
      url.startsWith("http") ? url : `${API_BASE}${url}`,
      { ...options, headers },
    );

    // Handle 401 — try refresh once
    if (response.status === 401) {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        const retryToken = getAccessToken();
        const retryResponse = await fetch(
          url.startsWith("http") ? url : `${API_BASE}${url}`,
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
    return getUser()?.permissions?.includes(permission) || false;
  }

  function getErrorPageUrl(status) {
    const inSubdir = window.location.pathname.includes("/admin/") || window.location.pathname.includes("/warehouse/");
    return `${inSubdir ? "../" : ""}error.html?status=${status}`;
  }

  function getLoginPageUrl() {
    const inSubdir = window.location.pathname.includes("/admin/") || window.location.pathname.includes("/warehouse/");
    return inSubdir ? "../index.html" : "index.html";
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
    hasRole,
    hasAnyRole,
    hasPermission,
    clearSession,
    API_BASE,
  };
})();
