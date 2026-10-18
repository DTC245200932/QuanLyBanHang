# Bảng Trạng Thái Triển Khai Tính Năng (Feature Status)

Theo quy định tại `AGENTS.md`, bảng dưới đây theo dõi tiến độ hoàn thành và trạng thái kết nối ma trận phân quyền (Permission Matrix) giữa Backend và Frontend:

| Story | Tính năng (Feature) | Backend Done | Frontend Done | Kết nối Ma trận Phân quyền (Permission Matrix) | Ghi chú (Notes) |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **S4-07** | **Xem danh sách đơn hàng kèm bộ lọc & Tổng tiền toàn công ty (Order Listing with Multi-filters & Total Amount)** | ✅ Đã hoàn thành | ⏳ Chưa thực hiện (Yêu cầu chỉ làm BE) | ✅ Đã kết nối đầy đủ (`ORDER_VIEW`) | - **Vai trò**: Quản lý kinh doanh (`ROLE_SALES_MANAGER`, `ORDER_VIEW`).<br>- **Bộ lọc**: Lọc đa tiêu chí phía server theo trạng thái, đại lý, nhân viên kinh doanh, khu vực và khoảng thời gian tạo đơn.<br>- **Tổng tiền**: Tự động tính và trả về tổng tiền (`totalAmount`) của tập kết quả đang lọc kèm endpoint `/api/orders/summary`.<br>- **Phân quyền RLS**: Nhân viên kinh doanh (`ROLE_SALES`) chỉ thấy đơn của đại lý mình phụ trách; Quản lý kinh doanh & Admin xem toàn công ty.<br>- **Bảo mật**: Chặn truy cập chi tiết đơn của đại lý khác với `403 Forbidden` (`AccessDeniedException`). |
| **S4-09** | **Sao chép đơn cũ thành đơn mới (Order Copy to Draft / New Order)** | ✅ Đã hoàn thành | ✅ Đã hoàn thành | ✅ Đã kết nối đầy đủ (`ORDER_CREATE`) | - **Vai trò**: Nhân viên kinh doanh (`ROLE_SALES`, `ORDER_CREATE`).<br>- **Dòng hàng**: Sao chép toàn bộ dòng hàng sang đơn mới / draft.<br>- **Giá & Chiết khấu**: Tự động áp lại bảng giá hiện hành (`findEffectivePriceLists`) và chính sách chiết khấu sản lượng hiện hành (`calculateOrderDiscount`), không kế thừa giá cũ.<br>- **Trạng thái**: Luôn bắt đầu ở trạng thái Nháp (`DRAFT`).<br>- **Phân quyền Backend**: `@PreAuthorize("hasAuthority('ORDER_CREATE') or ...")` và RLS kiểm tra quyền phụ trách đại lý.<br>- **Phân quyền Frontend**: Kiểm tra `Auth.hasPermission('ORDER_CREATE')` trước khi hiển thị nút sao chép và thực hiện sao chép. |
| **S5-01** | **Theo dõi trạng thái đơn hàng của đại lý** | ✅ Có ở nhánh backend S5-01, chưa gộp vào `develop` | ✅ Đã nối API trên nhánh frontend hiện tại | ✅ Frontend giới hạn menu/route theo `ROLE_DEALER` + `ORDER_VIEW`; backend API giới hạn dữ liệu theo đại lý | Frontend chỉ dùng hợp đồng `GET /api/portal/orders` và `GET /api/portal/orders/{id}`. Không kéo code backend và không merge nhánh backend. |
| **S5-03** | **Khai báo nhiều kho, vị trí lưu trữ (Khu/Kệ), gán kho phục vụ đại lý & Soạn hàng (Warehouse, Locations & Order Picking)** | ✅ Đã hoàn thành | ⏳ Chưa thực hiện (Yêu cầu người dùng: "chỉ làm backtend") | ✅ Đã kết nối đầy đủ (`WAREHOUSE_MANAGE`) | - **Khai báo kho**: Mỗi kho có mã, tên, địa chỉ, người phụ trách, trạng thái. Xóa kho an toàn (chuyển `active=false` nếu đã phát sinh phiếu kho/vị trí).<br>- **Vị trí lưu kho**: Khai báo Khu vực (`ZONE`) và Kệ (`SHELF`) thuộc Khu. Gán sản phẩm vào kệ kèm vị trí mặc định (`isDefault`).<br>- **Gán kho đại lý**: Đại lý được gán kho phục vụ mặc định (`PUT /api/dealers/{id}/default-warehouse`).<br>- **Soạn hàng**: `GET /api/orders/{id}/picking-list` hiển thị chính xác tên Kệ, Khu vực, mã vị trí và tồn khả dụng.<br>- **Phân quyền**: Gán quyền `WAREHOUSE_MANAGE` cho `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN`; bảo vệ bằng `@PreAuthorize` và `SecurityConfig`. Ghi `AuditLog` toàn diện. |
| **S5-06** | **Giữ chỗ tồn kho khi đơn được duyệt & Tự động nhả tồn quá hạn / huỷ đơn (Inventory Reservation on Order Approval & Auto-release)** | ✅ Đã hoàn thành | ⏳ Chưa thực hiện (Yêu cầu chỉ làm BE) | ✅ Đã kết nối đầy đủ (`ORDER_APPROVE`, `ORDER_CANCEL`, `ORDER_VIEW`) | - **Vai trò**: Nhân viên kinh doanh (`ROLE_SALES`), Quản lý kinh doanh (`ROLE_SALES_MANAGER`), Admin (`ROLE_ADMIN`), Quản lý kho (`ROLE_WAREHOUSE_MANAGER`).<br>- **Giữ chỗ tồn khi duyệt đơn**: Khi đơn chuyển sang trạng thái Đã duyệt (`RECORDED`), tồn tương ứng được giữ chỗ (`reservedQuantity`), ngăn chặn bán vượt tồn hoặc mất hàng đã chốt.<br>- **Giao dịch `@Transactional` nguyên tử**: Kiểm tra tồn khả dụng, tăng `reservedQuantity` và đổi trạng thái đơn nằm trong cùng giao dịch; nếu không đủ tồn hoặc phát sinh lỗi thì rollback toàn bộ.<br>- **Tự động nhả tồn**: Huỷ đơn (`cancelOrder`) hoặc từ chối đơn (`rejectOrder`) tự động hoàn trả tồn giữ chỗ. Background Scheduler (`OrderReservationScheduler`) tự động quét định kỳ và nhả tồn đối với đơn quá hạn giữ chỗ (`reservationExpiresAt <= now()`), chuyển đơn sang trạng thái `EXPIRED`.<br>- **Phân quyền Backend**: Phê duyệt / huỷ / giải phóng tồn được bảo vệ bằng `@PreAuthorize("hasAuthority('ORDER_APPROVE') ...")` và `ORDER_CANCEL`. |

