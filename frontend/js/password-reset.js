(() => {
  const API = "http://localhost:8080/api/auth";
  const message = document.getElementById("message");
  const show = (text, ok) => { message.hidden = false; message.className = `message message--${ok ? "success" : "error"}`; message.textContent = text; };
  const submit = async (form, path, body) => {
    const button = form.querySelector("button"); button.disabled = true;
    try {
      const response = await fetch(`${API}/${path}`, { method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify(body()) });
      const result = await response.json().catch(() => ({}));
      show(result.message || (response.ok ? "Yêu cầu đã được xử lý." : "Không thể xử lý yêu cầu."), response.ok);
      if (response.ok) form.reset();
    } catch { show("Không thể kết nối máy chủ. Vui lòng thử lại.", false); }
    finally { button.disabled = false; }
  };
  document.getElementById("forgot-form")?.addEventListener("submit", (e) => { e.preventDefault(); submit(e.currentTarget, "forgot-password", () => ({email:document.getElementById("email").value.trim()})); });
  document.getElementById("reset-form")?.addEventListener("submit", (e) => {
    e.preventDefault(); const password = document.getElementById("new-password").value;
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(password)) return show("Mật khẩu cần ít nhất 8 ký tự, có chữ và số.", false);
    if (password !== document.getElementById("confirm-password").value) return show("Hai mật khẩu chưa khớp.", false);
    submit(e.currentTarget, "reset-password", () => ({token:new URLSearchParams(location.search).get("token") || "", newPassword:password}));
  });
})();
