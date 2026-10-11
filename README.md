# SaleStock — Quản lý bán hàng và kho

Ứng dụng gồm:

- Backend Spring Boot 3, Java 17, Spring Security, JWT, JPA và MySQL.
- Frontend HTML, CSS và JavaScript thuần.
- Đăng nhập, gia hạn phiên, quên/đổi mật khẩu và phân quyền theo 7 vai trò.
- Quản trị tài khoản, gán kho/địa bàn và khóa hoặc mở khóa nhân sự.

## 1. Yêu cầu

Cài sẵn:

- Java Development Kit 17 trở lên.
- MySQL 8.
- Python 3.9 trở lên để phục vụ frontend. Có thể dùng một HTTP server khác nếu muốn.

Kiểm tra nhanh trong PowerShell:

```powershell
java -version
mysql --version
python --version
```

Repo đã có Maven Wrapper nên không bắt buộc cài Maven riêng.

## 2. Chuẩn bị MySQL

Backend có thể tự tạo database `quanlybanhang` nếu tài khoản MySQL có quyền tạo database. Có thể tạo trước bằng MySQL:

```sql
CREATE DATABASE quanlybanhang
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

Không nên lưu mật khẩu MySQL thật vào Git. Trước khi chạy backend, đặt thông tin kết nối bằng biến môi trường trong PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/quanlybanhang?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true"
$env:SPRING_DATASOURCE_USERNAME="root"
$env:SPRING_DATASOURCE_PASSWORD="mat-khau-mysql-cua-ban"
$env:APP_JWT_SECRET="chuoi-bi-mat-rieng-cua-moi-truong-it-nhat-32-ky-tu"
$env:APP_BOOTSTRAP_ADMIN_PASSWORD="mat-khau-admin-ban-tu-chon"
$env:APP_BOOTSTRAP_ADMIN_EMAIL="email-quan-tri-thuc-te-cua-ban"
```

`APP_JWT_SECRET` là bắt buộc và phải là chuỗi bí mật riêng cho từng môi trường.
Không commit giá trị thật của biến này vào repository.

Các giá trị mặc định khác nằm trong [application.properties](backend/src/main/resources/application.properties).

## 3. Chạy backend

Nếu các bí mật từng được commit, cần xoay vòng chúng vì xóa khỏi file không loại bỏ chúng khỏi lịch sử Git.

Mở PowerShell tại thư mục repo:

```powershell
cd D:\GitHub\QuanLyBanHang\backend
.\mvnw.cmd spring-boot:run
```

Khi thấy ứng dụng khởi động thành công, API hoạt động tại:

```text
http://localhost:8080/api
```

Kiểm tra endpoint công khai:

```powershell
Invoke-RestMethod http://localhost:8080/api/test/public
```

Để dừng backend, nhấn `Ctrl+C` trong cửa sổ PowerShell đang chạy.

## 4. Chạy frontend

Giữ backend hoạt động. Mở một cửa sổ PowerShell khác tại thư mục gốc của repo:

```powershell
cd D:\GitHub\QuanLyBanHang
python serve_frontend.py
```

Sau đó mở:

```text
http://localhost:5500/login
```

Có thể chạy `.\run_frontend.ps1` từ PowerShell. Mở `/dashboard`, `/products`,
`/profile`, `/admin/users`, `/admin/audit-logs` hoặc `/warehouse/units`.
Các đường dẫn cũ `/frontend/*.html` tự chuyển sang đường dẫn mới.
Server chỉ phục vụ thư mục frontend; không mở trang bằng `file://`.

## 5. Tài khoản đăng nhập

### Tài khoản luôn được tạo

Ở lần chạy đầu tiên, backend tự tạo tài khoản quản trị bằng mật khẩu trong
`APP_BOOTSTRAP_ADMIN_PASSWORD`:

| Tên đăng nhập | Mật khẩu | Vai trò |
|---|---|---|
| `admin` | Giá trị `APP_BOOTSTRAP_ADMIN_PASSWORD` | Quản trị hệ thống |

Dùng mật khẩu riêng cho mỗi môi trường và cập nhật hồ sơ quản trị bằng thông tin thực tế.

### Dữ liệu nghiệp vụ

Backend không tự chèn kho, địa bàn, nhóm hàng, sản phẩm, đại lý, bảng giá,
chiết khấu hay tài khoản demo. Database mới chỉ có vai trò hệ thống và tài khoản
`admin`. `APP_BOOTSTRAP_ADMIN_EMAIL` phải là email quản trị thực tế; không đặt
số điện thoại giả cho tài khoản ban đầu. Database đã có `admin` không cần khai
báo lại hai biến bootstrap và giữ nguyên hồ sơ/mật khẩu hiện có.

Quản trị viên tạo nhân sự, khai báo/nhập danh mục thực tế và thiết lập bảng giá,
chiết khấu qua các trang quản lý. Kho và địa bàn phải được cấu hình bằng dữ liệu
thực trong database trước khi gán nhân sự hoặc ghi phiếu kho. Chưa có dữ liệu
thì trang hiển thị trạng thái trống hoặc thông báo thiếu cấu hình.

