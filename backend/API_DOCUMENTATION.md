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
| POST | `/auth/logout` | Đã đăng nhập | Thu hồi access token và refresh token hiện tại |
| POST | `/auth/change-password` | Đã đăng nhập | Đổi mật khẩu và thu hồi mọi phiên khác |
| GET | `/auth/me` | Đã đăng nhập | Hồ sơ, vai trò và phạm vi làm việc hiện tại |

### Đăng nhập

```json
POST /api/auth/login
{
  "username": "admin",
  "password": "admin123"
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
| PUT | `/admin/permissions/{role}` | Khai báo/cập nhật quyền cho vai trò (chặn gán giá vốn ngoài Sales Manager, chặn gán sửa tồn kho cho Sales) |
| POST | `/admin/permissions/reset` | Khôi phục ma trận quyền 7 vai trò về mặc định ban đầu |

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

## Kho & Tồn kho

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| POST | `/warehouse/adjust` | `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN` | Điều chỉnh tồn kho; Nhân viên kinh doanh và Thủ kho bị cấm |
| POST | `/warehouse/import` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN` | Nhập kho |
| POST | `/warehouse/export` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN` | Xuất kho |
| GET | `/warehouse/inventory` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN`, `ROLE_VIEWER` | Xem danh sách tồn kho |

## Tài chính (Bảo mật giá vốn & biên lợi nhuận)

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| GET | `/finance/cost-price` | `ROLE_SALES_MANAGER` | Xem giá vốn sản phẩm — chỉ Quản lý kinh doanh; Admin, Thủ kho, Sales, Kế toán đều bị từ chối |
| GET | `/finance/profit-margin` | `ROLE_SALES_MANAGER` | Xem biên lợi nhuận — chỉ Quản lý kinh doanh |
| GET | `/finance/debt` | `ROLE_ADMIN`, `ROLE_SALES_MANAGER`, `ROLE_ACCOUNTANT` | Xem công nợ khách hàng |

## Bảy vai trò

- `ROLE_ADMIN`: quản trị tài khoản và hệ thống; không mặc nhiên xem giá vốn.
- `ROLE_SALES_MANAGER`: quản lý kinh doanh; vai trò duy nhất xem giá vốn và biên lợi nhuận.
- `ROLE_WAREHOUSE_MANAGER`: quản lý, điều chỉnh và phê duyệt kho.
- `ROLE_SALES`: tạo đơn, quản lý khách hàng và địa bàn; không sửa được tồn kho, không xem giá vốn.
- `ROLE_WAREHOUSE_STAFF`: nhập/xuất tại kho được gán; không sửa điều chỉnh tồn kho, không xem giá vốn.
- `ROLE_ACCOUNTANT`: công nợ và báo cáo tài chính, không xem giá vốn.
- `ROLE_VIEWER`: chỉ xem báo cáo được phép.

Mọi chức năng đều kiểm quyền ở tầng server. Endpoint chưa được khai báo hoặc không đủ quyền bị từ chối mặc định (401 khi chưa đăng nhập, 403 khi thiếu quyền).

## Chạy local

1. Tạo MySQL hoặc để ứng dụng tự tạo schema `quanlybanhang`.
2. Cấu hình SMTP thật nếu cần nhận email kích hoạt/reset.
3. Chạy `backend\\mvnw.cmd spring-boot:run`.
4. Phục vụ thư mục repo qua HTTP, ví dụ cổng `5500`, rồi mở `/frontend/index.html`.

Tài khoản khởi tạo cho môi trường phát triển: `admin / admin123`. Hãy đổi ngay sau lần đăng nhập đầu tiên.
