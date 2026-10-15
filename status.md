# Trạng thái dự án Quản Lý Bán Hàng (SaleStock)

Cập nhật lần cuối: 14/10/2026

## 1. Bảng theo dõi tính năng & Kết nối ma trận phân quyền

Tuân thủ quy định tại `AGENTS.md`: Sau khi hoàn thành tính năng, rà soát ma trận phân quyền (Permission Matrix) ở cả Backend & Frontend, đồng thời cập nhật bảng tiến độ.

| Mã tính năng | Tên tính năng | Backend | Frontend | Kết nối ma trận phân quyền (Backend & Frontend) | Trạng thái tổng thể |
|---|---|---|---|---|---|
| **S4-01** | Áp bảng giá & chiết khấu tự động theo nhóm khách hàng | **Hoàn thành** | Hoàn thành | Đã nối: `/api/orders/line-item/pricing`, `/api/orders/calculate` kiểm soát quyền qua `@PreAuthorize` và `SecurityConfig`. | Đạt nghiệm thu |
| **S4-02** | Kiểm tra hạn mức công nợ & cảnh báo vượt hạn mức | **Hoàn thành** | Hoàn thành | Đã nối: `/api/orders/check-debt`, phân quyền theo vai trò `SALES`, `SALES_MANAGER`, `ACCOUNTANT`, `ADMIN`. | Đạt nghiệm thu |
| **S4-03** | Tồn khả dụng, chặn vượt tồn kho & chống bán âm khi chốt đơn đồng thời | **Hoàn thành** | Một phần (PR #67) | **Backend đã hoàn thành & bảo vệ nghiêm ngặt**: <br>- `POST /api/orders/check-inventory`: tra cứu tồn khả dụng và gợi ý số lượng tối đa.<br>- Khóa bi quan `PESSIMISTIC_WRITE` trên `WarehouseInventory` chống race condition.<br>- `ROLE_SALES` được xem tồn kho `/api/warehouse/inventory` và kho phục vụ `/api/warehouse/options`, nhưng **bị chặn tuyệt đối** với điều chỉnh tồn kho `/api/warehouse/adjust` (403 Forbidden).<br>- Frontend có UI module ở PR #67, cần tích hợp gọi API khi merge FE. | **Backend Đạt 100%** (Frontend đang ghép) |
| **S4-04** | Lịch sử mua hàng đại lý | Đang phát triển | Có ở PR #66 | Chưa nối | Chờ triển khai |
| **S4-05** | Phê duyệt đơn hàng bán dưới giá sàn / vượt công nợ | Hoàn thành cơ bản | Đang phát triển | Đã có endpoint duyệt/từ chối của Quản lý kinh doanh (`ROLE_SALES_MANAGER`, `ROLE_ADMIN`). | Đang hoàn thiện |
| **S4-06** | Vòng đời đơn hàng & Huỷ đơn giải phóng tồn giữ chỗ | **Hoàn thành** | Đang phát triển | Đã có logic giải phóng `reservedQuantity` khi Huỷ (`cancelOrder`) hoặc Từ chối (`rejectOrder`). | Backend Đạt |
| **S4-07** | Danh sách & Bộ lọc đơn hàng theo phạm vi đại lý | Đang phát triển | Đang phát triển | Cần siết chặt phạm vi đại lý được gán cho Sales. | Đang hoàn thiện |
| **S4-08** | In phiếu & Xuất PDF đơn hàng | Chưa triển khai | Chưa triển khai | Chưa có API | Chưa triển khai |
| **S4-09** | Sao chép đơn hàng & Áp lại giá hiện hành | Chưa triển khai | Chưa triển khai | Chưa có API | Chưa triển khai |
| **S4-10** | Cổng đặt hàng cho đại lý | Chưa triển khai | Chưa triển khai | Chưa có API | Chưa triển khai |

---

## 2. Chi tiết kỹ thuật cho S4-03 (Tồn khả dụng & Kiểm soát đồng thời)

### Backend:
- **Công thức tính**: `availableQuantity = max(0, baseQuantity - reservedQuantity)`. Tự động quy đổi theo đơn vị đặt hàng (`availableInUnit`) và gợi ý số lượng tối đa nguyên đơn vị (`maxAllowedInUnit`).
- **Kho phục vụ**: Tự động ưu tiên kho phục vụ gán cho đại lý (`dealer.warehouse`), hoặc cho phép chọn kho hoạt động hợp lệ.
- **Chặn vượt tồn**: Báo lỗi `400 Bad Request` kèm thông báo chi tiết mã hàng, kho phục vụ, tồn khả dụng và số lượng tối đa được phép đặt.
- **Giữ chỗ & Nhả giữ chỗ**: Đơn ghi sổ (`RECORDED`) tự động tăng `reservedQuantity` và set `inventoryReserved = true`. Huỷ đơn (`cancelOrder`) hoặc Từ chối (`rejectOrder`) tự động giải phóng `reservedQuantity`. Xuất kho (`POST /api/warehouse/export`) tự động trừ tồn thực tế và giảm giữ chỗ.
- **An toàn đồng thời (Concurrency Safety)**: Sử dụng `LockModeType.PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) sắp xếp theo `productId` tăng dần để tránh Deadlock. Kiểm thử tự động đa luồng xác nhận: 2 người cùng chốt SKU sắp hết thì chỉ 1 người thành công, 1 người bị chặn, tồn kho không bao giờ âm.
- **Phân quyền vai trò (RBAC)**:
  - `ROLE_SALES`: Được xem tồn kho (`GET /api/warehouse/inventory`), xem danh sách kho (`GET /api/warehouse/options`), kiểm tra tồn (`POST /api/orders/check-inventory`), tính toán và tạo đơn.
  - `ROLE_SALES`: Bị từ chối `403 Forbidden` khi cố ý sửa tồn kho (`POST /api/warehouse/adjust`).
  - Unit tests & RBAC tests: **100% Passed**.
