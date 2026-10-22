# Báo cáo Kiểm tra Ma trận Phân quyền & Trạng thái Hoàn thành: S4-10

Theo quy tắc trong `AGENTS.md`:
> "After completing a feature, always check whether it is connected to the permission matrix in both the backend and frontend.
> and after that, make a .md to show that if backend or fe are done, and if they are properly connected"

---

## 1. Thông tin tính năng: S4-10 (Cổng tự đặt hàng dành cho Đại lý)
- **User Story**: Là Đại lý, tôi muốn tự đặt hàng trên cổng dành cho đại lý, để đặt hàng lúc nửa đêm mà không phải chờ nhân viên tới.
- **Tiêu chí chấp nhận**:
  1. Đăng nhập bằng tài khoản đại lý, chỉ thấy sản phẩm và giá thuộc nhóm khách hàng của mình (DAI_LY_CAP_1, DAI_LY_CAP_2, KHACH_LE).
  2. Thêm hàng vào giỏ, xem tổng tiền và công nợ còn lại trước khi gửi đơn.
  3. Cảnh báo vượt hạn mức công nợ hoặc có nợ quá hạn.
  4. Đơn gửi lên luôn ở trạng thái **Chờ duyệt** (RECORDED), do nhân viên phụ trách xác nhận.
  5. Giao diện mobile-first dùng thuận tiện trên điện thoại (touch steppers 44px, sticky bottom checkout bar, responsive 360px).
- **Phạm vi yêu cầu**: Chỉ Frontend (Frontend only).

---

## 2. Trạng thái Hoàn thành (Done Status)

| Phần | Trạng thái | Chi tiết |
|---|---|---|
| **Frontend** | **Hoàn thành (DONE)** | - Tạo trang [dealer-portal.html](file:///d:/QuanLyBanHang/frontend/dealer-portal.html)<br>- Stylesheet [dealer-portal.css](file:///d:/QuanLyBanHang/frontend/css/dealer-portal.css) tối ưu mobile & touch targets<br>- Logic xử lý [dealer-portal.js](file:///d:/QuanLyBanHang/frontend/js/dealer-portal.js): lọc sản phẩm theo bảng giá hiệu lực của nhóm khách hàng đại lý, quản lý giỏ hàng in-memory (không lưu vào browser storage), tính công nợ còn lại thời gian thực, gửi đơn hàng `POST /api/orders` và hiển thị trạng thái Chờ duyệt.<br>- Cấu hình route `/dealer-portal` trong [serve_frontend.py](file:///d:/QuanLyBanHang/serve_frontend.py)<br>- Bộ kiểm thử tự động [dealer-portal.test.cjs](file:///d:/QuanLyBanHang/tests/dealer-portal.test.cjs) (4/4 tests pass). |
| **Backend** | **Đã có API nghiệp vụ, cần mở rộng Role đại lý** | - Các API đã sẵn sàng: `GET /api/dealers/active`, `GET /api/dealers/{id}/debt`, `GET /api/products?active=true`, `GET /api/price-lists/effective`, `POST /api/orders`.<br>- Hiện tại enum `ERole` ở backend có 7 vai trò nội bộ: `ROLE_ADMIN`, `ROLE_SALES_MANAGER`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_SALES`, `ROLE_WAREHOUSE_STAFF`, `ROLE_ACCOUNTANT`, `ROLE_VIEWER`. Backend chưa tạo riêng `ROLE_DEALER` và tài khoản người dùng gắn trực tiếp với `dealerId` (như đã ghi nhận trong tài liệu audit sprint 4). |

---

## 3. Kiểm tra Kết nối Ma trận Phân quyền (Permission Matrix Connection)

### 3.1. Phía Frontend
- **Mã quyền kết nối**: `ORDER_CREATE` (Tạo đơn hàng).
- **Menu Config**:
  - File [menu-config.js](file:///d:/QuanLyBanHang/frontend/js/menu-config.js) đã khai báo:
    ```javascript
    {
      id: "dealer-portal",
      section: "Kinh doanh",
      label: "Cổng đại lý",
      href: "/dealer-portal",
      permission: "ORDER_CREATE",
      roles: ["ROLE_ADMIN", "ROLE_SALES_MANAGER", "ROLE_SALES", "ROLE_DEALER"],
    }
    ```
  - Bổ sung tên hiển thị vai trò trong `getRoleDisplayName`: `ROLE_DEALER: "Đại lý"`.
- **Hoạt động với Ma trận Phân quyền**:
  - Khi quản trị viên mở modal **Ma trận phân quyền** tại `/admin/users` ([admin-users.js](file:///d:/QuanLyBanHang/frontend/js/admin-users.js)), các quyền được quản lý theo catalog quyền của hệ thống.
  - Khi một vai trò bị gỡ quyền `ORDER_CREATE`, menu `dealer-portal` tự động bị ẩn đối với vai trò đó thông qua `MenuConfig.getMenuForUser(user)`.
- **Bảo mật truy cập trang**:
  - `dealer-portal.js` kiểm tra phiên đăng nhập thông qua `Auth.requireLogin()`.

### 3.2. Phía Backend
- **Quyền bảo vệ API**:
  - `POST /api/orders` trong `OrderController.java`:
    ```java
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    ```
  - Trong `RolePermissionConfig.java`, quyền `ORDER_CREATE` mặc định thuộc về:
    - `ROLE_ADMIN`
    - `ROLE_SALES_MANAGER`
    - `ROLE_SALES`
- **Khuyến nghị cho Backend khi triển khai phần Backend của S4-10**:
  - Thêm `ROLE_DEALER` vào enum `ERole`.
  - Cấu hình quyền `ORDER_CREATE` cho `ROLE_DEALER` trong `RolePermissionConfig.defaultPermissions()`.
  - Thêm `ROLE_DEALER` vào `@PreAuthorize` của `POST /api/orders` hoặc endpoint chuyên biệt `/api/portal/orders`.

---

## 4. Kết quả Kiểm thử (Test Execution Results)
- **Node Test Runner**: `48 pass, 0 fail` (toàn bộ 100% tests đạt)
  - Bao gồm 4 bài test mới trong `tests/dealer-portal.test.cjs`:
    1. *Cổng đại lý chỉ hiển thị sản phẩm và giá thuộc bảng giá nhóm khách hàng của mình*
    2. *Thêm sản phẩm vào giỏ, xem tổng tiền và tính toán công nợ còn lại trước khi gửi đơn*
    3. *Cảnh báo khi đơn hàng làm vượt hạn mức công nợ còn lại của đại lý*
    4. *Đơn gửi lên luôn ở trạng thái Chờ duyệt (RECORDED), gửi đúng payload và reset giỏ hàng*
- **Python Server Test**: `5 pass, 0 fail` (`tests/test_frontend_server.py`)
- **Kiểm tra lưu trữ**: Không sử dụng `localStorage`/`sessionStorage` cho dữ liệu nghiệp vụ (tuân thủ nghiêm ngặt rule bảo mật trong `SKILL_backend.md`).