---

## Chi tiết kết nối phân quyền S4-07 (Permission Matrix Details)

- **Mã quyền (Permission Code)**: `ORDER_VIEW`
- **Các vai trò được cấp quyền trong ma trận phân quyền**:
  - `ROLE_ADMIN`: Toàn quyền quản trị hệ thống
  - `ROLE_SALES_MANAGER`: Quản lý kinh doanh (xem tình hình bán hàng toàn công ty)
  - `ROLE_SALES`: Nhân viên kinh doanh (phụ trách đại lý)
  - `ROLE_WAREHOUSE_MANAGER`: Quản lý kho
  - `ROLE_WAREHOUSE_STAFF`: Nhân viên kho
  - `ROLE_ACCOUNTANT`: Kế toán
  - `ROLE_VIEWER`: Người xem báo cáo
- **Backend**:
  - `OrderController.java`:
    - `GET /api/orders`: `@PreAuthorize("hasAuthority('ORDER_VIEW') or hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT', 'VIEWER')")`
    - `GET /api/orders/summary`: `@PreAuthorize("hasAuthority('ORDER_VIEW') or hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT', 'VIEWER')")`
    - `GET /api/orders/{id}`: `@PreAuthorize("hasAuthority('ORDER_VIEW') or hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'ACCOUNTANT', 'VIEWER')")`
  - `OrderServiceImpl.java`:
    - `getOrders(OrderFilterRequest filter)`:
      - Xây dựng JPA Specification lọc động theo trạng thái, đại lý, nhân viên, khu vực, khoảng thời gian, từ khóa tìm kiếm.
      - Tự động tính tổng tiền (`totalAmount`) và tổng số đơn (`totalOrders`) của toàn bộ tập kết quả đang lọc.
      - Phân quyền dòng dữ liệu (Row-level Security): Nếu là `ROLE_SALES` (không có quyền quản lý/admin), tự động ép điều kiện `dealer.assignedUser = currentUser`.
    - `getOrderById(Long id, String username)`:
      - Kiểm soát quyền xem: nhân viên kinh doanh cố tình truy cập đơn của đại lý không do mình phụ trách sẽ bị chặn với `403 Forbidden` (`AccessDeniedException`).
    - `mapToResponse(Order order)`: Làm giàu dữ liệu trả về với thông tin đại lý (`dealerCode`, `dealerName`), nhân viên phụ trách (`salesUserId`, `salesUserName`, `salesUserFullName`), và khu vực (`territoryId`, `territoryCode`, `territoryName`).
