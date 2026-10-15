# Bảng Theo Dõi Trạng Thái Tính Năng (Feature Status)

| Mã User Story | Tên Tính Năng | Backend | Frontend | Kết Nối Ma Trận Phân Quyền (Permission Matrix) | Ghi Chú |
|---|---|---|---|---|---|
| **S5-01** | Theo dõi trạng thái đơn hàng của Đại lý (Dealer Order Tracking & Delivery Status) | **Hoàn thành (DONE)** | *Chưa thực hiện (PENDING)* | **Đã kết nối đầy đủ (Backend Connected)** | Phạm vi hiện tại chỉ làm BE theo yêu cầu. ROLE_DEALER đã được tích hợp vào `RolePermissionConfig`, bảo vệ RLS ở tầng service/controller. |

---

## Chi tiết kết nối Ma trận phân quyền (Permission Matrix Connection)

### 1. Phía Backend (Đã kết nối đầy đủ - PROPERLY CONNECTED)
- **Role mới**: Bổ sung `ROLE_DEALER` ("Đại lý") vào enum `ERole`.
- **Cấu hình quyền mặc định (`RolePermissionConfig`)**:
  - `ROLE_DEALER` được cấp các quyền mặc định: `EPermission.ORDER_VIEW`, `EPermission.ORDER_CREATE`.
  - Thiết lập cơ chế ràng buộc bảo mật (`ROLE_RESTRICTIONS`): Đại lý tuyệt đối không thể được gán các quyền quản lý kho (`INVENTORY_ADJUST`, `INVENTORY_VIEW`, `INVENTORY_IMPORT`, `INVENTORY_EXPORT`), không có quyền xem giá vốn / biên lợi nhuận (`COST_PRICE_VIEW`, `PROFIT_MARGIN_VIEW`).
  - Ma trận quyền động (`/api/admin/permissions`) tự động nhận diện `ROLE_DEALER`, cho phép quản trị viên xem và quản lý trong ma trận.
- **Kiểm soát bảo mật mức hàng (Row-Level Security & Data Isolation)**:
  - Tài khoản người dùng có trường `dealer_id` liên kết với đại lý đại diện.
  - Các endpoint `/api/portal/orders` và `/api/portal/orders/{id}` được bảo vệ bằng `@PreAuthorize("hasAnyRole('DEALER', 'ADMIN')")`.
  - Tầng nghiệp vụ (`OrderServiceImpl`) lọc dữ liệu chặt chẽ theo `dealer_id`. Nếu một đại lý cố tình truy cập ID đơn hàng của đại lý khác, hệ thống lập tức ném ngoại lệ `AccessDeniedException` và trả về `403 Forbidden`.
  - Nhân viên kinh doanh (`ROLE_SALES`) chỉ xem được đơn hàng của các đại lý mà mình phụ trách.
  - Quản trị viên (`ROLE_ADMIN`) và Quản lý kinh doanh (`ROLE_SALES_MANAGER`) có quyền xem toàn bộ.
- **Tiến trình giao hàng & thiếu hàng (Delivery Shortage Tracking)**:
  - Cung cấp API `PUT /api/orders/{id}/delivery` dành riêng cho Kho / Quản trị viên (`ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN`).
  - Tính toán tự động `shortageQuantity` (`quantity - deliveredQuantity`), cờ `isShortage`, và order-level `hasShortage`.

### 2. Phía Frontend (Chờ thực hiện - PENDING)
- Theo yêu cầu của người dùng, sprint này chỉ tập trung triển khai Backend ("chỉ làm BE").
- Phía Frontend sẽ kế thừa dữ liệu ma trận quyền từ API `/api/admin/permissions` khi triển khai giao diện màn hình Cổng Đại lý (Dealer Portal) ở sprint tiếp theo.

