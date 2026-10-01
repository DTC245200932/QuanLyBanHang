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
- Python 3 để phục vụ frontend. Có thể dùng một HTTP server khác nếu muốn.

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
```

Các giá trị mặc định khác nằm trong [application.properties](backend/src/main/resources/application.properties).

## 3. Chạy backend

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
python -m http.server 5500
```

Sau đó mở:

```text
http://localhost:5500/frontend/index.html
```

Không mở trực tiếp `frontend/index.html` bằng địa chỉ `file://`; frontend cần chạy qua HTTP để gọi API và xử lý CORS đúng cách.

## 5. Tài khoản đăng nhập

### Tài khoản luôn được tạo

Ở lần chạy đầu tiên, backend tự tạo:

| Tên đăng nhập | Mật khẩu | Vai trò |
|---|---|---|
| `admin` | `admin123` | Quản trị hệ thống |

Đây là tài khoản phát triển. Hãy đổi mật khẩu ngay sau khi đăng nhập và không dùng thông tin này ở production.

### Tài khoản demo theo vai trò

Các tài khoản sau mặc định **không được tạo**. Muốn dùng chúng để kiểm thử menu và phân quyền, đặt biến môi trường trước khi chạy backend:

Nếu dùng PowerShell:

```powershell
$env:APP_SEED_DEMO_USERS="true"
cd D:\GitHub\QuanLyBanHang\backend
.\mvnw.cmd spring-boot:run
```

Nếu dùng Command Prompt (`cmd.exe`):

```cmd
set "APP_SEED_DEMO_USERS=true"
set "SPRING_DATASOURCE_USERNAME=root"
set "SPRING_DATASOURCE_PASSWORD=MAT_KHAU_MYSQL_CUA_BAN"
cd /d D:\GitHub\QuanLyBanHang\backend
mvnw.cmd spring-boot:run
```

Backend sẽ tạo các tài khoản chưa tồn tại:

| Tên đăng nhập | Mật khẩu | Vai trò | Phạm vi mẫu |
|---|---|---|---|
| `sales.manager` | `SalesManager123` | Quản lý kinh doanh | Xem giá vốn và biên lợi nhuận |
| `sales.demo` | `SalesDemo123` | Nhân viên kinh doanh | Một địa bàn mẫu |
| `warehouse.demo` | `Warehouse123` | Thủ kho | Một kho mẫu |
| `accountant.demo` | `Accountant123` | Kế toán | Công nợ và báo cáo tài chính |
| `viewer.demo` | `ViewerDemo123` | Chỉ xem | Báo cáo được phép |

Chỉ bật dữ liệu demo trên máy phát triển. Để tắt cho những lần chạy sau:

```powershell
Remove-Item Env:APP_SEED_DEMO_USERS -ErrorAction SilentlyContinue
```

Việc tắt biến môi trường không xóa những tài khoản đã tạo. Nếu database đã có tài khoản cùng tên, initializer giữ nguyên tài khoản và mật khẩu hiện tại.

Kiểm tra các tài khoản đã được tạo trong MySQL:

```sql
USE quanlybanhang;
SELECT username, full_name, active FROM users;
```

## 6. Ví dụ gọi API đăng nhập

PowerShell:

```powershell
$loginBody = @{
  username = "admin"
  password = "admin123"
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

Các URL frontend trong email mặc định dùng cổng `5500`:

```text
http://localhost:5500/frontend/index.html
http://localhost:5500/frontend/reset-password.html
```

## 8. Chạy kiểm thử

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
