# 📋 API Documentation - Authentication Backend

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

**Response (401 Unauthorized):**
```json
{
  "success": false,
  "message": "Tên đăng nhập hoặc mật khẩu không đúng"
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
  "roles": ["ROLE_EMPLOYEE"]
}
```

> **Lưu ý:** `roles` là optional. Nếu không truyền → mặc định `ROLE_EMPLOYEE`.
> Các role hợp lệ: `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_EMPLOYEE`

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Đăng ký tài khoản thành công"
}
```

**Response (400 Bad Request) - Username trùng:**
```json
{
  "success": false,
  "message": "Tên đăng nhập đã được sử dụng"
}
```

**Response (400 Bad Request) - Validation lỗi:**
```json
{
  "success": false,
  "message": "Dữ liệu không hợp lệ",
  "data": {
    "username": "Tên đăng nhập phải từ 3-50 ký tự",
    "password": "Mật khẩu phải từ 6-100 ký tự"
  }
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

### 6. Lấy thông tin user hiện tại
```
GET /api/test/user
```

**Response (200 OK):**
```json
{
  "message": "Xin chào Quản trị viên",
  "username": "admin",
  "email": "admin@quanlybanhang.com"
}
```

### 7. Test quyền Manager
```
GET /api/test/manager
```
> Chỉ user có role `ROLE_MANAGER` hoặc `ROLE_ADMIN`

### 8. Test quyền Admin
```
GET /api/test/admin
```
> Chỉ user có role `ROLE_ADMIN`

---

## 🔑 Cách Frontend Sử Dụng

### Lưu token sau khi đăng nhập:
```javascript
// Sau khi gọi API login thành công
const response = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' })
});
const data = await response.json();

// Lưu token
localStorage.setItem('accessToken', data.accessToken);
localStorage.setItem('refreshToken', data.refreshToken);
localStorage.setItem('user', JSON.stringify({
  id: data.id,
  username: data.username,
  email: data.email,
  fullName: data.fullName,
  roles: data.roles
}));
```

### Gọi API có xác thực:
```javascript
const token = localStorage.getItem('accessToken');
const response = await fetch('/api/test/user', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
});
```

### Refresh token khi hết hạn:
```javascript
// Khi nhận 401 Unauthorized → gọi refresh
const refreshToken = localStorage.getItem('refreshToken');
const response = await fetch('/api/auth/refresh', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ refreshToken })
});
const data = await response.json();
localStorage.setItem('accessToken', data.accessToken);
```

---

## 📊 Phân quyền (Roles)

| Role | Quyền |
|------|-------|
| `ROLE_ADMIN` | Toàn quyền - quản lý hệ thống, user, sản phẩm, đơn hàng |
| `ROLE_MANAGER` | Quản lý nhân viên, sản phẩm, đơn hàng |
| `ROLE_EMPLOYEE` | Bán hàng, xem sản phẩm |

## 🗄️ Tài khoản mặc định

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123` | ADMIN |

> ⚠️ **Lưu ý:** Đổi mật khẩu admin sau khi triển khai!
