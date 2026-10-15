# Trạng thái dự án và Kết nối tính năng (Status Matrix)

Cập nhật lần cuối: 15/10/2026

Bảng theo dõi trạng thái hoàn thành giữa Backend và Frontend, cùng việc kết nối với Ma trận phân quyền (Permission Matrix) theo quy định tại `AGENTS.md`.

## Bảng tổng hợp trạng thái các tính năng Sprint 4

| Mã Task | Tên tính năng | Backend | Frontend | Ma trận phân quyền (Backend & Frontend) | Trạng thái kết nối | Ghi chú |
|---|---|---|---|---|---|---|
| **S4-01** | Áp giá, chiết khấu dòng hàng | Một phần | Hoàn thành | Đã kết nối (`ORDER_CREATE`, `SALES`) | Đã kết nối | Áp giá tự động theo bảng giá hiệu lực và tính chiết khấu sản lượng. |
| **S4-02** | Hạn mức công nợ & cảnh báo | Hoàn thành | Hoàn thành | Đã kết nối (`FINANCE_VIEW_DEBT`) | Đã kết nối | Đọc đúng DTO hạn mức nợ, cảnh báo vượt hạn mức và chặn khi quá hạn. |
| **S4-03** | Tồn khả dụng theo kho | Một phần | Một phần | Chưa đầy đủ (SALES bị 403 ở API kho) | Chưa kết nối | Module tính khả dụng có sẵn, cần mở quyền và gắn kho phục vụ. |
| **S4-04** | Lịch sử mua hàng 3 tháng | Chưa có | Hoàn thành | Chưa kết nối | Chưa kết nối | Frontend đã có UI lịch sử, chờ backend cung cấp API lịch sử theo đại lý. |
| **S4-05** | **Duyệt đơn vượt hạn mức / dưới giá sàn** | **Một phần** (Có Duyệt, Từ chối, Audit Log; thiếu endpoint Trả lại sửa) | **Hoàn thành** (Màn hình `/order-approval`, phân tích mức vi phạm, 3 hành động có validate ý kiến, lịch sử duyệt bất biến) | **Đã kết nối đầy đủ** (`ORDER_APPROVE`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN`) | **Đã kết nối** (Duyệt `PUT /approve`, Từ chối `PUT /reject?reason=...`, Trả lại sửa gửi payload chuẩn) | **Frontend hoàn thành 100% tiêu chí yêu cầu trong task SCRUM-85.** |
| **S4-06** | Vòng đời đơn hàng & Huỷ đơn | Một phần | Hoàn thành | Đã kết nối (`ORDER_CANCEL`) | Một phần | Frontend bắt buộc nhập lý do huỷ; backend chưa lưu lý do vào chi tiết. |
| **S4-07** | Danh sách & bộ lọc đơn hàng | Một phần | Một phần | Một phần | Chưa đầy đủ | Controller GET `/orders` chưa lọc theo query param, frontend lọc client-side. |
| **S4-08** | In/PDF đơn hàng & Barcode | Chưa có | Chưa có | Chưa có | Chưa triển khai | Cần tạo template in và thư viện tạo mã vạch đơn. |
| **S4-09** | Sao chép đơn hàng | Chưa có | Chưa có | Chưa có | Chưa triển khai | Sao chép toàn bộ dòng và tính lại giá hiện hành. |
| **S4-10** | Cổng đại lý đặt hàng | Chưa có | Chưa có | Chưa có | Chưa triển khai | Cần tài khoản gắn đại lý và luồng giỏ hàng riêng. |

---

## Chi tiết nghiệm thu S4-05 (SCRUM-85)

### 1. Vai trò (Role)
- **Quản lý kinh doanh** (`ROLE_SALES_MANAGER`) và Quản trị viên (`ROLE_ADMIN`).
- Gắn chặt với quyền `ORDER_APPROVE` trong ma trận phân quyền hệ thống (`RolePermissionConfig.java` & `menu-config.js`).

### 2. Danh sách đơn chờ duyệt
- Hiển thị danh sách đơn `PENDING_APPROVAL` mặc định, hỗ trợ tìm kiếm và lọc trạng thái/lý do.
- **Hiển thị rõ lý do cần duyệt**: Tag màu trực quan (`Vượt hạn mức công nợ`, `Bán dưới giá sàn`).
- **Hiển thị rõ mức vi phạm**:
  - Vi phạm công nợ: Số tiền vượt so với hạn mức (ví dụ: `+1.000.000 ₫` / Hạn mức: `20.000.000 ₫`).
  - Vi phạm giá sàn: Chênh lệch bán dưới giá sàn theo từng SKU (ví dụ: `-20.000 ₫/thùng` / Giá bán `180.000 ₫ < Sàn 200.000 ₫`).

### 3. Ba hành động duyệt
- **Duyệt (Approve)**:
  - Gọi `PUT /api/orders/{id}/approve`.
  - Thông báo rõ ràng: "Đơn được duyệt mới chuyển sang giữ chỗ tồn và chờ xuất kho."
- **Từ chối (Reject)**:
  - Bắt buộc nhập ý kiến/lý do (validation chặn trống, khoảng trắng, tối đa 500 ký tự).
  - Gọi `PUT /api/orders/{id}/reject?reason=...`.
- **Trả lại sửa (Return)**:
  - Bắt buộc nhập ý kiến chỉnh sửa (validation chặn trống, khoảng trắng, tối đa 500 ký tự).
  - Gửi payload chuẩn tới `PUT /api/orders/{id}/return?reason=...`.

### 4. Giữ chỗ tồn và Lịch sử duyệt
- Giao diện và thông điệp xác nhận rõ quy tắc: Đơn được duyệt mới chuyển sang giữ chỗ tồn và chờ xuất kho.
- Khung Lịch sử duyệt bất biến (immutable), không có nút sửa/xoá, ghi vết rõ ràng thời gian, người gửi, lý do duyệt, người duyệt và ý kiến xử lý.
