# SaleStock API — Sprint 1

Base URL: `http://localhost:8080/api`

Mọi response nghiệp vụ dùng cấu trúc `{ "success": boolean, "message": string, "data": any }`.

## Xác thực

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| POST | `/auth/login` | Công khai | Đăng nhập; khóa 15 phút ở lần sai thứ 5 |
| POST | `/auth/refresh` | Công khai | Gia hạn access token bằng refresh token |
| POST | `/auth/forgot-password` | Công khai | Gửi liên kết đặt lại có hạn 30 phút |
| POST | `/auth/reset-password` | Công khai | Đặt lại mật khẩu; token dùng một lần |
| POST | `/auth/logout` | Có access hoặc refresh token | Thu hồi ngay các token hiện có; vẫn xử lý khi access token vừa hết hạn |
| POST | `/auth/change-password` | Đã đăng nhập | Đổi mật khẩu và thu hồi mọi phiên khác |
| GET | `/auth/me` | Đã đăng nhập | Hồ sơ, vai trò và phạm vi làm việc hiện tại |

### Đăng nhập

```json
POST /api/auth/login
{
  "username": "admin",
  "password": "<APP_BOOTSTRAP_ADMIN_PASSWORD>"
}
```

Sai tên đăng nhập hoặc mật khẩu luôn trả `401` cùng thông báo `Tên đăng nhập hoặc mật khẩu không đúng`. Lần sai thứ 5 trả `423` với `data.type = TEMPORARY_LOCK`.

Response thành công gồm `accessToken`, `refreshToken`, thông tin người dùng, `roles`, `permissions`, `warehouses`, `territories` và `mustChangePassword`.

### Đăng xuất

```json
POST /api/auth/logout
Authorization: Bearer <access-token>
{
  "refreshToken": "<refresh-token>"
}
```

### Đổi mật khẩu

```json
POST /api/auth/change-password
Authorization: Bearer <access-token>
{
  "currentPassword": "TempPass123",
  "newPassword": "MatKhauMoi123"
}
```

Mật khẩu mới tối thiểu 8 ký tự, có chữ và số. `tokenVersion` của tài khoản được tăng nên mọi JWT cũ mất hiệu lực ngay phía server.

## Quản trị tài khoản

Tất cả endpoint dưới `/admin/**` yêu cầu `ROLE_ADMIN` và được kiểm tra tại server.

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/admin/users` | Tìm kiếm, lọc và phân trang (mặc định 20) |
| GET | `/admin/users/{id}` | Chi tiết tài khoản |
| POST | `/admin/users` | Tạo tài khoản, sinh mật khẩu tạm và gửi email |
| PUT | `/admin/users/{id}` | Sửa hồ sơ, nhiều vai trò, kho và địa bàn |
| POST | `/admin/users/{id}/lock` | Khóa, thu hồi phiên và cảnh báo bàn giao đại lý |
| POST | `/admin/users/{id}/unlock` | Mở khóa |
| GET | `/admin/assignment-options` | Danh sách 7 vai trò, kho và địa bàn |
| GET | `/admin/permissions` | Ma trận quyền của 7 vai trò |

### Tìm kiếm

```text
GET /api/admin/users?search=an&role=ROLE_SALES&active=true&page=0&size=20
```

`search` khớp tên, tài khoản hoặc số điện thoại. `size` tối đa 100.

### Tạo/cập nhật

```json
{
  "username": "nguyenvana",
  "email": "a@company.vn",
  "fullName": "Nguyễn Văn A",
  "phone": "0901234567",
  "roles": ["ROLE_SALES", "ROLE_VIEWER"],
  "warehouseIds": [],
  "territoryIds": [1]
}
```

Người có `ROLE_WAREHOUSE_MANAGER` hoặc `ROLE_WAREHOUSE_STAFF` bắt buộc có ít nhất một `warehouseId`. Quản trị viên không thể tự bỏ `ROLE_ADMIN` hoặc tự khóa tài khoản đang dùng.

### Khóa tài khoản

```json
POST /api/admin/users/15/lock
{
  "reason": "Nhân sự đã nghỉ việc"
}
```

Lý do là bắt buộc. Response trả `assignedDealerCount`; các đại lý liên quan được đánh dấu `handoverRequired = true`.

## Bảy vai trò

- `ROLE_ADMIN`: quản trị tài khoản và hệ thống; không mặc nhiên xem giá vốn.
- `ROLE_SALES_MANAGER`: quản lý kinh doanh; vai trò duy nhất xem giá vốn và biên lợi nhuận.
- `ROLE_WAREHOUSE_MANAGER`: quản lý, điều chỉnh và phê duyệt kho.
- `ROLE_SALES`: tạo đơn, quản lý khách hàng và địa bàn.
- `ROLE_WAREHOUSE_STAFF`: nhập/xuất tại kho được gán.
- `ROLE_ACCOUNTANT`: công nợ và báo cáo tài chính, không xem giá vốn.
- `ROLE_VIEWER`: chỉ xem báo cáo được phép.

Endpoint chưa được khai báo bị từ chối mặc định. Các route giá vốn/biên lợi nhuận dưới `/finance/cost-price/**` và `/finance/profit-margin/**` chỉ nhận `ROLE_SALES_MANAGER`.

## Chạy local

1. Tạo MySQL hoặc để ứng dụng tự tạo schema `quanlybanhang`.
2. Cấu hình SMTP thật nếu cần nhận email kích hoạt/reset.
3. Chạy `backend\\mvnw.cmd spring-boot:run`.
4. Phục vụ thư mục repo qua HTTP, ví dụ cổng `5500`, rồi mở `/frontend/index.html`.

Tài khoản khởi tạo cho môi trường phát triển là `admin`; mật khẩu lấy từ
`APP_BOOTSTRAP_ADMIN_PASSWORD`. Hãy đổi ngay sau lần đăng nhập đầu tiên.