- **Frontend**:
  - Đã sẵn sàng kết nối qua `Auth.hasPermission('ORDER_VIEW')` và `Auth.hasRole('ROLE_SALES_MANAGER')` khi triển khai giao diện màn hình danh sách đơn hàng cho Quản lý kinh doanh.

---

## Chi tiết kết nối phân quyền S4-09 (Permission Matrix Details)

- **Mã quyền (Permission Code)**: `ORDER_CREATE`
- **Các vai trò được cấp quyền**:
  - `ROLE_ADMIN`: Toàn quyền quản trị
  - `ROLE_SALES_MANAGER`: Quản lý kinh doanh
  - `ROLE_SALES`: Nhân viên kinh doanh (phụ trách đại lý)
- **Backend**:
  - `OrderController.java`:
    - `POST /api/orders/{id}/copy`: `@PreAuthorize("hasAuthority('ORDER_CREATE') or hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")`
    - `POST /api/orders/{id}/copy-to-draft`: `@PreAuthorize("hasAuthority('ORDER_CREATE') or hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")`
  - `OrderServiceImpl.java`: `validateSalesAccess` kiểm soát truy cập phân quyền dòng dữ liệu (Row-level Security) cho nhân viên kinh doanh theo đại lý phụ trách hoặc đơn do chính mình tạo.
- **Frontend**:
  - `dealer-transactions.js`: Kiểm tra `Auth.hasPermission('ORDER_CREATE')` để hiển thị nút hành động "Sao chép đơn".
  - `create-order.js`: Tự động nạp dữ liệu từ bản nháp đã sao chép, gọi bảng giá và chiết khấu hiện hành.

---

## Chi tiết kết nối phân quyền S5-03 (Permission Matrix Details)

- **Mã quyền (Permission Code)**: `WAREHOUSE_MANAGE` ("Quản lý danh mục kho và vị trí")
- **Các vai trò được cấp quyền trong ma trận phân quyền**:
  - `ROLE_ADMIN`: Toàn quyền quản trị hệ thống (tự động kế thừa toàn bộ quyền).
  - `ROLE_WAREHOUSE_MANAGER`: Quản lý kho (khai báo kho, quản lý danh mục kho, phân cấp vị trí lưu trữ Khu/Kệ, gán sản phẩm vào kệ/vị trí, gán kho phục vụ cho đại lý).
