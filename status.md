# Trạng thái phát triển — QuanLyBanHang

> Cập nhật lần cuối: 2026-10-20

## Bảng theo dõi kết nối Ma trận phân quyền & Tính năng

| Mã tính năng | Tên tính năng | Backend | Frontend | Đã kết nối đúng? | Ghi chú quyền & Phân quyền |
|---|---|:---:|:---:|:---:|---|
| **S5-04** | Phiếu nhập kho (Voucher Nhập kho, NCC, Lô, HSD, Nháp) | ✅ | ✅ | ✅ | Quyền `WAREHOUSE_IMPORT`, `WAREHOUSE_VIEW` cho `ROLE_ADMIN`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_WAREHOUSE_STAFF` |
| **S5-06** | Giữ chỗ tồn kho khi đơn được duyệt (Inventory Reservation) | ✅ | ✅ | ✅ | Quyền `ORDER_CREATE`, `ORDER_VIEW`, `ORDER_APPROVE` cho `ROLE_SALES`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN` |

---

## Chi tiết tính năng S5-06: Giữ chỗ tồn kho khi đơn được duyệt

### 1. Nghiệp vụ đã triển khai (Frontend)

| # | Hạng mục nghiệp vụ | Backend | Frontend | Kết nối đúng? | Mô tả chi tiết |
|---|-------------------|:-------:|:--------:|:-------------:|----------------|
| 1 | Giữ chỗ tồn kho ngay khi đơn chuyển sang Đã duyệt (`APPROVED` / `RECORDED`) | ✅ | ✅ | ✅ | Đơn được duyệt lập tức đưa vào tập `HOLDING_ORDER_STATUSES`, tính vào `reservedBase`, làm giảm tồn khả dụng của SKU để hàng không bị người khác bán mất. |
| 2 | Tự động nhả tồn khi đơn bị huỷ (`CANCELLED`) hoặc từ chối (`REJECTED`) | ✅ | ✅ | ✅ | Đơn chuyển trạng thái huỷ hoặc từ chối sẽ tự động loại khỏi tập giữ chỗ, trả lại tồn khả dụng cho kho. |
| 3 | Tự động nhả tồn khi đơn quá hạn giữ chỗ (`reservationExpiresAt <= now`) | ✅ | ✅ | ✅ | Kiểm tra mốc thời gian giữ chỗ; nếu đã hết hạn, hệ thống tự động nhả tồn mà không cần thao tác thủ công. |
| 4 | Hiển thị cột Tồn khả dụng trên bảng tạo đơn hàng | — | ✅ | ✅ | Bổ sung cột thứ 4 trong bảng dòng hàng (`col-stock`), hiển thị tồn khả dụng theo đơn vị tính đã chọn kèm chi tiết (Thực tế / Giữ chỗ). |
| 5 | Chặn đặt vượt tồn khả dụng & Gợi ý số lượng tối đa | — | ✅ | ✅ | Cảnh báo trực quan khi nhập vượt tồn, cung cấp nút **"Đặt tối đa"** để nhanh chóng điều chỉnh số lượng hợp lệ, chặn xác nhận đơn nếu vượt tồn. |
| 6 | Tự động xác định kho phục vụ cho đại lý theo thứ tự ưu tiên | — | ✅ | ✅ | Tự động nạp kho gắn với đại lý -> kho phụ trách của nhân viên -> kho mặc định đầu tiên; tự tải tồn kho thực tế và đơn hàng. |
| 7 | Phát hiện xung đột đồng thời tránh âm tồn | ✅ | ✅ | ✅ | Kiểm tra tồn tức thời trước khi gửi đơn, phát hiện trường hợp hai người cùng chốt đơn khi tồn sắp hết để chặn không làm âm kho. |
| 8 | Hiển thị trạng thái giữ chỗ trên modal chi tiết đơn duyệt | — | ✅ | ✅ | Modal chi tiết đơn duyệt hiển thị rõ ràng: "Đang giữ chỗ tồn kho", "Chờ duyệt (Chưa giữ chỗ)", hoặc "Đã nhả tồn". |

### 2. Ma trận phân quyền (Permission Matrix)

| Hành động | Quyền (Permission) | Roles cho phép | Kiểm tra Frontend | Kiểm tra Backend |
|-----------|--------------------|----------------|:-----------------:|:----------------:|
| Tạo đơn hàng & Kiểm tra tồn giữ chỗ | `ORDER_CREATE` | `ROLE_SALES`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | ✅ `Auth.hasAnyRole` & `OrderInventory` | ✅ `@PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', ...)")` |
| Xem danh sách đơn & Tồn khả dụng | `ORDER_VIEW` | `ROLE_SALES`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN`, `ROLE_WAREHOUSE_MANAGER` | ✅ Menu-config & role guard | ✅ `@PreAuthorize` |
| Phê duyệt đơn hàng (kích hoạt giữ chỗ tồn) | `ORDER_APPROVE` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | ✅ Role guard `order-approval.js` | ✅ `PUT /orders/{id}/approve` (`hasAnyRole('SALES_MANAGER', 'ADMIN')`) |
| Huỷ đơn hàng (kích hoạt nhả tồn tự động) | `ORDER_CANCEL` | `ROLE_SALES`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | ✅ Action menu | ✅ `PUT /orders/{id}/cancel` |

### 3. Kết quả Unit Tests (50/50 Tests Passed)

- `tests/order-reservation-s5-06.test.cjs`: 6/6 tests pass (bao gồm giữ chỗ khi duyệt, nhả tồn khi huỷ, tự động nhả tồn khi quá hạn, quy đổi theo đơn vị tính, xung đột đồng thời).
- `tests/order-inventory.test.cjs`: 7/7 tests pass.
- `tests/create-order-debt.test.cjs`: 4/4 tests pass.
- `tests/warehouse-import-voucher.test.cjs`: 3/3 tests pass.
- `tests/menu-config.test.cjs`: 13/13 tests pass.
- `tests/dealer-transactions.test.cjs`: 5/5 tests pass.
- `tests/password-reset.test.cjs`: 7/7 tests pass.
- `tests/products-import.test.cjs`: 5/5 tests pass.
- Tất cả unit tests đều chạy thành công 100%.

