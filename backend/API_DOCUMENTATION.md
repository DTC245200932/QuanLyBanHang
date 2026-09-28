# 📋 API Documentation - Authentication & Authorization Backend

## Base URL
```
http://localhost:8080/api
```

## 🔓 Endpoints Công Khai (Không cần đăng nhập)

---

### 1. Đăng nhập
```
POST /api/auth/login
```

**Request Body:**
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "id": 1,
  "username": "admin",
  "email": "admin@quanlybanhang.com",
  "fullName": "Quản trị viên",
  "roles": ["ROLE_ADMIN"]
}
```

**Response (401 Unauthorized) — Sai thông tin:**
```json
{
  "success": false,
  "message": "Tên đăng nhập hoặc mật khẩu không đúng"
}
```
> ⚠️ Message luôn chung — không tiết lộ tài khoản có tồn tại hay không.

**Response (423 Locked) — Bị khoá tạm (5 lần sai liên tiếp):**
```json
{
  "success": false,
  "message": "Tài khoản bị khoá tạm thời do đăng nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.",
  "data": {
    "remainingMinutes": 15,
    "type": "TEMPORARY_LOCK"
  }
}
```

**Response (403 Forbidden) — Bị admin khoá:**
```json
{
  "success": false,
  "message": "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.",
  "data": {
    "type": "ADMIN_LOCK"
  }
}
```

---

### 2. Đăng ký
```
POST /api/auth/register
```

**Request Body:**
```json
{
  "username": "nhanvien1",
  "email": "nv1@email.com",
  "password": "123456",
  "fullName": "Nguyen Van A",
  "phone": "0901234567",
  "roles": ["ROLE_SALES"]
}
```

> **Lưu ý:** `roles` là optional. Nếu không truyền → mặc định `ROLE_SALES`.
> Các role hợp lệ: `ROLE_ADMIN`, `ROLE_SALES_MANAGER`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_SALES`, `ROLE_WAREHOUSE_STAFF`, `ROLE_ACCOUNTANT`, `ROLE_VIEWER`

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Đăng ký tài khoản thành công"
}
```

---

### 3. Refresh Token
```
POST /api/auth/refresh
```

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...(token mới)",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...(giữ nguyên)",
  "tokenType": "Bearer",
  "id": 1,
  "username": "admin",
  "email": "admin@quanlybanhang.com",
  "fullName": "Quản trị viên",
  "roles": ["ROLE_ADMIN"]
}
```

---

### 4. Quên mật khẩu
```
POST /api/auth/forgot-password
```

**Request Body:**
```json
{
  "email": "user@email.com"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Email đặt lại mật khẩu đã được gửi. Vui lòng kiểm tra hộp thư."
}
```

---

### 5. Đặt lại mật khẩu
```
POST /api/auth/reset-password
```

**Request Body:**
```json
{
  "token": "uuid-token-tu-email",
  "newPassword": "matkhaumoi123"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập lại."
}
```

---

## 🔒 Endpoints Cần Đăng Nhập

> **Header bắt buộc:** `Authorization: Bearer <accessToken>`

### 6. Lấy thông tin user hiện tại + permissions
```
GET /api/test/user
```

**Response (200 OK):**
```json
{
  "message": "Xin chào Quản trị viên",
  "username": "admin",
  "email": "admin@quanlybanhang.com",
  "roles": ["ROLE_ADMIN"],
  "permissions": [
    "CUSTOMER_CREATE", "CUSTOMER_EDIT", "CUSTOMER_VIEW",
    "FINANCE_MANAGE", "FINANCE_VIEW_COST_PRICE", "FINANCE_VIEW_DEBT", "FINANCE_VIEW_PROFIT_MARGIN",
    "ORDER_APPROVE", "ORDER_CANCEL", "ORDER_CREATE", "ORDER_EDIT", "ORDER_VIEW",
    "PRODUCT_CREATE", "PRODUCT_DELETE", "PRODUCT_EDIT", "PRODUCT_VIEW",
    "REPORT_FINANCE", "REPORT_INVENTORY", "REPORT_SALES",
    "USER_ASSIGN_ROLE", "USER_CREATE", "USER_EDIT", "USER_LOCK", "USER_VIEW",
    "WAREHOUSE_ADJUST", "WAREHOUSE_APPROVE", "WAREHOUSE_EXPORT", "WAREHOUSE_IMPORT", "WAREHOUSE_VIEW"
  ]
}
```

### 7. Xem ma trận phân quyền (ADMIN only)
```
GET /api/test/permissions
```
> Trả về mapping role → permissions cho toàn bộ 7 vai trò.

### 8. Test quyền Manager
```
GET /api/test/manager
```
> Chỉ user có role `ROLE_SALES_MANAGER`, `ROLE_WAREHOUSE_MANAGER`, hoặc `ROLE_ADMIN`

### 9. Test quyền Admin
```
GET /api/test/admin
```
> Chỉ user có role `ROLE_ADMIN`

---

## 📊 Phân quyền — 7 Vai trò Nghiệp vụ

| Role | Mô tả | Xem giá vốn |
|------|--------|:------------:|
| `ROLE_ADMIN` | Quản trị hệ thống — toàn quyền | ✅ |
| `ROLE_SALES_MANAGER` | Quản lý kinh doanh — đơn hàng, sản phẩm, giá vốn | ✅ |
| `ROLE_WAREHOUSE_MANAGER` | Quản lý kho — phê duyệt nhập/xuất | ❌ |
| `ROLE_SALES` | Nhân viên kinh doanh — tạo đơn, quản lý khách | ❌ |
| `ROLE_WAREHOUSE_STAFF` | Thủ kho — nhập/xuất trên kho được gán | ❌ |
| `ROLE_ACCOUNTANT` | Kế toán — báo cáo tài chính, công nợ | ❌ |
| `ROLE_VIEWER` | Chỉ xem — xem báo cáo, không thao tác | ❌ |

## 🔒 Bảo mật Login

| Cơ chế | Chi tiết |
|--------|----------|
| Khoá tạm | 5 lần sai liên tiếp → khoá 15 phút |
| Message chung | Sai password hay sai username đều trả `"Tên đăng nhập hoặc mật khẩu không đúng"` |
| Khoá admin | `active=false` → không đăng nhập được, phiên bị thu hồi |

## 🗄️ Tài khoản mặc định

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123` | ADMIN |

> ⚠️ **Lưu ý:** Đổi mật khẩu admin sau khi triển khai!