- **Backend**:
  - `Permission.java`: Bổ sung `WAREHOUSE_MANAGE("Quản lý danh mục kho và vị trí")`.
  - `RolePermissionConfig.java`: Cấu hình quyền mặc định cho `ROLE_WAREHOUSE_MANAGER` có `Permission.WAREHOUSE_MANAGE`; endpoint `/api/admin/permissions` tự động phản ánh quyền này trong ma trận phân quyền động lưu trong database.
  - `SecurityConfig.java`:
    - Phân quyền URL cho `/api/warehouses/**`, `/api/warehouse-locations/**`, `/api/warehouse/**` cho các vai trò liên quan.
  - `WarehouseResourceController.java`:
    - `POST /api/warehouses`: `@PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasRole('ADMIN')")`
    - `PUT /api/warehouses/{id}`: `@PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasRole('ADMIN')")`
    - `DELETE /api/warehouses/{id}`: `@PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasRole('ADMIN')")`
    - `POST /api/warehouse-locations`: `@PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasRole('ADMIN')")`
    - `PUT /api/warehouse-locations/{id}`: `@PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasRole('ADMIN')")`
    - `DELETE /api/warehouse-locations/{id}`: `@PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasRole('ADMIN')")`
    - `POST /api/warehouse-locations/{locationId}/products`: `@PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasRole('ADMIN')")`
    - `DELETE /api/warehouse-locations/{locationId}/products/{productId}`: `@PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasRole('ADMIN')")`
  - `DealerController.java`:
    - `PUT /api/dealers/{id}/default-warehouse`: `@PreAuthorize("hasAuthority('DEALER_MANAGE') or hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('ADMIN', 'SALES_MANAGER', 'WAREHOUSE_MANAGER')")`
  - `OrderController.java`:
    - `GET /api/orders/{id}/picking-list`: `@PreAuthorize("hasAuthority('ORDER_VIEW') or hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF', 'SALES_MANAGER')")`
  - **Nhật ký kiểm toán (`AuditLog`)**: Ghi log chi tiết cho các hành động `CREATE_WAREHOUSE`, `UPDATE_WAREHOUSE`, `DELETE_WAREHOUSE`, `DEACTIVATE_WAREHOUSE`, `CREATE_LOCATION`, `UPDATE_LOCATION`, `DELETE_LOCATION`, `ASSIGN_PRODUCT_LOCATION`, `REMOVE_PRODUCT_LOCATION`, `ASSIGN_DEFAULT_WAREHOUSE`.
- **Frontend**:
  - ⏳ Chưa triển khai giao diện theo yêu cầu cụ thể của người dùng: *"chỉ làm backtend"*.
  - Đã chuẩn bị sẵn API và mã quyền `WAREHOUSE_MANAGE` tương thích hoàn toàn với cơ chế phân quyền frontend hiện tại (`Auth.hasPermission('WAREHOUSE_MANAGE')` và `Auth.hasRole('ROLE_WAREHOUSE_MANAGER')`).
## Chi tiết kết nối phân quyền S5-06 (Permission Matrix Details)

- **Mã quyền (Permission Code)**: `ORDER_APPROVE`, `ORDER_CANCEL`, `ORDER_VIEW`
- **Các vai trò được cấp quyền trong ma trận phân quyền**:
  - `ROLE_ADMIN`: Toàn quyền quản trị hệ thống (phê duyệt đơn hàng, huỷ đơn, kích hoạt giải phóng tồn giữ chỗ quá hạn).
  - `ROLE_SALES_MANAGER`: Quản lý kinh doanh (phê duyệt đơn hàng chờ duyệt kèm thiết lập thời gian giữ chỗ tồn kho, huỷ đơn, kích hoạt giải phóng tồn).
  - `ROLE_SALES`: Nhân viên kinh doanh (xem trạng thái đơn và hạn giữ chỗ `ORDER_VIEW`, huỷ đơn hàng `ORDER_CANCEL` để tự động hoàn trả tồn giữ chỗ cho các đơn hàng của đại lý phụ trách).
  - `ROLE_WAREHOUSE_MANAGER`: Quản lý kho (theo dõi tồn thực tế/tồn giữ chỗ/tồn khả dụng, kích hoạt quét giải phóng tồn quá hạn).
