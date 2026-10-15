# Bảng Trạng Thái Tiến Độ Dự Án (Sprint 4 - Quản Lý Bán Hàng)

Tài liệu theo dõi trạng thái triển khai và kết nối ma trận phân quyền theo quy định tại `AGENTS.md`.

## 1. Trạng Thái Tính Năng Sprint 4

| Mã | Tính năng | Backend | Frontend | Kết nối Ma trận Phân quyền | Ghi chú |
|---|---|:---:|:---:|:---:|---|
| **S4-01** | Tự động áp bảng giá hiệu lực theo nhóm đại lý, chặn thêm SKU chưa có bảng giá | ✅ Hoàn thành | Đã có | ✅ Đồng bộ (`ORDER_CREATE`, `SALES`, `SALES_MANAGER`, `ADMIN`) | Áp giá bán, giá sàn, quy đổi đơn vị theo bảng giá hiệu lực của nhóm khách hàng đại lý. |
| **S4-02** | Kiểm tra công nợ đại lý, cảnh báo vượt hạn mức nợ / nợ quá hạn | ✅ Hoàn thành | Đã có | ✅ Đồng bộ (`SALES`, `SALES_MANAGER`, `ADMIN`, `ACCOUNTANT`) | Tự động gắn cờ `requiresApproval` và lý do `OVER_DEBT_LIMIT`. |
| **S4-03** | Áp chiết khấu sản lượng tự động theo bậc chính sách | ✅ Hoàn thành | Đã có | ✅ Đồng bộ (`SALES`, `SALES_MANAGER`, `ADMIN`) | Tính lại chiết khấu tức thì khi sửa số lượng hoặc đơn vị. |
| **S4-04** | Tra cứu lịch sử mua hàng đại lý gợi ý mặt hàng thường mua | ✅ Hoàn thành | Đã có | ✅ Đồng bộ (`SALES`, `SALES_MANAGER`, `ADMIN`, `ACCOUNTANT`, `VIEWER`) | API `GET /api/dealers/{id}/purchase-history` hỗ trợ lọc theo N tháng (mặc định 3 tháng). |
| **S4-05** | Phê duyệt đơn hàng (Duyệt, Từ chối, Trả lại sửa) | ✅ Hoàn thành | Đã có | ✅ Đồng bộ (`ORDER_APPROVE`, `SALES_MANAGER`, `ADMIN`) | Bắt buộc nhập ý kiến khi Từ chối hoặc Trả lại; duyệt xong mới chuyển giữ chỗ tồn (`RECORDED`) và chờ xuất kho; ghi nhật ký kiểm toán không thể sửa/xoá. |
| **S4-06** | Huỷ đơn hàng có lý do bắt buộc | ✅ Hoàn thành | Đã có | ✅ Đồng bộ (`SALES`, `SALES_MANAGER`, `ADMIN`) | Bắt buộc cung cấp `reason`; chặn huỷ đơn đã xuất kho (`EXPORTED`) hoặc đã giao hàng (`DELIVERED`); lưu lý do, người huỷ và thời điểm huỷ. |
| **S4-07** | Bộ lọc danh sách đơn hàng đa tiêu chí | ✅ Hoàn thành | Đã có | ✅ Đồng bộ (Tất cả vai trò theo phạm vi dữ liệu) | Lọc theo trạng thái, lý do duyệt, cần duyệt, tìm kiếm, đại lý (`dealerId`), người tạo (`createdBy`), khu vực (`territoryId`), khoảng ngày (`dateFrom`, `dateTo`). |
| **S4-08** | In phiếu đặt hàng (HTML / PDF) | ✅ Hoàn thành | Đã có | ✅ Đồng bộ (`SALES`, `SALES_MANAGER`, `ADMIN`, `WAREHOUSE_STAFF`, `WAREHOUSE_MANAGER`) | Endpoint `GET /api/orders/{id}/print` trả về HTML sẵn sàng in trực tiếp hoặc xuất PDF qua trình duyệt. |
| **S4-09** | Sao chép đơn hàng cũ thành đơn mới | ✅ Hoàn thành | Đã có | ✅ Đồng bộ (`ORDER_CREATE`, `SALES`, `SALES_MANAGER`, `ADMIN`) | API `POST /api/orders/{id}/copy` giữ nguyên đại lý, điểm giao, số lượng; tự động áp bảng giá hiệu lực hiện hành và kiểm tra trạng thái khoá của đại lý. |

---

## 2. Ma Trận Phân Quyền Chi Tiết (Backend Verification)

| Hành động | Endpoint | Quyền yêu cầu (Role / Authority) | Kiểm tra Backend |
|---|---|---|:---:|
| Xem danh sách đơn hàng | `GET /api/orders` | Đã xác thực (tự động phân quyền phạm vi dữ liệu) | ✅ Passed |
| In phiếu đơn hàng | `GET /api/orders/{id}/print` | Đã xác thực (`SALES`, `SALES_MANAGER`, `ADMIN`, v.v.) | ✅ Passed |
| Tạo đơn hàng mới | `POST /api/orders` | `ORDER_CREATE` / `SALES`, `SALES_MANAGER`, `ADMIN` | ✅ Passed |
| Sao chép đơn hàng | `POST /api/orders/{id}/copy` | `ORDER_CREATE` / `SALES`, `SALES_MANAGER`, `ADMIN` | ✅ Passed |
| Huỷ đơn hàng | `PUT /api/orders/{id}/cancel` | `ORDER_CANCEL` / `SALES`, `SALES_MANAGER`, `ADMIN` | ✅ Passed |
| Phê duyệt đơn hàng | `PATCH /api/orders/{id}/status` | `ORDER_APPROVE` / `SALES_MANAGER`, `ADMIN` | ✅ Passed |
| Phê duyệt nhanh | `PUT /api/orders/{id}/approve` | `ORDER_APPROVE` / `SALES_MANAGER`, `ADMIN` | ✅ Passed |
| Từ chối đơn | `PUT /api/orders/{id}/reject` | `ORDER_APPROVE` / `SALES_MANAGER`, `ADMIN` | ✅ Passed |
| Trả lại đơn | `PUT /api/orders/{id}/return` | `ORDER_APPROVE` / `SALES_MANAGER`, `ADMIN` | ✅ Passed |
| Tra cứu lịch sử mua hàng | `GET /api/dealers/{id}/purchase-history` | `SALES`, `SALES_MANAGER`, `ADMIN`, `ACCOUNTANT`, `VIEWER` | ✅ Passed |

---

## 3. Kết Quả Kiểm Thử Tự Động (Maven Surefire Test Suite)

- **Tổng số bài kiểm thử đã chạy**: 360/360 tests
- **Số lỗi / thất bại**: 0 failures, 0 errors
- **Tỷ lệ thành công**: 100%
- **Các test suite cốt lõi**:
  - `OrderControllerTest` (14/14 tests Passed)
  - `OrderApprovalControllerTest` (9/9 tests Passed)
  - `DealerControllerTest` (47/47 tests Passed)
  - `OrderApprovalServiceTest` (10/10 tests Passed)
  - `OrderPriceListTest` (13/13 tests Passed)
  - `OrderServiceTest` (7/7 tests Passed)
  - `RoleBasedAccessControlTest` (23/23 tests Passed)
