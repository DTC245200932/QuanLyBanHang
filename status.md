# Bảng Trạng Thái Triển Khai Tính Năng (Feature Status)

Theo quy định tại `AGENTS.md`, bảng dưới đây theo dõi tiến độ hoàn thành và trạng thái kết nối ma trận phân quyền (Permission Matrix) giữa Backend và Frontend:

| Story | Tính năng (Feature) | Backend Done | Frontend Done | Kết nối Ma trận Phân quyền (Permission Matrix) | Ghi chú (Notes) |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **S4-07** | **Xem danh sách đơn hàng kèm bộ lọc & Tổng tiền toàn công ty (Order Listing with Multi-filters & Total Amount)** | ✅ Đã hoàn thành | ⏳ Chưa thực hiện (Yêu cầu chỉ làm BE) | ✅ Đã kết nối đầy đủ (`ORDER_VIEW`) | - **Vai trò**: Quản lý kinh doanh (`ROLE_SALES_MANAGER`, `ORDER_VIEW`).<br>- **Bộ lọc**: Lọc đa tiêu chí phía server theo trạng thái, đại lý, nhân viên kinh doanh, khu vực và khoảng thời gian tạo đơn.<br>- **Tổng tiền**: Tự động tính và trả về tổng tiền (`totalAmount`) của tập kết quả đang lọc kèm endpoint `/api/orders/summary`.<br>- **Phân quyền RLS**: Nhân viên kinh doanh (`ROLE_SALES`) chỉ thấy đơn của đại lý mình phụ trách; Quản lý kinh doanh & Admin xem toàn công ty.<br>- **Bảo mật**: Chặn truy cập chi tiết đơn của đại lý khác với `403 Forbidden` (`AccessDeniedException`). |
| **S4-04** | **Xem lịch sử mua hàng của đại lý & Thêm nhanh nhóm hàng mua lần trước vào đơn mới** | ✅ Đã hoàn thành | ⏳ Sẵn sàng kết nối (Yêu cầu chỉ làm BE; FE đã có sẵn panel gọi API) | ✅ Đã kết nối đầy đủ (`ORDER_CREATE`, `ORDER_VIEW`, `CUSTOMER_VIEW`) | - **Vai trò**: Nhân viên kinh doanh (`ROLE_SALES`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN`).<br>- **Lịch sử 3 tháng & Số lượng bình quân**: Tổng hợp các đơn hàng đã ghi sổ (`RECORDED`) trong 3 tháng gần nhất (`findPurchasedOrdersInPeriod`), tính toán số lượng bình quân (`avgQty`) theo đơn vị mua gần nhất, tự động quy đổi đơn vị nếu có phát sinh khác đơn vị.<br>- **Thêm nhanh nhóm hàng mua lần trước**: Đánh dấu `inLastOrder = true` cho các mặt hàng trong đơn gần nhất và ưu tiên sắp xếp lên đầu danh sách; cung cấp endpoint `GET /last-order-items` và nạp thẳng vào bản nháp tạo đơn qua `POST /last-order-to-draft` (tự động áp lại giá/chiết khấu hiện hành).<br>- **Phân quyền Backend**: `@PreAuthorize("hasAuthority('ORDER_CREATE') or hasAuthority('ORDER_VIEW') or ...")` và RLS `validateSalesAccess` kiểm tra người phụ trách đại lý (`dealer.getAssignedUser()`).<br>- **Phân quyền Frontend**: `create-order.js` kiểm tra `hasPermission('ORDER_CREATE')` và gọi `GET /dealers/{dealerId}/purchase-history?months=3`. |
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

## Chi tiết kết nối phân quyền S4-04 (Permission Matrix Details)

- **Mã quyền (Permission Code)**: `ORDER_CREATE`, `ORDER_VIEW`, `CUSTOMER_VIEW`
- **Các vai trò được cấp quyền**:
  - `ROLE_ADMIN`: Toàn quyền quản trị hệ thống (xem được toàn bộ đại lý).
  - `ROLE_SALES_MANAGER`: Quản lý kinh doanh (xem được toàn bộ đại lý).
  - `ROLE_SALES`: Nhân viên kinh doanh (chỉ xem và thao tác với đại lý do mình được phân công phụ trách).
  - `ROLE_WAREHOUSE_MANAGER`, `ROLE_WAREHOUSE_STAFF`: Hỗ trợ tạo đơn và kiểm tra lịch sử giao dịch.
- **Backend**:
  - `DealerController.java`:
    - `GET /api/dealers/{id}/purchase-history?months={n}&lastOrderOnly={true/false}`: `@PreAuthorize("hasAuthority('ORDER_CREATE') or hasAuthority('ORDER_VIEW') or hasAuthority('CUSTOMER_VIEW') or hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")`
    - `GET /api/dealers/{id}/last-order-items`: `@PreAuthorize("hasAuthority('ORDER_CREATE') or hasAuthority('ORDER_VIEW') or hasAuthority('CUSTOMER_VIEW') or hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")`
    - `POST /api/dealers/{id}/last-order-to-draft`: `@PreAuthorize("hasAuthority('ORDER_CREATE') or hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")`
  - `OrderController.java`:
    - `POST /api/orders/dealer/{dealerId}/last-order-to-draft`: `@PreAuthorize("hasAuthority('ORDER_CREATE') or hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")`
  - `DealerServiceImpl.java`: Row-Level Security (RLS) `validateSalesAccess` kiểm tra người phụ trách đại lý `dealer.getAssignedUser()` trùng khớp với người dùng hiện tại khi người dùng mang vai trò `ROLE_SALES`. Nếu đại lý chưa phân công hoặc do nhân viên khác phụ trách, server trả về mã lỗi `403 Forbidden`.
  - `OrderServiceImpl.java`: RLS `validateSalesAccessForDealer` kiểm tra quyền phụ trách đại lý khi sao chép nhóm hàng đơn gần nhất vào bản nháp tạo đơn của người dùng.
- **Frontend**:
  - `create-order.js`: Kiểm tra vai trò (`hasAnyRole('ROLE_SALES', 'ROLE_SALES_MANAGER', 'ROLE_ADMIN', ...)`) và quyền (`Auth.hasPermission('ORDER_CREATE')`). Khi người dùng chọn đại lý, tự động gọi API `GET /dealers/{dealerId}/purchase-history?months=3` để hiển thị danh sách các sản phẩm đại lý đã mua trong 3 tháng gần nhất kèm số lượng bình quân (`avgQty`), hỗ trợ thêm từng sản phẩm hoặc nhấn "Thêm tất cả vào đơn".
| **S5-01** | **Theo dõi trạng thái đơn hàng của Đại lý (Dealer Order Tracking & Delivery Status)** | ✅ Đã hoàn thành | ⏳ Chờ thực hiện | ✅ Đã kết nối đầy đủ (`ORDER_VIEW`, `ORDER_CREATE`) | - **Vai trò**: Đại lý (`ROLE_DEALER`).<br>- **Tiêu chí 1**: Danh sách đơn kèm trạng thái, ngày giao dự kiến, tổng tiền (`GET /api/portal/orders`).<br>- **Tiêu chí 2**: Xem chi tiết dòng hàng, số lượng thực giao và số lượng giao thiếu (`GET /api/portal/orders/{id}`).<br>- **Tiêu chí 3**: Cô lập dữ liệu (Row-Level Security) chỉ xem đơn của chính mình, chặn truy cập chéo (403 Forbidden).<br>- **Kho & Giao hàng**: API `PUT /api/orders/{id}/delivery` dành cho thủ kho ghi nhận số lượng thực giao.<br>- **Phạm vi**: Chỉ làm BE theo yêu cầu. |

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
## Chi tiết kết nối Ma trận phân quyền S5-01 (Dealer Portal)

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
