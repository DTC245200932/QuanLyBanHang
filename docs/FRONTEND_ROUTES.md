# Chạy frontend và đường dẫn trang

Từ thư mục repo, chạy một trong hai lệnh:

```powershell
.\run_frontend.ps1
# Hoặc:
python serve_frontend.py
```

Mở [trang đăng nhập](http://localhost:5500/login) hoặc
[trang tổng quan](http://localhost:5500/dashboard).
Backend vẫn chạy ở cổng 8080 theo cấu hình trong README.

| Trang | Đường dẫn |
|---|---|
| Đăng nhập | `/login` |
| Tổng quan | `/dashboard` |
| Hồ sơ | `/profile` |
| Sản phẩm | `/products` |
| Nhóm hàng | `/product-groups` |
| Bảng giá | `/price-lists` |
| Chiết khấu sản lượng | `/discount-policies` |
| Hồ sơ, tìm kiếm và khoá đại lý | `/dealers` |
| Tạo đơn hàng | `/create-order` |
| Hạn mức công nợ | `/dashboard?view=debt-limits` |
| Nhà cung cấp | `/suppliers` |
| Đơn vị tính | `/warehouse/units?tab=units` |
| Lập chứng từ | `/warehouse/units?tab=receipts` |
| Sổ cái kho | `/warehouse/units?tab=ledger` |
| Tồn kho | `/warehouse/units?tab=inventory` |
| Tài khoản nhân sự | `/admin/users` |
| Nhật ký thao tác | `/admin/audit-logs` |
| Quên mật khẩu | `/forgot-password` |
| Đặt lại mật khẩu | `/reset-password?token=...` |
| Trang lỗi | `/error?status=403` |

`/` mở trang đăng nhập. Các liên kết cũ như `/frontend/dashboard.html`,
`/admin/users.html` hoặc `/frontend/reset-password.html?token=...` được chuyển
sang đường dẫn mới; tham số truy vấn được giữ lại.

Server dùng thư viện chuẩn Python, không cần cài thêm package. Khi thêm trang,
cập nhật `ROUTES` trong `serve_frontend.py` và menu trong `frontend/js/menu-config.js`.
Không dùng `python -m http.server` cho các đường dẫn ngắn này.

## Kiểm tra

```powershell
python -m unittest discover -s tests -p test_frontend_server.py
node --test tests/menu-config.test.cjs
```

Đã kiểm tra đăng nhập chuyển sang `/dashboard`, điều hướng tới trang quản trị,
đường dẫn kho có tham số `tab`, vai trò chỉ xem và bố cục 360px.
Ảnh minh họa: [desktop](evidence/ui-clean-dashboard.jpg),
[mobile](evidence/ui-clean-dashboard-mobile.jpg).
