# Trạng thái phát triển — QuanLyBanHang

> Cập nhật lần cuối: 2026-10-20

## S5-04: Phiếu nhập kho (Warehouse Import Voucher)

### Tổng quan tính năng

| # | Tính năng | Backend | Frontend | Kết nối đúng? |
|---|-----------|:-------:|:--------:|:-------------:|
| 1 | Dropdown nhà cung cấp (`/suppliers?size=100`) | ✅ | ✅ | ✅ |
| 2 | Lưu bản nháp phiếu kho (DRAFT, không gọi API ghi sổ) | — (local) | ✅ | ✅ |
| 3 | Xác nhận ghi sổ phiếu nhập kho (`POST /warehouse/import`) | ✅ | ✅ | ✅ |
| 4 | Ghi số chứng từ gốc (`docNumber`) vào ghi chú phiếu | ✅ | ✅ | ✅ |
| 5 | Ghi tên nhà cung cấp (`supplierName`) vào ghi chú phiếu | ✅ | ✅ | ✅ |
| 6 | Ghi số lô & hạn sử dụng vào note từng item | ✅ | ✅ | ✅ |
| 7 | Status label mở rộng: DRAFT, ADJUSTED | — | ✅ | ✅ |
| 8 | Hiển thị nháp trên sổ cái + nút "Sửa nháp" | — (local) | ✅ | ✅ |
| 9 | Modal chi tiết phiếu: hiện lô/HSD + thông báo bất biến RECORDED | ✅ | ✅ | ✅ |
| 10 | Snapshot hệ số quy đổi tại thời điểm ghi sổ | ✅ | ✅ | ✅ |

### Ma trận phân quyền (Permission Matrix)

| Hành động | Permission | Roles cho phép | FE kiểm tra | BE kiểm tra |
|-----------|-----------|----------------|:-----------:|:-----------:|
| Xem tồn kho / sổ cái | `WAREHOUSE_VIEW` | ADMIN, SALES_MANAGER, WAREHOUSE_MANAGER, WAREHOUSE_STAFF, WAREHOUSE_VIEWER | ✅ `canManageUnits` | ✅ |
| Tạo phiếu nhập/xuất kho | `WAREHOUSE_IMPORT` | ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF | ✅ `canRecordVouchers` | ✅ |
| Lưu nháp phiếu | `WAREHOUSE_IMPORT` | ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF | ✅ `canRecordVouchers` | — (local) |
| Quản lý đơn vị quy đổi | `WAREHOUSE_VIEW` | ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF, SALES_MANAGER | ✅ `canManageUnits` | ✅ |
| Tab "Nhập / Xuất kho" | `WAREHOUSE_IMPORT` | ADMIN, WAREHOUSE_MANAGER, WAREHOUSE_STAFF | ✅ ẩn tab | ✅ menu-config |

### Unit Tests

| Test | Mô tả | Kết quả |
|------|--------|:-------:|
| `S5-04: Khởi tạo tải danh sách NCC` | Gọi `/suppliers?size=100`, populate dropdown | ✅ Pass |
| `S5-04: Lưu nháp không gọi API` | Không POST `/warehouse/import`, hiện toast | ✅ Pass |
| `S5-04: Xác nhận ghi sổ` | POST `/warehouse/import` với voucherCode, docNumber, supplierName | ✅ Pass |

### File đã sửa

- `frontend/js/warehouse-units.js` — Toàn bộ logic S5-04
- `tests/warehouse-import-voucher.test.cjs` — 3 unit tests