- **Backend**:
  - `OrderController.java`:
    - `PUT /api/orders/{id}/approve`: `@PreAuthorize("hasAuthority('ORDER_APPROVE') or hasAnyRole('SALES_MANAGER', 'ADMIN')")` — Phê duyệt đơn hàng chờ duyệt (`PENDING_APPROVAL`), giữ chỗ tồn kho và gán hạn giữ chỗ.
    - `PUT /api/orders/{id}/reject`: `@PreAuthorize("hasAuthority('ORDER_APPROVE') or hasAnyRole('SALES_MANAGER', 'ADMIN')")` — Từ chối đơn hàng chờ duyệt, giải phóng tồn giữ chỗ nếu có.
    - `PUT /api/orders/{id}/cancel`: `@PreAuthorize("hasAuthority('ORDER_CANCEL') or hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN')")` — Huỷ đơn hàng; tự động hoàn trả tồn giữ chỗ (`reservedQuantity`) nếu đơn đang giữ chỗ.
    - `POST /api/orders/release-expired-reservations`: `@PreAuthorize("hasAuthority('ORDER_APPROVE') or hasAnyRole('SALES_MANAGER', 'WAREHOUSE_MANAGER', 'ADMIN')")` — Kích hoạt quét và nhả tồn tức thì cho các đơn hàng quá hạn giữ chỗ.
    - `GET /api/orders`, `GET /api/orders/{id}`: Trả về đầy đủ thông tin `inventoryReserved`, `reservedAt`, `reservationExpiresAt`.
  - `OrderServiceImpl.java`:
    - Toàn bộ thao tác kiểm tra tồn khả dụng, tăng `reservedQuantity` và chuyển trạng thái đơn hàng sang `RECORDED` đều nằm trong một giao dịch `@Transactional` nguyên tử (lỗi giữa chừng thì rollback toàn bộ).
    - Khóa bi quan (`LockModeType.PESSIMISTIC_WRITE`) trên các bản ghi `WarehouseInventory` được sắp xếp theo `productId` tăng dần để loại bỏ nguy cơ Deadlock khi nhiều đơn được duyệt đồng thời.
    - `releaseExpiredReservations`: Tìm các đơn có `inventoryReserved = true` và `reservationExpiresAt <= now()`, giảm `reservedQuantity`, gán trạng thái `EXPIRED` và ghi nhật ký kiểm toán.
  - `OrderReservationScheduler.java`:
    - Chạy tác vụ nền định kỳ (mặc định 60 giây qua `app.order.reservation-check-interval-ms`) tự động quét và giải phóng tồn cho các đơn quá hạn.
- **Frontend**:
  - Đã sẵn sàng kết nối qua Ma trận phân quyền:
    - Quyền duyệt đơn: Nút "Duyệt đơn" kiểm tra `Auth.hasPermission('ORDER_APPROVE')` hoặc vai trò `ROLE_SALES_MANAGER`, `ROLE_ADMIN`.
    - Quyền huỷ đơn: Nút "Huỷ đơn" kiểm tra `Auth.hasPermission('ORDER_CANCEL')` hoặc vai trò `ROLE_SALES`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN`.
    - Hiển thị nhãn trạng thái giữ chỗ kho: Kiểm tra `inventoryReserved` và thời hạn `reservationExpiresAt` trên màn hình chi tiết đơn hàng.