Các biến `APP_SEED_DEMO_USERS` và `APP_DEMO_USER_PASSWORD` không còn được dùng.
Thay đổi này không tự xoá các bản ghi đã tạo ở phiên bản trước: cần đối chiếu
lịch sử giao dịch và sao lưu trước khi dọn database của nhóm. Doanh số giả trong
cột `products.revenue` cũ đã được bỏ qua; API tính từ đơn hàng đã ghi sổ.

## 6. Ví dụ gọi API đăng nhập

PowerShell:

```powershell
$loginBody = @{
  username = "admin"
  password = "mat-khau-admin-da-cau-hinh"
} | ConvertTo-Json

$session = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body $loginBody

$session.accessToken
```

Gọi API cần đăng nhập:

```powershell
$headers = @{ Authorization = "Bearer $($session.accessToken)" }
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/auth/me" `
  -Headers $headers
```

Đăng xuất và thu hồi cả access token lẫn refresh token:

```powershell
$logoutBody = @{ refreshToken = $session.refreshToken } | ConvertTo-Json
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/logout" `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $logoutBody
```

Xem toàn bộ endpoint và request mẫu trong [backend/API_DOCUMENTATION.md](backend/API_DOCUMENTATION.md).

## 7. Email kích hoạt và đặt lại mật khẩu

Muốn gửi email thật, cấu hình SMTP trước khi chạy backend. Ví dụ với Gmail App Password:

```powershell
$env:SPRING_MAIL_USERNAME="email-cua-ban@gmail.com"
$env:SPRING_MAIL_PASSWORD="gmail-app-password"
```

Không dùng mật khẩu Gmail thông thường và không commit App Password vào repo.
Nếu chưa cấu hình hai biến trên, hệ thống không thể gửi email đặt lại mật khẩu thật.

Các URL frontend trong email mặc định dùng cổng `5500`:

```text
http://localhost:5500/login
http://localhost:5500/reset-password
```

## 8. Chạy kiểm thử

Báo cáo kiểm tra Sprint 3 hiện tại: [ngày 11/10/2026](docs/SPRINT_3_AUDIT_20261011.md).
Báo cáo có trạng thái frontend/backend của từng task, các lỗi còn lại trước
nghiệm thu và bằng chứng chạy API/giao diện.

Kiểm tra đường dẫn frontend và menu theo vai trò từ thư mục repo:

```powershell
python -m unittest discover -s tests -p test_frontend_server.py
node --test tests/menu-config.test.cjs
```

Xem các đường dẫn trang trong [docs/FRONTEND_ROUTES.md](docs/FRONTEND_ROUTES.md).

```powershell
cd D:\GitHub\QuanLyBanHang\backend
.\mvnw.cmd test
```

Kiểm tra cú pháp JavaScript từ thư mục gốc nếu máy đã cài Node.js:

```powershell
Get-ChildItem frontend\js -Filter *.js | ForEach-Object {
  node --check $_.FullName
}
```

## 9. Lỗi thường gặp

### CMD báo `mysql is not recognized`

MySQL Server có thể đã được cài và chạy nhưng thư mục chứa `mysql.exe` chưa nằm trong `PATH`. Với vị trí cài đặt MySQL 8.0 mặc định trên Windows, có thể chạy trực tiếp:

```cmd
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
```

Hoặc thêm thư mục MySQL tạm thời vào `PATH` của cửa sổ CMD hiện tại:

```cmd
set "PATH=%PATH%;C:\Program Files\MySQL\MySQL Server 8.0\bin"
mysql --version
mysql -u root -p
```

Thiết lập bằng `set` chỉ tồn tại trong cửa sổ CMD hiện tại. Muốn dùng lâu dài, thêm thư mục sau vào biến môi trường `Path` của Windows rồi mở lại CMD:

```text
C:\Program Files\MySQL\MySQL Server 8.0\bin
```

Kiểm tra dịch vụ MySQL từ PowerShell:

```powershell
Get-Service MySQL80
Get-NetTCPConnection -State Listen -LocalPort 3306
```

### Backend không kết nối được MySQL

- Kiểm tra dịch vụ MySQL đang chạy.
- Kiểm tra username, password và cổng `3306`.
- Kiểm tra tài khoản MySQL có quyền dùng hoặc tạo database `quanlybanhang`.

### Frontend báo `Failed to fetch`

- Đảm bảo backend đang chạy ở cổng `8080`.
- Đảm bảo frontend được mở từ `http://localhost:5500`, không phải `file://`.
- Nếu dùng cổng khác, thêm origin đó vào `app.cors.allowed-origins`.

### Không nhận được email

- Kiểm tra cấu hình SMTP và App Password.
- Kiểm tra log backend để xem lỗi gửi mail.
- Email không tồn tại vẫn nhận cùng thông báo trên giao diện nhằm tránh lộ tài khoản.

### Tài khoản bị khóa tạm

Sau 5 lần nhập sai liên tiếp, tài khoản bị khóa trong 15 phút. Đây là hành vi bảo mật dự kiến.
