(() => {
  // ── Configuration ──
  const API_BASE = "http://localhost:8080/api";

  // ── DOM refs ──
  const form = document.querySelector(".login-form");
  const usernameInput = document.querySelector("#username");
  const passwordInput = document.querySelector("#password");
  const passwordToggle = document.querySelector("[data-password-toggle]");
  const submitButton = document.querySelector(".submit-button");

  // ── Password visibility toggle ──
  if (passwordInput && passwordToggle) {
    passwordToggle.addEventListener("click", () => {
      const shouldShowPassword = passwordInput.type === "password";

      passwordInput.type = shouldShowPassword ? "text" : "password";
      passwordToggle.classList.toggle("is-visible", shouldShowPassword);
      passwordToggle.setAttribute("aria-pressed", String(shouldShowPassword));
      passwordToggle.setAttribute(
        "aria-label",
        shouldShowPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu",
      );

      passwordInput.focus({ preventScroll: true });
    });
  }

  // ── Toast notification system ──
  function createToastContainer() {
    let container = document.getElementById("toast-container");
    if (!container) {
      container = document.createElement("div");
      container.id = "toast-container";
      document.body.appendChild(container);
    }
    return container;
  }

  function showToast(message, type = "error") {
    const container = createToastContainer();

    const toast = document.createElement("div");
    toast.className = `toast toast--${type}`;
    toast.setAttribute("role", "alert");

    const icons = {
      error: `<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="m15 9-6 6M9 9l6 6"/></svg>`,
      success: `<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/></svg>`,
    };

    toast.innerHTML = `
      <span class="toast__icon">${icons[type] || icons.error}</span>
      <span class="toast__message">${message}</span>
    `;

    container.appendChild(toast);

    // Trigger enter animation
    requestAnimationFrame(() => toast.classList.add("toast--visible"));

    // Auto dismiss
    setTimeout(() => {
      toast.classList.remove("toast--visible");
      toast.classList.add("toast--exit");
      toast.addEventListener("animationend", () => toast.remove(), {
        once: true,
      });
    }, 4000);
  }

  // ── Field validation styling ──
  function setFieldError(input, hasError) {
    const group = input.closest(".field-group");
    if (group) {
      group.classList.toggle("field-group--error", hasError);
    }
  }

  function clearAllErrors() {
    document.querySelectorAll(".field-group--error").forEach((el) => {
      el.classList.remove("field-group--error");
    });
  }

  // ── Loading state ──
  function setLoading(isLoading) {
    if (!submitButton) return;

    submitButton.disabled = isLoading;
    submitButton.classList.toggle("submit-button--loading", isLoading);

    const label = submitButton.querySelector("span");
    const arrow = submitButton.querySelector("svg");

    if (isLoading) {
      if (label) label.textContent = "Đang xử lý...";
      if (arrow) arrow.style.display = "none";
    } else {
      if (label) label.textContent = "Đăng nhập";
      if (arrow) arrow.style.display = "";
    }
  }

  // ── Token & user storage ──
  function saveAuthData(data) {
    localStorage.setItem("accessToken", data.accessToken);
    localStorage.setItem("refreshToken", data.refreshToken);
    localStorage.setItem(
      "user",
      JSON.stringify({
        id: data.id,
        username: data.username,
        email: data.email,
        fullName: data.fullName,
        roles: data.roles,
      }),
    );
  }

  // ── Check if already logged in ──
  function checkExistingSession() {
    const token = localStorage.getItem("accessToken");
    if (token) {
      // Optionally redirect to dashboard
      // window.location.href = "dashboard.html";
    }
  }

  // ── Login handler ──
  async function handleLogin(e) {
    e.preventDefault();
    clearAllErrors();

    const username = usernameInput.value.trim();
    const password = passwordInput.value;

    // Client-side validation
    let hasError = false;

    if (!username) {
      setFieldError(usernameInput, true);
      hasError = true;
    }

    if (!password) {
      setFieldError(passwordInput, true);
      hasError = true;
    }

    if (hasError) {
      showToast("Vui lòng nhập đầy đủ thông tin đăng nhập.", "error");
      return;
    }

    setLoading(true);

    try {
      const response = await fetch(`${API_BASE}/auth/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ username, password }),
      });

      if (response.ok) {
        const data = await response.json();
        saveAuthData(data);

        showToast(
          `Đăng nhập thành công! Chào mừng ${data.fullName}.`,
          "success",
        );

        // Redirect to dashboard after a brief delay
        setTimeout(() => {
          window.location.href = "dashboard.html";
        }, 1000);
      } else if (response.status === 401) {
        showToast("Tên đăng nhập hoặc mật khẩu không đúng.", "error");
        setFieldError(usernameInput, true);
        setFieldError(passwordInput, true);

        // Shake the form for visual feedback
        form.classList.add("login-form--shake");
        form.addEventListener(
          "animationend",
          () => form.classList.remove("login-form--shake"),
          { once: true },
        );
      } else {
        const errorData = await response.json().catch(() => null);
        const message =
          errorData?.message || "Đã xảy ra lỗi. Vui lòng thử lại.";
        showToast(message, "error");
      }
    } catch (err) {
      console.error("Login error:", err);
      showToast(
        "Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại kết nối.",
        "error",
      );
    } finally {
      setLoading(false);
    }
  }

  // ── Clear field error on input ──
  if (usernameInput) {
    usernameInput.addEventListener("input", () =>
      setFieldError(usernameInput, false),
    );
  }
  if (passwordInput) {
    passwordInput.addEventListener("input", () =>
      setFieldError(passwordInput, false),
    );
  }

  // ── Init ──
  checkExistingSession();

  if (form) {
    form.addEventListener("submit", handleLogin);
  }
})();
