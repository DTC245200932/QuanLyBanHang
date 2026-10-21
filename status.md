# Trạng thái sprint 1–5

## S6-01 — Theo dõi Lô và Hạn sử dụng của Hàng hoá (Backend) — 22/10/2026

Nguồn yêu cầu: Ảnh user story S6-01 người dùng cung cấp trong chat (*"Là Nhân viên kho, tôi muốn theo dõi lô và hạn sử dụng của hàng hoá, để không giao nhầm hàng sắp hết hạn cho đại lý"*). Phạm vi thực hiện: **chỉ làm BE** theo chỉ đạo trực tiếp của người dùng, tuân thủ AGENTS.md và ma trận phân quyền.

| Tiêu chí / hạng mục | Backend | Frontend | Kết nối / ma trận quyền |
|---|---|---|---|
| Bật hoặc tắt quản lý lô theo từng SKU | Đã hoàn thành: `PATCH /api/products/{id}/manage-lot` (và `PUT /api/products/{id}`), hỗ trợ bật/tắt cờ `manageLot` và cấu hình `expiryWarningDays`; mặc định 30 ngày nếu không khai báo cụ thể | Chưa triển khai (chỉ làm backend theo yêu cầu) | Quyền `PRODUCT_EDIT` hoặc `WAREHOUSE_MANAGE` hoặc `ROLE_ADMIN`; ma trận database-backed, thu hồi quyền lập tức trả 403 không fallback role |
| Mỗi lô có số lô, ngày sản xuất, hạn sử dụng và số lượng còn lại | Đã hoàn thành: `GET /api/warehouse/inventory/lots`, trả về đầy đủ `lotNumber`, `manufacturingDate`, `expiryDate`, `baseQuantity` (số lượng còn lại), `baseUnit`, `daysUntilExpiry`, `status` ("EXPIRED", "EXPIRING_SOON", "ACTIVE"), `warningThresholdDays`, `isExpired`, `isExpiringSoon` | Chưa triển khai (chỉ làm backend theo yêu cầu) | Quyền `WAREHOUSE_VIEW` hoặc `WAREHOUSE_MANAGE` hoặc `ROLE_ADMIN` |
| Cảnh báo lô sắp hết hạn theo ngưỡng khai báo được | Đã hoàn thành: `GET /api/warehouse/lot-expiry-alerts` (thống kê tổng cảnh báo, số lô quá hạn, số lô sắp hết hạn kèm danh sách chi tiết các lô có tồn > 0 sắp hết hạn theo ngưỡng SKU hoặc ngưỡng tùy chọn `thresholdDays`), `GET /api/warehouse/lot-thresholds` (xem danh sách ngưỡng theo SKU), `PUT /api/warehouse/lot-thresholds/{productId}` (khai báo ngưỡng ngày cảnh báo cho SKU) | Chưa triển khai (chỉ làm backend theo yêu cầu) | Xem cảnh báo & ngưỡng: `WAREHOUSE_VIEW` hoặc `WAREHOUSE_MANAGE` hoặc `ROLE_ADMIN`; Khai báo/sửa ngưỡng: `WAREHOUSE_MANAGE` hoặc `ROLE_ADMIN` |
| Không cho xuất lô đã quá hạn, trừ khi Quản lý kho xác nhận ghi đè kèm lý do | Đã hoàn thành: `POST /api/warehouse/export` và `POST /api/warehouse/vouchers/{id}/confirm`. Tự động kiểm tra hạn sử dụng của lô khi xuất. Nếu quá hạn: Nhân viên kho bình thường bị chặn 403 Forbidden; Quản lý kho bắt buộc phải bật cờ `overrideExpiredLot = true` và nhập lý do `expiredLotOverrideReason` không rỗng; lưu vết phiếu kho, chi tiết dòng và AuditLog `OVERRIDE_EXPIRED_EXPORT` | Chưa triển khai (chỉ làm backend theo yêu cầu) | Quản lý kho: `WAREHOUSE_APPROVE` hoặc `WAREHOUSE_MANAGE` hoặc `ROLE_ADMIN`. Thu hồi quyền `WAREHOUSE_APPROVE` trên phiên JWT lập tức chặn xuất lô quá hạn với lỗi 403, không fallback role |

Kiểm chứng: **24 test backend mới qua toàn bộ**, bao gồm:
- 11 test service `WarehouseLotExpiryServiceTest`: xem tồn theo lô đầy đủ số lượng/NSX/HSD, cảnh báo sắp hết hạn theo ngưỡng SKU và ngưỡng tùy chọn, cập nhật ngưỡng cảnh báo SKU, chặn xuất lô quá hạn đối với nhân viên kho (403 AccessDenied), chặn Quản lý kho nếu thiếu cờ ghi đè hoặc thiếu lý do, cho phép Quản lý kho xuất kèm lý do và ghi audit log, chặn xuất vượt quá tồn lô.
- 9 test bảo mật `WarehouseLotSecurityTest`: ma trận phân quyền database-backed, chặn 401 khi chưa đăng nhập, chặn 403 khi thiếu quyền xem kho, cho phép Thủ kho có `WAREHOUSE_VIEW` xem tồn lô và cảnh báo, chặn Thủ kho sửa ngưỡng (403), cho phép Quản lý kho có `WAREHOUSE_MANAGE` sửa ngưỡng (200), chặn Thủ kho xuất lô quá hạn (403), cho phép Quản lý kho xuất có lý do (200), thu hồi `WAREHOUSE_APPROVE` lập tức chặn 403, `ROLE_ADMIN` luôn có toàn quyền.
- 4 test controller `ProductManageLotControllerTest`: bật/tắt quản lý lô và cập nhật ngưỡng theo SKU qua PATCH API với quyền `PRODUCT_EDIT` / `WAREHOUSE_MANAGE`.
- Hồi quy toàn bộ kho: 60/60 test liên quan đến kho và kiểm kê đều pass.

Cập nhật **21/10/2026**: hoàn thiện S5-08 trên nhánh `feature/S5-08-chuc-nang-kiem-ke-chenh-lech(frontend)`, sau khi người dùng đồng bộ develop tại merge commit `5d9008c`. Các mục phía dưới giữ lịch sử những đợt triển khai trước; trạng thái S5-08 mới nhất nằm ở bảng ngay dưới đây. Đợt sửa này không tự commit, merge vào develop hoặc push.

## S5-08 — Hoàn thiện frontend, backend và đối chiếu tiêu chí Jira — 21/10/2026

Nguồn nghiệm thu: ảnh user story S5-08 người dùng cung cấp trong chat. Người dùng đã xác nhận tiêu chí chốt phiếu áp dụng **WAREHOUSE_ADJUST theo AGENTS.md**, không khóa cứng theo `ROLE_WAREHOUSE_MANAGER`; `ROLE_ADMIN` luôn có toàn quyền.

| Tiêu chí / hạng mục | Backend | Frontend | Kết nối / ma trận quyền |
|---|---|---|---|
| Tạo phiếu theo kho hoặc nhóm hàng, chốt tồn sổ tại thời điểm tạo | POST `/warehouse/stock-counts`; snapshot từ tồn DB, đơn vị cơ sở, ID và phiên bản tồn; sinh mã tại server | Chọn kho và nhóm hàng bằng ID từ options; nhận mã, người tạo, thời điểm và tồn sổ từ server, không sinh snapshot bằng JavaScript | VIEW hoặc ADJUST, ADMIN; giới hạn kho được giao tại API; chỉ nạp API kiểm kê khi mở tab, không đòi PRODUCT_VIEW/ORDER_VIEW hoặc các API phiếu kho cũ |
| Nhập số đếm và tự tính chênh lệch từng dòng | Kiểm số >=0, tối đa 4 chữ số thập phân, chênh lệch = thực đếm - tồn sổ; từ chối dòng ngoài phiếu, trùng hoặc sai ID | Phân biệt trống/null với số 0, nhãn chưa đếm/khớp/thừa/thiếu, tính hiển thị theo cùng độ chính xác; ghi chú tối đa 500 ký tự | PUT `/{id}/items`; không thay tồn kho khi lưu nháp; backend luôn tính lại, không nhận chênh lệch do client tự gửi |
| Lưu/mở lại phiếu và lịch sử | Phiếu, snapshot, số đếm, ghi chú và trạng thái lưu DB; danh sách phân trang, chi tiết theo ID | Tạo, lưu nháp, reload, mở lại từ lịch sử, hủy phiếu nháp; chuyển kho không dùng lại phiếu kho cũ; giữ input/lý do khi API lỗi | GET options/list/detail, POST create/cancel và PUT items đúng hợp đồng mới; không lưu nghiệp vụ trong browser storage, không dùng `/warehouse/adjust` cho kiểm kê |
| Chốt về số thực đếm, bắt buộc lý do | POST `/{id}/complete`; tất cả dòng phải đã đếm, lý do không được trống; cập nhật tồn, trạng thái và audit trong cùng transaction | Chốt/nháp/hủy khóa trong lúc gửi; phiếu đã chốt/hủy chỉ đọc; báo riêng trường hợp ghi thành công nhưng tải lịch sử thất bại để tránh gửi lặp | WAREHOUSE_ADJUST hoặc ADMIN; backend kiểm lại quyền trên yêu cầu hiện tại, frontend đối chiếu quyền và canEdit/canAdjust từ server |
| Chống chốt đồng thời, ghi đè chuyển động và mất tồn giữ chỗ | Khóa phiếu -> kho -> các dòng tồn theo SKU tăng dần; kiểm snapshot ID/version/đơn vị/số lượng trước mọi ghi; thực đếm không thấp hơn lượng đang giữ chỗ; lỗi rollback toàn phiếu và audit | Hiện lỗi và giữ số đếm/lý do; nếu tồn đã đổi, yêu cầu hủy và lập phiếu mới thay vì âm thầm ghi đè | Đã kiểm chốt cùng phiếu, sửa/hủy cạnh tranh chốt, đặt giữ chỗ đồng thời, nhiều dòng lỗi và lỗi audit, chuyển động về lại số lượng cũ |
| Ma trận trên phiên đang mở | Grant/revoke trên JWT cũ có hiệu lực, không fallback role; nhiều role nhận hợp quyền, giới hạn kho được giao | Menu kiểm kê nhận VIEW hoặc ADJUST; chốt chỉ ADJUST/ADMIN; thu hồi quyền xóa dữ liệu nhạy cảm, vô hiệu phản hồi cũ; vai trò VIEWER được cấp ADJUST vẫn sử dụng được | Tiêu chí “Quản lý kho chốt” được diễn giải theo quyền chốt người dùng đã xác nhận, không kiểm vai trò cứng |
| Bố cục và tương thích | Giữ API/luồng các sprint khác | Giữ shared shell, sidebar/topbar và lề chung; bảng cuộn nội bộ; thêm menu kiểm kê mà giữ menu tồn tối thiểu/chuyển kho | Kiểm desktop/tablet/mobile; không đổi layout chung của các trang khác |

Kiểm chứng cuối: **649 test backend qua**, trong đó **51 test kiểm kê** (19 tích hợp HTTP/JWT/H2, 17 service, 9 controller, 6 security), chạy trên bản sao tạm, không sửa MySQL thật; **113 test JavaScript qua**; **10 test định tuyến/bảo vệ file qua**. Chrome với fixture HTTP qua tạo snapshot, lưu/mở lại nháp, zero/thập phân, lỗi giữ input, chốt/hủy, chống gửi lặp, lỗi tải lịch sử sau ghi, đổi kho, grant/revoke và phản hồi trễ, ADMIN, XSS, 6 kích thước. Đã xem ảnh desktop/mobile. Kiểm bố cục chung **150 kiểm tra = 25 trang × 6 kích thước**; hồi quy sổ tồn S5-05, chuyển kho S5-07 và điều hướng click/Back/Forward/Reload/mobile/reduced-motion qua, sidebar giữ scroll **472 -> 472**, không lỗi JavaScript.

Triển khai: cần khởi động lại backend để nhận entity/service mới và cập nhật schema theo cấu hình `ddl-auto=update`. Bổ sung `warehouse_inventories.version` (mặc định 0), metadata snapshot trên `warehouse_stock_count_items` (`snapshot_captured`, `snapshot_inventory_id`, `snapshot_inventory_version`) và nới ghi chú dòng lên 500 ký tự. Phiếu nháp cũ chưa có marker snapshot vẫn xem/hủy được nhưng cần lập lại để chốt an toàn, không tự gán tồn hiện tại vào snapshot cũ. Chưa nghiệm thu MySQL thật; không chạy SQL hay thay dữ liệu/schema DB đang dùng trong đợt kiểm thử này.

## S5-02 — Đặt lại đơn hàng cũ trên Cổng đại lý (backend) — 21/10/2026

Nguồn tiêu chí: User story S5-02 (*"Là Đại lý, tôi muốn đặt lại một đơn đã mua trước đây, để lấy hàng định kỳ mà không phải chọn lại từng mặt hàng"*). Thực hiện trên nhánh `feature/S5-02/dat-lai-don-da-mua`; phạm vi chỉ làm **backend** theo yêu cầu người dùng, tuân thủ AGENTS.md và ma trận phân quyền.

| Tiêu chí / hạng mục | Backend | Frontend | Kết nối / ma trận quyền |
|---|---|---|---|
| Chọn đơn cũ và đặt lại toàn bộ hoặc một phần dòng hàng | API xem trước `GET /api/portal/orders/{id}/reorder-preview` và tạo đơn `POST /api/portal/orders/{id}/reorder`. Hỗ trợ đặt lại toàn bộ (khi body `items` rỗng/null) hoặc đặt lại từng dòng đã chọn | Chưa làm (giữ nguyên theo yêu cầu "chỉ làm backend") | Preview yêu cầu `ORDER_VIEW` + `ORDER_CREATE` hoặc `ROLE_ADMIN`; Đặt lại yêu cầu `ORDER_CREATE` hoặc `ROLE_ADMIN`; tài khoản DEALER cách ly dữ liệu theo `dealerId`, đơn ngoài phạm vi trả 403 |
| Mặt hàng ngừng kinh doanh / hết bảng giá bị loại bỏ kèm cảnh báo | Phân loại `availableItems` vs `discontinuedItems` (kiểm tra `product.active == false` hoặc không có trong bảng giá hiệu lực hiện hành của nhóm khách hàng). Loại bỏ khỏi đơn mới và trả danh sách cảnh báo `warnings`/`excludedItems`; nếu toàn bộ bị loại bỏ trả 400 | Chưa làm (giữ nguyên theo yêu cầu "chỉ làm backend") | Kiểm tra trực tiếp từ bảng giá và trạng thái sản phẩm hiện hành trong DB; không tin dữ liệu client gửi |
| Áp lại giá theo bảng giá / chính sách hiện hành | Lấy bảng giá hiệu lực hiện hành theo nhóm khách hàng của đại lý tại thời điểm đặt lại; tính giá theo quy cách đơn vị quy đổi (`unitPrice = basePrice * factor`); không kế thừa giá cũ của đơn gốc | Chưa làm (giữ nguyên theo yêu cầu "chỉ làm backend") | Đơn giá được tính toán hoàn toàn phía server từ DB; preview hiển thị `originalUnitPrice` vs `currentUnitPrice` và cờ `priceChanged` |
| Đơn mới ở trạng thái Chờ duyệt | Đơn mới tạo thành công có trạng thái `PENDING_APPROVAL`, ghi nhận audit action `REORDER_FROM` kèm mã đơn gốc | Chưa làm (giữ nguyên theo yêu cầu "chỉ làm backend") | Quyền tạo đơn `ORDER_CREATE` kiểm tra động qua DB matrix; thu hồi quyền trên phiên JWT hiện tại lập tức chặn 403 không fallback role |

Kiểm chứng cuối: **24 test backend qua** (7 test service `OrderReorderServiceTest`, 14 test controller `DealerPortalControllerTest`, 3 test tích hợp `DealerPortalReorderIntegrationTest` trên HTTP/JWT/JPA/H2). Kiểm chứng đầy đủ: đặt lại toàn bộ/một phần, loại bỏ hàng ngừng kinh doanh / hết hiệu lực giá, tính lại giá theo bảng giá hiện hành, đơn mới tạo ở PENDING_APPROVAL, đại lý bị khóa/ngừng hoạt động trả lỗi, cách ly đa đại lý (đại lý khác truy cập trả 403), và thu hồi quyền `ORDER_CREATE` trên cùng token JWT lập tức trả 403 mà không fallback về role. Đã cập nhật hợp đồng tại `backend/API_DOCUMENTATION.md`.

## S5-08 — Kiểm kê kho và ghi nhận chênh lệch (Backend) — 21/10/2026

Nguồn yêu cầu: S5-08 theo ảnh mô tả. Đã đọc AGENTS.md và hai skill trong SKILLS. Thực hiện backend theo yêu cầu "chỉ làm backend".

| Tiêu chí / hạng mục | Backend | Frontend | Kết nối / ma trận quyền |
|---|---|---|---|
| Tạo phiếu kiểm kê theo kho hoặc theo nhóm hàng | Đã hoàn thành: `POST /api/warehouse/stock-counts`, chốt số lượng tồn sổ sách (`bookQuantity`) theo đơn vị cơ sở tại thời điểm tạo; lọc theo kho hoặc nhóm hàng; tự sinh mã `PKK-yyyyMMddHHmmssSSS`; trạng thái `DRAFT` | Chưa triển khai (chỉ làm backend theo yêu cầu) | Quyền `WAREHOUSE_VIEW` hoặc `WAREHOUSE_ADJUST` hoặc `ROLE_ADMIN`; kiểm tra phạm vi kho được phân công |
| Nhập số đếm thực tế, tính chênh lệch từng dòng | Đã hoàn thành: `PUT /api/warehouse/stock-counts/{id}/items`, nhận `actualQuantity` cho từng dòng sản phẩm, tự động tính `differenceQuantity = actualQuantity - bookQuantity` và lưu ghi chú dòng; chặn số lượng âm | Chưa triển khai (chỉ làm backend theo yêu cầu) | Quyền `WAREHOUSE_VIEW` hoặc `WAREHOUSE_ADJUST` hoặc `ROLE_ADMIN`; chỉ cập nhật khi phiếu ở trạng thái `DRAFT` |
| Chốt kiểm kê điều chỉnh tồn về số thực đếm và bắt buộc nhập lý do | Đã hoàn thành: `POST /api/warehouse/stock-counts/{id}/complete`, cập nhật tồn kho `WarehouseInventory.baseQuantity = actualQuantity`, bắt buộc nhập lý do `reason` (báo lỗi nếu để trống), chuyển trạng thái `COMPLETED`, ghi nhận AuditLog chi tiết | Chưa triển khai (chỉ làm backend theo yêu cầu) | Bắt buộc quyền `WAREHOUSE_ADJUST` hoặc `ROLE_ADMIN`; ghi nhận `completedBy`, `completedAt`, `adjustmentReason` |
| Chỉ Quản lý kho được chốt phiếu kiểm kê | Đã hoàn thành: Controller & Service bắt buộc quyền `WAREHOUSE_ADJUST` hoặc `ROLE_ADMIN`; Thủ kho (`ROLE_WAREHOUSE_STAFF`) không có quyền điều chỉnh bị chặn 403 Forbidden; thu hồi `WAREHOUSE_ADJUST` từ Quản lý kho có hiệu lực ngay lập tức (không fallback role) | Chưa triển khai (chỉ làm backend theo yêu cầu) | Ma trận phân quyền database-backed là nguồn sự thật duy nhất; đã kiểm thử cấp/thu hồi quyền trực tiếp trên MockMvc |
| Xem, tìm kiếm và hủy phiếu kiểm kê | Đã hoàn thành: `GET /options`, `GET /` (tìm kiếm/lọc kho, nhóm hàng, trạng thái, keyword, phân trang), `GET /{id}` (chi tiết kèm toàn bộ dòng kiểm kê), `POST /{id}/cancel` (hủy phiếu DRAFT) | Chưa triển khai (chỉ làm backend theo yêu cầu) | `WAREHOUSE_VIEW` hoặc `WAREHOUSE_ADJUST` hoặc `ROLE_ADMIN`; kiểm tra phạm vi kho |

Kiểm chứng: **28 test backend qua**, bao gồm 13 test `StockCountServiceTest` (tạo phiếu, snapshot tồn sổ theo kho/nhóm hàng, nhập số đếm, tính chênh lệch, chốt kiểm kê cập nhật tồn kho, bắt buộc lý do, chặn số âm/chưa đếm hết/phiếu đã chốt, kiểm tra quyền và phạm vi kho), 9 test `StockCountControllerTest` (endpoint REST, validation, HTTP status), 6 test `StockCountSecurityTest` (phân quyền ma trận, chặn 401 khi chưa đăng nhập, 403 khi thiếu quyền, chặn Thủ kho không được chốt kiểm kê, cho phép Quản lý kho và Admin chốt, thu hồi quyền WAREHOUSE_ADJUST lập tức trả 403).

## S5-09 — Tồn tối thiểu và cảnh báo (backend + frontend) — 20/10/2026

Nguồn AC: `4. Product Backlog!A62:F62`. Thực hiện trên nhánh `feature/S5-09-frontend-va-backend-canh-bao-ton-duoi-muc-toi-thieu`, nền `38b9795`; đã đọc AGENTS.md và hai skill trong SKILLS. Không merge/pull/lấy code nhánh khác, không tự commit.

| Tiêu chí / hạng mục | Backend | Frontend | Kết nối / ma trận quyền |
|---|---|---|---|
| Khai báo mức tối thiểu theo SKU và kho | Đã làm bảng riêng có unique SKU–kho, lượng decimal, đơn vị snapshot, người/thời điểm; transaction, khóa kho và audit | Đã làm `/warehouse/minimums`, menu **Kho hàng → Ngưỡng tồn tối thiểu**; tìm/lọc/phân trang, lưu/sửa/bỏ khai báo; lỗi lưu giữ input | GET options/list, PUT/DELETE `/warehouse/stock-minimums`; WAREHOUSE_MANAGE hoặc ADMIN; không cần VIEW/PRODUCT_VIEW và không lộ số tồn qua DTO cấu hình |
| Hàng dưới ngưỡng nổi bật trong sổ tồn | Bổ sung minimumQuantity/minimumUnit/minimumUnitMatches/belowMinimum và lọc server `belowMinimum=true` | Tô nền/viền cảnh báo kèm nhãn, có cột ngưỡng và bộ lọc; xuất Excel kèm ngưỡng/cảnh báo | WAREHOUSE_VIEW hoặc ADMIN; không thay ba lượng/trạng thái tồn S5-05, không tính nghiệp vụ bằng JS |
| Cảnh báo trên dashboard kho | GET `/warehouse/stock-alerts`, phân trang và phạm vi kho từ DB; không dùng cờ cache | Khối cảnh báo ở **Tổng quan**, tổng số SKU–kho và 5 dòng đầu, link mở sổ tồn đúng bộ lọc, tải lại/định kỳ 30 giây khi trang hiện | Chỉ WAREHOUSE_VIEW hoặc ADMIN; lỗi API không hiện thành không có hàng thiếu |
| Ngưỡng và thay đổi tồn | So tồn khả dụng (thực tế trừ giữ chỗ, không tính đang chuyển), nghiêm ngặt `<`; bằng ngưỡng/0/chưa khai báo không cảnh báo; SKU chưa có tồn = 0 mà không tự tạo tồn | Ghi rõ cơ sở so sánh; phân biệt chưa khai báo và mức 0; đơn vị đổi báo cần khai báo lại | Ngưỡng theo đúng kho/SKU/đơn vị; kho/SKU ngừng hoạt động không cảnh báo; không tự đổi snapshot cũ |
| Ma trận và phạm vi | Grant/revoke có hiệu lực ngay trên JWT cũ; MANAGE và VIEW độc lập; kho ngoài phạm vi trả 403; role kho chưa gán kho trả tập rỗng | Đồng bộ quyền trước nạp, thu hồi ẩn menu/đóng form/xóa dữ liệu cảnh báo; chặn trang cấu hình, không role fallback | Đã kiểm vai trò không mặc định (VIEWER), STAFF thiếu phân công rồi gán lại, Admin luôn được phép |
| Bố cục/tương thích | API tồn cũ giữ nguyên; cấu hình không sửa tồn thực tế/giữ chỗ | Dùng app-shell/app-navigation và lề chung, bảng cuộn nội bộ, form native dialog; desktop/mobile | Không lưu ngưỡng vào browser storage; tài liệu API và route đã cập nhật |

Kiểm chứng cuối: **583 test backend qua**, bao gồm 6 test S5-09 tích hợp HTTP/JWT/JPA/H2: ranh giới thập phân/equality, độc lập SKU–kho, mức 0/bỏ khai báo, tồn chưa phát sinh và biến động giữ chỗ, dữ liệu sai/đơn vị đổi/kho-SKU ngừng hoạt động, grant/revoke trên cùng token, scope kho và xóa hợp lệ SKU/kho không bị cấu hình cản trở. **88 test JS qua**, **8 test route/bảo vệ file liên quan qua**. Chrome `stock-minimums.browser.cjs` qua cấu hình/lưu/bỏ, giữ input khi lỗi, validation, XSS, lọc/tô cảnh báo sổ tồn, dashboard khi bổ sung hàng/lỗi API, live grant/revoke, không role fallback, ADMIN và 6 kích thước. Hồi quy `stock-ledger.browser.cjs` và `warehouse-transfers.browser.cjs` qua. Bố cục chung **144 kiểm tra trên 24 trang × 6 kích thước**; đã xem ảnh desktop/mobile/form 320px. Điều hướng chung qua click/Back/Forward/Reload/mobile/reduced-motion, sidebar giữ scroll **432 → 432**, không lỗi JS. `git diff --check` sạch. Bộ test route đầy đủ có assertion tên Cổng đại lý lệch có sẵn (xem các cập nhật trước); không đổi trang ngoài phạm vi.

Giới hạn: có bảng DB mới `warehouse_stock_minimums`; cần khởi động lại **backend** để tạo bảng theo `ddl-auto=update` và **server frontend** để nhận route. Chưa nghiệm thu MySQL thật, không thay dữ liệu/schema DB thật; kiểm tích hợp trên H2/MySQL-mode. Không triển khai kiểm kê S5-08 hoặc thay đổi nghiệp vụ giữ chỗ S5-06. Không tự commit/merge/push.

## S5-07 — Chuyển kho nội bộ (backend + frontend) — 20/10/2026

Nguồn tiêu chí: `4. Product Backlog!A60:F60`. Thực hiện trên nhánh `feature/S5-07-backend-va-frontend-chuyen_kho_noi_bo`, nền `develop` tại `c74485d`; không merge/pull/lấy code nhánh khác, không tự commit.

| Tiêu chí / hạng mục | Backend | Frontend | Kết nối và ma trận quyền |
|---|---|---|---|
| Chọn kho đi, kho đến, danh sách hàng và số lượng | Đã có; củng cố kiểm kho hoạt động/không trùng, SKU duy nhất, đơn vị quy đổi đóng băng, số lượng tối đa 4 số thập phân, mã tùy chọn/tự sinh | Đã làm màn `/warehouse/transfers`, menu **Kho hàng → Chuyển kho nội bộ**, tạo phiếu theo đơn vị cơ sở, tìm hàng theo mã/tên | POST `/warehouse/transfers`; options/products hẹp không đòi thêm PRODUCT_VIEW/WAREHOUSE_VIEW/ORDER_VIEW để tạo |
| Hàng đang chuyển chưa cộng kho đến | Trừ tồn thực tế kho đi, chỉ tăng inTransit ở kho đến; chặn dùng phần đã giữ chỗ cho đơn; khóa kho và dòng tồn | Giải thích rõ hai mốc xuất/nhận, hiển thị trạng thái từ API | Dùng cùng sổ tồn với S5-05, không suy lượng giữ chỗ bằng danh sách đơn trên trình duyệt |
| Kho đến xác nhận thực nhận | Khóa phiếu, cộng đúng thực nhận, gỡ lượng đang chuyển của phiếu; chặn thao tác lặp/đồng thời | Chi tiết có số đã chuyển/thực nhận, Điền nhận đủ và Xác nhận thực nhận | Chỉ WAREHOUSE_TRANSFER/ADMIN và kho đến trong phạm vi; server trả canReceive |
| Chênh lệch bắt buộc lý do | Thiếu/thừa/nhận 0 đều yêu cầu lý do; lưu theo dòng, snapshot hệ số, transaction rollback khi lỗi | Nhập và đối chiếu chênh lệch theo dòng; không mất số lượng/lý do khi POST lỗi | API kiểm lại tất cả dòng; chặn ID giả, trùng, mâu thuẫn hoặc thiếu dòng |
| Hủy khi đang chuyển | Bắt buộc lý do, hoàn tồn kho đi và gỡ inTransit; đã nhận/đã hủy không thao tác lại | Hiện thao tác theo quyền/phạm vi; xem lại lý do hủy | Chỉ WAREHOUSE_TRANSFER/ADMIN và kho đi trong phạm vi; server trả canCancel |
| Xem, tìm/lọc và phân trang | Danh sách/chi tiết chỉ phiếu có một đầu kho trong phạm vi; lọc kho/trạng thái/mã, phân trang server | Có bộ lọc, tải lại, phân trang, lỗi/rỗng rõ ràng, hủy phản hồi cũ | WAREHOUSE_VIEW hoặc WAREHOUSE_TRANSFER; quyền được cấp cho vai trò bất kỳ đều áp dụng; không role fallback |
| Lưu ma trận quyền và bố cục | JWT hiện tại bị thu hồi quyền trả 403; quyền role kho không thay thế quyền bị bỏ; ADMIN luôn được phép | Thu hồi TRANSFER thành chỉ đọc nếu còn VIEW; mất cả hai thì ẩn menu/chặn trang; đóng form/chi tiết và hủy read đang chờ | Dùng shell/topbar/transition chung, lề cân và bảng cuộn nội bộ; không lưu nghiệp vụ vào browser storage |

Kiểm chứng: **577 test backend qua**, bao gồm HTTP/JWT/JPA/H2 và các ca chuyển vượt tồn, cạnh tranh với giữ chỗ đơn hàng, nhận/hủy đồng thời, nhận thừa/thiếu, rollback nhiều dòng, snapshot hệ số và grant/revoke trên token cũ. **87 test JS qua**; test route S5-07/S5-05 qua. Chrome `warehouse-transfers.browser.cjs` qua luồng lập/nhận/hủy, XSS, lỗi/retry và giữ input, phản hồi cũ, ma trận/ADMIN, sáu kích thước 1920/1440/1024/768/390/320. Kiểm bố cục chung: **138 kiểm tra trên 23 trang × 6 kích thước**; đã xem ảnh desktop/mobile.

Kiểm tra điều hướng chung qua: click/Back/Forward/Reload, nội dung trang, mobile và reduced-motion; sidebar giữ scroll **392 → 392**, không có lỗi JS. **7 test route/bảo vệ file liên quan qua**, `git diff --check` sạch. Bộ route đầy đủ vẫn có assertion tên Cổng đại lý lệch trước đợt này, không sửa trang ngoài phạm vi.

Giới hạn: chưa chạy trên MySQL thật, không thay đổi dữ liệu/schema DB thật. Cần khởi động lại **backend và server frontend** để nhận service/API/route mới. Không thay đổi nghiệp vụ S5-06 hoặc triển khai kiểm kê/cảnh báo S5-08/09.

## S5-08 — Kiểm kê kho và ghi nhận chênh lệch (Frontend hoàn thiện) — 20/10/2026

User Story: Là Quản lý kho, tôi muốn thực hiện kiểm kê kho và ghi nhận chênh lệch, để đưa sổ sách về khớp thực tế và biết lệch ở đâu.
- Vị trí giao diện: Phân hệ **Kho hàng → Đơn vị tính / Kiểm kê kho** (`/warehouse/units?tab=stocktake`), tab **Kiểm kê kho** (`#tab-stocktake`).
- Tuân thủ ma trận phân quyền: `ROLE_ADMIN` toàn quyền; `ROLE_WAREHOUSE_MANAGER` yêu cầu quyền `WAREHOUSE_ADJUST`; các vai trò khác không được phép chốt phiếu.

| Hạng mục / Tiêu chí chấp nhận (AC) | Backend | Frontend | Kết nối / Ma trận quyền (BE ↔ FE) |
|---|---|---|---|
| **1. Khởi tạo phiếu kiểm kê (Freeze Snapshot)**: Chọn phạm vi kiểm kê toàn kho hoặc theo nhóm hàng, hệ thống chốt tồn sổ sách tại thời điểm tạo | Đã có sẵn: `GET /api/warehouse/inventory` (lấy tồn sổ thực tế) và `GET /api/warehouse/options` (danh sách kho). | Đã hoàn thành: Khởi tạo phiếu kiểm kê với mã `PKK-YYYYMM-XXX`, phạm vi Toàn kho hoặc Nhóm hàng; chốt đóng băng số tồn sổ sách (`bookQuantity = inventoryBaseQty`); khởi tạo số đếm thực tế bằng số tồn sổ, chênh lệch ban đầu 0 (`MATCH`). | Đã kết nối: Cần quyền `WAREHOUSE_VIEW` để truy cập tab và tải tồn kho; không phụ thuộc role cứng; thu hồi quyền xem ẩn tab kiểm kê. |
| **2. Kiểm đếm thực tế & tính chênh lệch từng dòng**: Nhập số thực tế đếm, hệ thống tự động tính toán chênh lệch (Khớp, Thừa, Thiếu) kèm nhãn trực quan | Không xử lý tính toán dòng hàng tạm thời (chỉ nhận kết quả khi chốt phiếu). | Đã hoàn thành: Tính chênh lệch `actualQuantity - bookQuantity` theo thời gian thực; hiển thị badge màu xanh lá (Khớp - 0), xanh ngọc (Thừa - `+X`), đỏ (Thiếu - `-X`); ô nhập ghi chú chênh lệch từng dòng; strip 4 thẻ chỉ số tổng quan (Tổng mặt hàng, Khớp, Thừa, Thiếu) cập nhật trực tiếp khi nhập. | Logic hoàn toàn reactive trên frontend; module hóa độc lập tại `frontend/js/warehouse-stocktake.js` phục vụ cả UI và unit test. |
| **3. Bắt buộc nhập lý do điều chỉnh**: Khi chốt phiếu kiểm kê, bắt buộc nhập lý do điều chỉnh tồn kho phục vụ kiểm toán | Kiểm tra `request.getAdjustmentReason()` hoặc ghi chú trong `WarehouseServiceImpl.recordVoucher`. | Đã hoàn thành: Validate bắt buộc nhập lý do vào ô `#stocktake-adjustment-reason` (chặn chuỗi rỗng / khoảng trắng); hiển thị cảnh báo toast nếu thiếu. | Đã kết nối: Lý do được gửi trong payload lên API `POST /api/warehouse/adjust` qua trường `adjustmentReason` và `note`. |
| **4. Chốt kiểm kê & Cân bằng tồn kho**: Điều chỉnh tồn sổ sách về số thực đếm, lưu phiếu điều chỉnh kho | Đã có sẵn: API `POST /api/warehouse/adjust` (`@PreAuthorize("hasRole('ADMIN') or hasAuthority('WAREHOUSE_ADJUST')")`), `type = ADJUSTMENT`, cập nhật `inventory.setBaseQuantity(baseQty)` cho từng mặt hàng khi không truyền delta. | Đã hoàn thành: Gửi request `POST /api/warehouse/adjust` với danh sách mặt hàng có `quantity` bằng số đếm thực tế; cập nhật trạng thái phiếu sang `RECORDED`; đưa vào lịch sử kiểm kê; tự động reload lại tồn kho và sổ cái. | Đã kết nối: API nhận payload, cập nhật `baseQuantity` trong bảng tồn kho khớp chính xác với số đếm thực tế; trả về voucher `PDC`/`PKK` trạng thái `RECORDED`. |
| **5. Phân quyền chốt phiếu (Chỉ Quản lý kho có quyền WAREHOUSE_ADJUST)**: Không cho phép vai trò khác chốt phiếu | `WarehouseController.adjustInventory` kiểm tra `@PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_ADJUST'))")`. | Đã hoàn thành: `canAdjustInventory` kiểm tra người dùng là `ROLE_ADMIN` hoặc (`ROLE_WAREHOUSE_MANAGER` có quyền `WAREHOUSE_ADJUST`). Nếu không đủ quyền: vô hiệu hóa nút `#btn-confirm-stocktake`, hiển thị cảnh báo `#stocktake-role-hint`, chặn thao tác click và validate. | Đã kết nối: Khi thu hồi quyền `WAREHOUSE_ADJUST` khỏi `ROLE_WAREHOUSE_MANAGER`, backend trả về 403 Forbidden và frontend vô hiệu hóa nút chốt phiếu ngay trên phiên hiện tại. ROLE_SALES, ROLE_WAREHOUSE_STAFF không được phép chốt phiếu. |
| **6. Lưu nháp tiến độ kiểm kê**: Cho phép lưu tạm tiến độ đếm mà không tác động tới tồn kho | Không đổi | Đã hoàn thành: Nút `#btn-save-draft-stocktake` lưu trạng thái đếm và ghi chú của các dòng hàng hiện tại; giữ trạng thái `DRAFT`; không gọi API ghi sổ điều chỉnh kho. | Lưu nháp an toàn cục bộ trong phiên làm việc của thủ kho / quản lý kho. |

Kiểm chứng: **92 test JavaScript qua** (toàn bộ test suite không có failure), bao gồm 6 bài test chuyên sâu cho S5-08 trong `tests/warehouse-stocktake-s5-08.test.cjs`:
1. Chốt số tồn sổ tại thời điểm tạo phiếu (Toàn bộ kho hoặc theo nhóm hàng).
2. Tự động tính chênh lệch từng dòng và tổng hợp metrics (Khớp, Thừa, Thiếu).
3. Bắt buộc nhập lý do khi chốt phiếu kiểm kê.
4. Xây dựng payload chuẩn cho API `POST /api/warehouse/adjust` đưa tồn kho về số thực đếm.
5. Kiểm tra nghiêm ngặt ma trận phân quyền: ROLE_ADMIN luôn được phép, ROLE_WAREHOUSE_MANAGER phải có WAREHOUSE_ADJUST, thu hồi quyền bị chặn ngay lập tức, các vai trò khác (STAFF, SALES) bị từ chối.
6. Chặn số lượng thực tế âm hoặc không hợp lệ.

## S5-05 — Sổ tồn theo SKU và kho (backend + frontend) — 20/10/2026

Nhánh `feature/S5-05-frontend-va-backend-xem_so_ton_kho_theoSKU_va_kho`, bắt đầu từ develop `b6b07a4`. Đã đọc AGENTS.md và cả hai skill trong SKILLS. Menu **Kho hàng → Tồn kho**, URL `/warehouse/inventory`; link/tab cũ `?tab=inventory` chuyển tới trang mới. Trang dùng app-shell, app-navigation và khung nội dung chung, không đặt outer width/gutter riêng.

| AC / hạng mục | Backend | Frontend | Kết nối / ma trận quyền |
|---|---|---|---|
| Ba cột tồn thực tế, giữ chỗ, khả dụng theo SKU/kho | API read-only mới `/api/warehouse/stock-ledger`; truy vấn DB phân trang, khả dụng không âm, không cộng hàng đang chuyển; SKU chưa có bản ghi tồn trả 0 qua LEFT JOIN, không ghi DB | Hiển thị ba cột riêng, đơn vị theo sổ tồn đã ghi nhận, kho và nhóm hàng ở từng dòng; không cộng tổng lượng giữa SKU khác đơn vị | `WAREHOUSE_VIEW` hoặc ADMIN ở cả controller/service và FE; không phụ thuộc PRODUCT_VIEW, ORDER_VIEW hoặc quyền nhập kho |
| Lọc kho, nhóm hàng, trạng thái tồn; tìm mã/tên | Lọc trên server, keyword không phân biệt hoa thường và escape `%/_/!`; trạng thái IN_STOCK/OUT_OF_STOCK/FULLY_RESERVED; metadata bộ lọc từ `/options` | Tìm theo mã/tên, lọc kết hợp, đặt lại, phân trang/đổi số dòng, phản hồi chậm không ghi đè bộ lọc mới | Tập kho lấy lại từ DB mỗi request; tài khoản đã gán kho chỉ xem kho được gán; role kho thiếu phân công không được fallback toàn hệ thống; ID kho ngoài phạm vi trả 403 |
| Lưu thay đổi ma trận quyền | Token đang đăng nhập mất WAREHOUSE_VIEW bị chặn 403 ở cả list/options; role kho/MANAGE/IMPORT không thay thế quyền này; ADMIN luôn được phép | Thu hồi quyền ẩn menu/chặn trang, xóa dòng đang xem và hủy yêu cầu/xuất đang chờ | Đã kiểm tài khoản STAFF chỉ có VIEW, revoke rồi grant lại cùng token; VIEWER chỉ có VIEW dùng được trang không cần role kho |
| Xuất Excel và trạng thái lỗi | Dùng cùng API và bộ lọc, mỗi trang export kiểm lại quyền/phạm vi | Xuất toàn bộ kết quả bộ lọc đã áp dụng, đủ ba lượng tồn; lỗi tải không biến thành tồn 0, có Tải lại; thiếu kho/không có kết quả có hướng dẫn rõ | Không trả giá bán/giá vốn; không lưu nghiệp vụ vào browser storage; đổi bộ lọc hoặc mất quyền hủy xuất |
| Tương thích và bố cục | Không thêm schema, seed, quyền mới; không đổi API inventory cũ phục vụ S4 | Giữ sidebar/topbar/chuyển cảnh chung, bảng cuộn bên trong trên màn hình nhỏ; các tab đơn vị tính/chứng từ/sổ cái cũ giữ nguyên | Không merge, pull, chuyển nhánh hoặc tự commit |

Kiểm chứng cuối: **68 test backend qua** (StockLedgerControllerTest 3, StockLedgerServiceTest 5, WarehouseControllerTest 10, WarehouseServiceTest 28, SprintMergeIntegrationTest 22). Tích hợp dùng HTTP/JWT/JPA/H2, kiểm lượng thập phân và đơn vị gốc, SKU chưa có tồn không tạo bản ghi, trạng thái, tìm ký tự đặc biệt, phân trang, phạm vi kho và thu hồi quyền trên phiên cũ. **86 test JS qua**; **6 test route/bảo vệ file liên quan qua**. Chrome `tests/stock-ledger.browser.cjs` kiểm ba cột/bộ lọc/phân trang/export toàn bộ, lỗi/retry/empty, phản hồi cũ, chống chèn HTML, sáu kích thước và grant/revoke/ADMIN. `tests/content-layout.browser.cjs`: **132 kiểm tra trên 22 trang × 6 kích thước**, lề cân và không tràn ngang toàn trang; đã xem ảnh desktop/mobile. `tests/navigation.browser.cjs` qua, sidebar giữ scroll 352→352 qua điều hướng; `git diff --check` sạch.

Giới hạn: chưa chạy với MySQL thật; không đổi dữ liệu DB thật. Cần khởi động lại **backend và server frontend** để nhận API/route mới. Không gộp cảnh báo tồn tối thiểu S5-09 vào trạng thái tồn S5-05. Case test route cũ lệch tên Cổng đại lý ngoài phạm vi đã được ghi ở phần S5-03; không sửa nội dung trang đó.

## S5-03 — Mã vị trí tùy chọn, tự sinh tại backend — 20/10/2026

| Hạng mục | Backend | Frontend | Kết nối / ma trận quyền |
|---|---|---|---|
| Tạo khu/kệ với mã để trống | Tự sinh theo từng kho: `KHU-001`, `KE-001`; lấy số lớn nhất của tiền tố hiện có rồi tăng, bỏ qua mã chữ tùy chỉnh; khóa ghi trên kho và unique constraint chống trùng đồng thời | Bỏ dấu bắt buộc và required của mã vị trí; có hướng dẫn để trống tự sinh, gửi mã rỗng và hiển thị mã từ API, không sinh ở JavaScript | Giữ `WAREHOUSE_MANAGE` hoặc ADMIN cho mọi POST/PUT; không thêm quyền/fallback role |
| Mã riêng và sửa vị trí | Mã riêng chỉ trim/viết hoa như trước, trùng trong cùng kho trả lỗi; khác kho được cùng mã; sửa với mã trống/null giữ mã hiện có | Cho nhập mã nhãn có sẵn; sửa mã để trống có giải thích giữ mã cũ | Đã kiểm cấp quyền rồi thu hồi ngay trên cùng token, POST mã trống trả 403 sau thu hồi |

Kiểm chứng lần bổ sung: **59 test backend qua** (12 controller, 28 service, 19 tích hợp H2/JWT/JPA). Kiểm bốn POST tạo kệ đồng thời nhận bốn mã khác nhau, mã tay `KE-009` khiến mã tiếp theo bắt đầu `KE-010`, kho khác bắt đầu `KE-001`, chuỗi quá 999, giữ mã khi sửa và chặn mã tay trùng. **85 test JavaScript qua**; Chrome kiểm form không bắt buộc mã, hiển thị mã backend trả về và giữ mã khi sửa trống, cùng các luồng/quyền/kích thước của trang. Đã xem ảnh form. Không thay schema hoặc MySQL thật; cần khởi động lại backend. **Mã kho vẫn nhập bắt buộc**, chỉ mã vị trí được tự sinh. Cập nhật hợp đồng tại backend/API_DOCUMENTATION.md.

## S5-03 — Frontend quản lý nhiều kho và vị trí lưu — 20/10/2026

Nhánh `feature/S5-03-frontend-quan_ly_nhieu_kho_va_vi_tri_luu`. Menu **Kho hàng → Kho & vị trí lưu**, URL `/warehouses`. Đọc AGENTS.md và cả hai SKILLS; tái sử dụng app-shell/app-navigation, nội dung nằm trong khung chung, không tạo lề/chiều rộng ngoài riêng.

| Hạng mục / AC | Backend | Frontend | Kết nối / ma trận quyền |
|---|---|---|---|
| Nhiều kho: mã, tên, địa chỉ, người phụ trách, trạng thái | Tái sử dụng GET/POST/PUT `/api/warehouses` | Danh sách, tìm kiếm/lọc trạng thái, thêm/sửa kho, người phụ trách nhập tên | Xem với `WAREHOUSE_VIEW` hoặc `WAREHOUSE_MANAGE`; thay đổi với `WAREHOUSE_MANAGE`; không cần quyền xem tài khoản |
| Vị trí lưu: khu/kệ trong từng kho | Tái sử dụng API vị trí; sửa bỏ cha bằng `parentLocationId=0`; chặn khu con/cycle và đổi khu đang chứa con thành kệ | Chọn kho xem vị trí; thêm/sửa khu/kệ, chọn khu cha trong cùng kho, mô tả và trạng thái | Cùng quyền xem/quản lý trên cả FE và BE; không tự đổi kiểu vị trí cũ khi sửa |
| Kho phục vụ mặc định của đại lý | Bổ sung projection tối thiểu GET/PUT `/api/warehouses/dealer-assignments`; lưu DB và audit; chặn kho ngừng hoạt động | Danh sách đại lý có tìm kiếm/phân trang, gán/chuyển kho mặc định, không sửa kho đơn hàng đã có | Chỉ `WAREHOUSE_MANAGE` hoặc ADMIN; không yêu cầu thêm quyền khách hàng/tài chính, không trả dữ liệu công nợ |
| Sửa lỗi audit khi gán kho | Mã cũ 24 ký tự vượt cột action VARCHAR(20), làm rollback; đổi thành `ASSIGN_WAREHOUSE`, cả luồng mới và endpoint khách hàng cũ | Báo thành công sau API thành công; giữ dữ liệu form và báo lỗi server nếu thất bại | Không đổi schema hoặc dữ liệu DB thật; không nới quyền endpoint khách hàng cũ |
| Cấp/thu hồi quyền trên phiên đang mở | API lấy quyền hiện tại, không fallback role quản lý kho | Đồng bộ trước khi ghi; thu hồi quyền đóng form và ẩn thao tác; giữ quyền xem thì chỉ đọc, mất cả hai quyền chuyển 403 | ADMIN vẫn toàn quyền; kiểm bằng tài khoản VIEWER chỉ có quyền được cấp, không có role thứ hai |

Kiểm chứng: **80 test backend qua**, gồm WarehouseResourceControllerTest, WarehouseServiceTest, DealerServiceTest và **18 test tích hợp HTTP/JWT/JPA/H2**, có gán kho/lưu vị trí và thu hồi quyền ngay trên cùng access token. **85 test JavaScript qua**. `tests/warehouses.browser.cjs` chạy Chrome/API mock kiểm thêm/sửa kho, khu/kệ, bỏ cha, gán đại lý, lỗi lưu, grant/revoke, readonly, ADMIN và 6 kích thước 1920/1440/1024/768/390/320. `tests/content-layout.browser.cjs` kiểm **126 trường hợp trên 21 trang × 6 kích thước**, khung cân lề, không tràn ngang toàn trang. Đã xem ảnh desktop/mobile. Thêm kiểm thử route `/warehouses` và legacy URL.

Giới hạn kiểm chứng: không chạy/lưu dữ liệu vào MySQL thật; backend sửa cần được khởi động lại để có endpoint mới. Bộ test route cũ có một assertion ngoài S5-03 đang chờ chuỗi “Cổng đại lý” trong khi trang hiện tại dùng “Cổng đặt hàng đại lý”; giữ nguyên nội dung trang đó, không coi lỗi có sẵn này là lỗi S5-03. Không merge, pull, đổi nhánh hay tự commit.

## Màu thông báo ma trận quyền — 20/10/2026

| Hạng mục | Backend | Frontend | Kết nối / phân quyền |
|---|---|---|---|
| Phản hồi lưu/khôi phục quyền | Không đổi | Thành công nền xanh nhạt/chữ xanh; không có thay đổi dùng màu trung tính; lỗi thật vẫn đỏ. Reset trạng thái màu theo từng thông báo, dùng role status/alert tương ứng | Giữ nguyên API, payload, kiểm quyền ADMIN và đồng bộ phiên; chỉ đổi hiển thị |

Chrome/API mock: `tests/permission-feedback.browser.cjs` kiểm lưu xanh, không thay đổi không gọi PUT, lỗi sau thành công đổi về đỏ, khôi phục mặc định xanh; đã xem ảnh. **84 test JS hiện có qua**. Không sửa hoặc lưu ma trận DB thật, giữ nguyên các thay đổi khác đang có.

## Rút gọn giá trị dài trong Nhật ký thao tác — 20/10/2026

| Hạng mục | Backend | Frontend | Kết nối / phân quyền |
|---|---|---|---|
| Giá trị trước/sau trong danh sách | Không đổi | Giới hạn nhãn 240px, hiển thị ellipsis khi dài; bỏ giới hạn % gây vòng lặp kích thước trong table auto-layout | Chỉ cách hiển thị; không cắt dữ liệu API hoặc đổi quyền truy cập nhật ký |
| Đọc đầy đủ | Không đổi | Tooltip chứa nguyên văn; nút Xem giữ toàn bộ trước/sau; bảng trong modal tự xuống dòng kể cả chuỗi không có dấu cách | Giữ escape HTML cả nội dung lẫn thuộc tính title; không đổi dữ liệu xuất Excel |

Kiểm chứng: `tests/audit-values.browser.cjs` chạy Chrome/API mock ở **1920/1440/390/320px**, chuỗi hàng nghìn ký tự có/không có khoảng trắng, giá trị ngắn và null: ellipsis hoạt động, bảng không bị kéo rộng hàng nghìn pixel, toàn trang không tràn ngang, popup còn nguyên dữ liệu và không chèn được HTML. Đã xem ảnh desktop/mobile; **84 test JS hiện có qua**. Không sửa backend/DB, giữ nguyên các thay đổi Cổng đại lý đang có trong worktree.

## Cổng đại lý dùng menu và khung nội dung chung — 20/10/2026

Phần này thay thế mô tả cổng đại lý “standalone” ở các mốc trước bên dưới.

| Hạng mục | Backend | Frontend | Kết nối / phân quyền |
|---|---|---|---|
| Menu trên Cổng đại lý | Không đổi | Dùng `app-shell.js` và `app-navigation.js`; có sidebar, breadcrumb, profile/logout như các trang khác; khung `.content` dùng lề/chiều rộng chung | Menu và guard dùng ma trận hiện tại; ADMIN thấy chức năng quản trị, DEALER không nhận thêm quyền |
| Giỏ hàng và ngữ cảnh đại lý | Không đổi | Chuyển các node điều khiển vào slot thanh trên, không clone/trùng ID; giữ chọn đại lý ADMIN, catalog, giỏ và preview | Giữ toàn bộ endpoint `/portal/*`, không gọi API thiếu ngữ cảnh và không đổi phạm vi dữ liệu |
| Điện thoại | Không đổi | Nút mở/đóng menu chung; thanh giỏ dưới thuộc vùng nội dung, trở thành inert khi menu mở; drawer/modal ở ngoài shell để không bị khóa theo menu | Thu hồi ORDER_CREATE khi đang mở cổng vẫn chuyển 403, không chỉ ẩn một nút |

Kiểm chứng: **84 test JS qua**. Chrome thật/API mock kiểm ADMIN chọn đại lý rồi tải catalog, DEALER có menu đúng quyền, giỏ mở từ thanh trên và thanh dưới, mobile menu mở/đóng, thu hồi quyền và các luồng S4 hiện có; không pageerror. Đã xem ảnh desktop và mobile. Bài căn lề mở rộng thành **20 trang/tab × 6 kích thước = 120 ca qua**, gồm Cổng đại lý tại 1920/1440/1024/768/390/320px; hồi quy điều hướng, giữ cuộn và transition chờ quyền qua. Không thay đổi backend, không chạy/sửa DB thật.

## Menu đứng yên, chỉ chuyển cảnh nội dung — 20/10/2026

| Hạng mục | Backend | Frontend | Kết nối / phân quyền |
|---|---|---|---|
| Giữ menu khi đổi chức năng | Không đổi | Giữ snapshot menu cũ đứng yên, không fade; chỉ nhả sau khi menu trang mới khôi phục cuộn và đồng bộ quyền xong | Giữ `Auth.ready`, menu inert khi chờ; không cấp quyền bằng snapshot |
| Chỉ chuyển cảnh phần trắng | Không đổi | Đặt tên transition trên khung nội dung thực, không chụp cả `.main-area`; fade 100–160ms, không trượt; topbar nằm trong nền tĩnh để tránh blur chữ | Không sửa DOM form, replay script, gom trang thành SPA hay cache dữ liệu nghiệp vụ |
| Mạng chậm / chuyển nhanh | Không đổi | Watchdog 1 giây chỉ kết thúc giữ ảnh, không mở khóa quyền; xử lý transition bị hủy và trang đang parse; callback cũ không phá transition mới | Backend vẫn kiểm ma trận ở từng request; frontend vẫn chặn 403 khi bị thu hồi quyền |
| Reload / Back / Forward / mobile | Không đổi | Giữ điều hướng HTML gốc, history và vị trí menu; giảm chuyển động tắt transition; browser không hỗ trợ dùng tải trang bình thường | Không can thiệp liên kết, submit, tải file hay API; không sửa DB |

**Lưu ý chính xác về kiến trúc:** đây là giữ nguyên menu **về hiển thị**, không phải chỉ fetch một mảnh HTML. Trình duyệt vẫn nạp tài liệu đích và các script khởi tạo riêng như cũ, nên không phải di trú vòng đời của từng trang sang SPA/PJAX. Dùng [cross-document view transitions của trình duyệt](https://developer.chrome.com/docs/web-platform/view-transitions/cross-document); không cam kết hiệu ứng này trên browser không hỗ trợ.

Kiểm chứng: **84 test JS qua**. Chrome thật/API mock: `tests/menu-transition.browser.cjs` xác nhận menu cũ opacity 1 và đứng yên khi chờ quyền, menu mới opacity 0, chỉ khung nội dung có tên transition, cuộn 312px → 312px; form tạo tài khoản mở được, watchdog không mở menu khi quyền chưa đồng bộ, chuyển trang liên tiếp và thu hồi quyền của VIEWER đều qua, không pageerror. Đã xem ảnh giữa/sau chuyển cảnh và sửa lỗi topbar bị blur trong snapshot. `tests/navigation.browser.cjs` qua Back/Forward/Reload/mobile/reduced-motion; `tests/s4-workflows.browser.cjs` qua giỏ/xác nhận/vòng đời/thu hồi quyền; `tests/content-layout.browser.cjs` qua **114 ca căn lề**. Không thử MySQL thật, không thay đổi logic backend, không merge/chuyển nhánh.

## Đồng bộ căn lề các trang trong menu — 20/10/2026

| Hạng mục | Backend | Frontend | Kết nối / phân quyền |
|---|---|---|---|
| Khung nội dung chung | Không thay đổi | Căn giữa cùng khung tối đa 1.240px như Chiết khấu sản lượng; lề tối thiểu 28px desktop, 16px mobile; đồng nhất khoảng cách trên/dưới | Chỉ CSS, giữ nguyên API, page guard và nội dung; không đổi sidebar/topbar |
| Các trang đang lệch trái | Không thay đổi | Áp dụng cho Tổng quan/Hạn mức công nợ, hồ sơ, sản phẩm/nhóm hàng, đơn hàng/tạo/duyệt/đơn đại lý, bảng giá/chiết khấu, đại lý/NCC, bốn tab kho, tài khoản và nhật ký | Không thêm quyền, không đổi điều kiện hiển thị menu/action; các bảng rộng vẫn cuộn trong bảng, không che cột bằng CSS |
| Bộ lọc Đơn hàng trên điện thoại | Không thay đổi | Cho hai trường ngày tự xuống dòng; bỏ flex-basis dạng hàng khi toolbar chuyển thành cột để không tạo chiều cao trống lớn | Giữ đủ trường lọc và sự kiện, không đổi logic tìm/lọc |

Kiểm chứng: `tests/content-layout.browser.cjs` chạy Chrome thật với API mock, **19 trang/tab × 6 chiều rộng (1920/1440/1024/768/390/320) = 114 ca qua**: lề trái/phải bằng nhau, đủ gutter, khung không vượt 1.240px, không tràn ngang toàn trang; sidebar giữ 260px, không pageerror. Đã xem ảnh desktop và mobile. Cổng đại lý độc lập, trang auth/in và modal không bị áp khung này.

Hồi quy: **79 test JS qua**, bài browser giữ cuộn menu **312px → 312px**, Back/Forward/Reload/mobile/reduced-motion qua; bài browser S4 (giỏ, xác nhận, vòng đời, thu hồi quyền đang mở trang) qua. Không thay đổi backend/DB; chưa kiểm mọi browser hoặc dữ liệu thật.

## Sửa các phát hiện còn lại Sprint 4 — 20/10/2026

Phần này cập nhật kết quả sau audit bên dưới; các nhận xét “chưa sửa” trong audit là mốc trước lần sửa này.

| Story | Backend | Frontend | BE ↔ FE / ma trận quyền |
|---|---|---|---|
| S4-01 | Đồng nhất giá ngày đặt với preview/catalog, không đổi giá theo ngày giao; chặn sản phẩm ngừng bán và pricing ngoài đại lý được giao | Dùng lại API tính giá | ORDER_CREATE và phạm vi dữ liệu hiện hành; API không tin giá client |
| S4-02 | Công nợ/quá hạn không biến mất khi soạn/xuất/giao/đóng đơn; summary đại lý dùng cùng tập trạng thái | Hiển thị dữ liệu nợ server | Giữ FINANCE_VIEW_DEBT và ORDER_CREATE cho các luồng tương ứng |
| S4-03 | Tạo/tính đơn/kiểm tồn dùng đúng kho phục vụ; chặn đổi kho, kho thiếu hoặc ngừng hoạt động; giữ cơ chế khóa tồn hiện có | Nhận lỗi có lý do từ API, không chọn kho thay đại lý | Quyền không thay thế ràng buộc kho; có kiểm thử HTTP sai kho và đồng thời |
| S4-04 | Không phát hiện thêm lỗi cần sửa trong lần rà này | Giữ lịch sử mua hàng | Giữ CUSTOMER_VIEW, giới hạn đại lý |
| S4-05 | Bỏ fallback duyệt từ ROLE_SALES/ORDER_CREATE và lời gọi không xác thực; xác nhận thường tách endpoint riêng | Luồng duyệt/từ chối/trả lại giữ nguyên | ORDER_APPROVE; quyền xác nhận không được xử lý vi phạm |
| S4-06 | Trả bước vòng đời tiếp theo theo quyền và kho của người gọi | Thêm nút chuyển bước trong Đơn hàng → Chi tiết; tải lại tracking sau thao tác | WAREHOUSE_EXPORT + kho được giao; bỏ quyền 403 và ẩn nút |
| S4-07 | Giữ danh sách, bộ lọc, tổng tập lọc | Tải lại khả năng thao tác khi quyền đổi | ORDER_VIEW, không dùng role để cấp quyền |
| S4-08 | Giữ in/PDF/barcode và giới hạn dữ liệu | Không đổi | ORDER_VIEW; không phát hiện thêm lỗi trong các ca hồi quy |
| S4-09 | Giữ copy nháp và tính lại giá/chiết khấu | Không đổi | ORDER_CREATE + ORDER_VIEW; các ca hồi quy hiện có qua |
| S4-10 | Thêm preview server; ORDER_CONFIRM riêng, đúng đại lý phụ trách; kiểm công nợ/tồn trước xác nhận | Giỏ hiển thị chiết khấu/tổng/nợ từ server; chặn preview lỗi; chống phản hồi trễ; giá đổi cần xác nhận lại | Preview dùng ORDER_CREATE + PRODUCT_VIEW + ORDER_VIEW; xác nhận ORDER_CONFIRM, không cấp quyền duyệt ngoại lệ |

**Triển khai:** Với ma trận đã lưu trong DB, Admin phải tích **Xác nhận đơn đại lý** (`ORDER_CONFIRM`) cho vai trò cần xác nhận rồi lưu. Không tự thêm quyền vào cấu hình cũ, không sửa DB thật, không merge/chuyển nhánh.

Kiểm chứng: **538 test backend, 79 test JS, 5 test định tuyến qua**, không failure/error/skip; hai bài Chrome hồi quy điều hướng và luồng S4 đều qua. Chi tiết và giới hạn trong [báo cáo sửa Sprint 4](docs/SPRINT_4_FIXES_20261020.md). Chưa tuyên bố nghiệm thu dữ liệu MySQL thật hoặc tích hợp thu tiền; đóng đơn không tự coi là thanh toán.

## Đối chiếu Excel Sprint 4 và sửa chuyển trang — 20/10/2026

Đã đọc sheet `4. Product Backlog`, `A42:F51`, đối chiếu AC với BE/FE trên nhánh hiện tại (không lấy cột trạng thái Excel làm trạng thái code).
**Chưa thể nghiệm thu toàn Sprint 4**: S4-01/04/05/06/07/08/09 cơ bản có luồng chính; S4-02 còn tính nợ chỉ từ RECORDED, S4-03 chưa ràng buộc kho client với kho phục vụ đại lý, S4-10 còn lệch xác nhận của sales và tổng giỏ khi có chiết khấu. Đây là phát hiện audit, chưa sửa nghiệp vụ trong đợt sửa chuyển trang.

| Hạng mục | Backend | Frontend | BE ↔ FE / quyền |
|---|---|---|---|
| Menu tự nhảy lên đầu | Không đổi | Đã sửa giữ tọa độ cuộn khi bấm/chuyển trang, Back/Forward/Reload; không dựng lại menu nếu không đổi | Giữ Auth.ready, menu inert trong lúc đồng bộ, guard theo ma trận; có test thu hồi quyền |
| Giật/chớp chuyển trang | Không đổi | Đã thêm transition gốc của browser chỉ cho nội dung trắng, ổn định scrollbar, xử lý transition hủy; giảm chuyển động tắt animation | Không đổi API, form và logic nghiệp vụ; vẫn load trang HTML, không giả chuyển thành SPA |
| Audit S4-01–10 theo Excel | Đã đọc code/test | Đã đọc luồng gọi API/UI | 7 story cơ bản đạt luồng chính, 3 story còn vấn đề; xem báo cáo để phân biệt AC, quyền và phạm vi dữ liệu |

Kiểm chứng đợt này: **529 test backend, 74 test JS, 5 test định tuyến đều qua**. Chrome thật với API mock: 1440×800 và 390×844, menu giữ **312px → 312px**, Back/Forward/Reload, nội dung trang, menu mobile và reduced-motion qua, không pageerror. Không sửa DB thật.
Chi tiết và bằng chứng: [báo cáo đối chiếu Sprint 4](docs/SPRINT_4_AUDIT_20261020.md).

## Sửa ma trận quyền — 20/10/2026

Phần này thay thế các nhận xét **phân quyền** cũ ngày 19/10 bên dưới cho những đường truy cập được liệt kê; không thay thế nghiệm thu nghiệp vụ từng story.

| Phạm vi | Backend | Frontend | BE ↔ FE / ma trận quyền |
|---|---|---|---|
| Lưu ma trận | Đã sửa: lưu nhiều vai trò trong một transaction, flush DELETE trước INSERT; chỉ đổi cache sau khi lưu thành công; giữ trạng thái vai trò không có quyền | Đã sửa: gửi một yêu cầu, chỉ gồm các vai trò thay đổi | Đã nối `PUT /admin/permissions`; chỉ ADMIN được lưu; ADMIN luôn toàn quyền |
| Phiên đang đăng nhập | JWT nạp quyền hiện tại từ ma trận cho từng request; thêm endpoint đọc quyền của chính phiên | Đồng bộ khi mở trang, focus và mỗi 15 giây; cập nhật menu, guard và các nút liên quan; không reload trang chỉ vì quyền không thay đổi | Đã nối `GET /auth/permissions`; đã test cùng token: cấp → bỏ quyền/403 → cấp lại |
| S4-01–03: tạo đơn, giá, công nợ, tồn | Các API đặt hàng và tham chiếu kiểm quyền; không dùng role để bù quyền thiếu; sửa hồ sơ đại lý không được đổi hạn mức/ngày nợ nếu thiếu FINANCE_MANAGE | Đọc sản phẩm tham chiếu theo ORDER_CREATE thay vì buộc PRODUCT_VIEW; quản lý công nợ theo FINANCE_MANAGE | Đã nối; giữ phạm vi đại lý/kho theo dữ liệu |
| S4-04, S4-07–09: lịch sử, danh sách, in, sao chép | ORDER_VIEW để xem/in; mọi đường sao chép đơn gần nhất cần cả ORDER_CREATE và ORDER_VIEW | Menu theo quyền, không chặn Thủ kho bằng role; tạo đơn không tự gọi lịch sử khi thiếu ORDER_VIEW | Đã nối cả API sao chép cũ `/orders/dealer/{id}/last-order-to-draft` |
| S4-05–06: duyệt, hủy, vòng đời | ORDER_APPROVE / ORDER_CANCEL / ORDER_VIEW; chuyển vòng đời và cập nhật giao hàng dùng WAREHOUSE_EXPORT, WAREHOUSE_IMPORT không thay thế quyền xuất | Nút duyệt/hủy theo quyền hiện tại; guard trang kiểm cả quyền xem lẫn thao tác | Đã nối; tiếp tục kiểm phạm vi đơn/kho và điều kiện trạng thái |
| S4-10 và cổng theo dõi | Giữ quyền ORDER_CREATE / ORDER_VIEW / PRODUCT_VIEW và phạm vi đại lý liên kết; tài khoản nhiều vai trò không bị filter cổng chặn toàn bộ API nội bộ | Đại lý chỉ có một vai trò chỉ thấy menu cổng; tài khoản nội bộ được cấp quyền không bị menu role cứng chặn | Đã nối; đây là sửa quyền, chưa bổ sung các AC nghiệp vụ còn thiếu của S4-10 |
| Danh mục hỗ trợ | CRUD sản phẩm/nhóm theo PRODUCT_CREATE/EDIT/DELETE; bảng giá/chiết khấu theo PRODUCT_EDIT; nhà cung cấp/đơn vị theo WAREHOUSE_MANAGE; nhập/xuất/duyệt phiếu theo quyền riêng | Thay role cứng bằng quyền thao tác; ẩn/đóng các nút và hộp sửa liên quan khi thu hồi quyền | Đã nối; dữ liệu lookup không trả giá vốn/doanh thu; giá vốn không còn tự cấp theo role quản lý |
| Tài khoản | USER_VIEW/CREATE/EDIT/LOCK và USER_ASSIGN_ROLE; sửa tài khoản không được đổi vai trò nếu thiếu USER_ASSIGN_ROLE | Danh sách theo USER_VIEW; tạo/import cần USER_CREATE + USER_ASSIGN_ROLE; checkbox vai trò bị khóa nếu không được gán | Đã nối; quản lý ma trận và nhật ký hệ thống vẫn dành riêng ADMIN |

Quyền đọc và quyền sửa là độc lập: muốn thao tác qua trang danh sách cần quyền xem trang đó cùng quyền thao tác. Người có nhiều vai trò nhận hợp quyền, nên bỏ quyền ở một vai trò không thu hồi quyền vẫn được cấp bởi vai trò khác. Đại lý một vai trò vẫn bị giới hạn trong cổng riêng; tick quyền không mở dữ liệu nội bộ ngoài phạm vi này.

Kiểm chứng: `mvn -q test` **529/529**, `node --test tests/*.test.cjs` **68/68**; kiểm cú pháp JavaScript và `git diff --check` đều qua. Có kiểm thử HTTP + Spring Security + database H2 thật cho lưu ma trận, giữ token cũ, thu hồi/cấp lại quyền; kiểm thử vai trò rỗng qua nạp lại cấu hình, hợp quyền nhiều vai trò, lưu thất bại không đổi cache, và phiên frontend tự ẩn/hiện nút. Các fixture bảo mật cũ được sửa để cấp authority rõ ràng; không dùng role mặc định làm bằng chứng được cấp quyền. Chưa thao tác thay đổi ma trận/database thật của người dùng, chưa kiểm thử trực tiếp toàn bộ màn hình trên trình duyệt.

**Chưa thể nghiệm thu toàn bộ sprint 1–5.** Có backend hoặc giao diện chưa đồng nghĩa với đủ tính năng, nối đúng và kiểm quyền đầy đủ.

- Nội dung backlog có **48 story**, từ S1-01 đến S5-09. Tiêu đề ghi 76 story/8 sprint nhưng không có nội dung sprint 6–8.
- Bản tích hợp chứa 13 PR: **#78, #82, #87, #91, #92, #95, #96, #97, #98, #99, #100, #101, #102**. PR #95 và #100 được chuyển từ `main` sang `develop`.
- **501 test backend, 62 test JavaScript và 5 test định tuyến đều qua**; `mvn verify` tạo được JAR. Có 7 test tích hợp HTTP + Spring Security + H2 thật, không mock service/repository.
- Chưa nghiệm thu trên MySQL thật, SMTP thật, toàn bộ giao diện 360px hoặc tệp 5.000 SKU trong đợt này.

## Sửa lỗi 19/10/2026 — Admin mở “Đơn hàng của tôi” và “Cổng đại lý” bị 403

Nguyên nhân: menu/guard cho phép ADMIN nhưng API yêu cầu tài khoản gắn với một hồ sơ đại lý.
Admin giờ chọn đại lý từ DB trên cả hai trang; cùng `dealerId` được gửi qua danh sách,
chi tiết, catalog, hồ sơ, điểm giao, công nợ và POST đặt/đặt lại. Không cần sửa liên kết
tài khoản admin; ngữ cảnh chọn nằm trong URL và không lưu vào localStorage/sessionStorage.

| Hạng mục | Backend | Frontend | BE ↔ FE | Ma trận quyền BE / FE |
|---|---|---|---|---|
| Admin xem đơn đại lý | Đã xong: chọn ngữ cảnh, lọc đúng đại lý, chặn chi tiết ngoài ngữ cảnh | Đã xong: bộ chọn, hướng dẫn khi chưa chọn, tải danh sách/chi tiết | Đã nối `/portal/dealers`, `/portal/orders`, `/portal/orders/{id}` và query `dealerId` | Đã đối chiếu: ADMIN có toàn bộ quyền; DEALER cần ORDER_VIEW và đúng đại lý liên kết |
| Admin đặt hàng trên cổng | Đã xong: hồ sơ/catalog/công nợ/điểm giao theo đại lý đã chọn; mọi đơn cổng chờ duyệt, chưa giữ tồn, ghi người thực hiện thật | Đã xong: bộ chọn và giỏ hàng gửi đúng ngữ cảnh | Đã nối GET cổng và POST `/portal/orders?dealerId=…` | Đã đối chiếu: menu + guard FE cần ORDER_CREATE, ORDER_VIEW, PRODUCT_VIEW; BE kiểm đúng quyền của từng API, ADMIN được hỗ trợ |
| Đặt lại đơn hàng | Đã xong: dùng luồng tạo đơn cổng, kiểm đại lý và tính giá server | Đã xong: giữ ngữ cảnh ở chi tiết/catalog/POST, ẩn nút khi thiếu ORDER_CREATE hoặc PRODUCT_VIEW | Đã nối qua cùng API cổng | Đã đối chiếu: ORDER_VIEW để xem đơn, ORDER_CREATE để gửi đơn, PRODUCT_VIEW để đọc giá hiện hành |
| Chống đổi đại lý / thu hồi quyền | Đã xong: DEALER đổi query sang đại lý khác bị 403; danh sách lựa chọn chỉ ADMIN; thu hồi quyền có hiệu lực trên token đang dùng | Đã xong: DEALER không có bộ chọn và không gửi ngữ cảnh URL giả; menu/guard/nút theo quyền | Đã kiểm bằng HTTP + Security + H2 và test JavaScript | Đã kết nối cho phạm vi sửa lỗi này; các hạn chế ma trận ở phần sprint bên dưới vẫn còn |

Kiểm chứng: `mvn verify` **501/501**, `node --test tests/*.test.cjs` **62/62**,
test định tuyến Python **5/5**, kiểm cú pháp JS và `git diff --check` qua.
Test mới xác nhận admin không liên kết đại lý vẫn mở/chọn/xem/đặt/đặt lại; API thiếu
ngữ cảnh trả 400 hướng dẫn chọn, ID không tồn tại trả 400, truy cập sai đại lý hoặc
thu hồi quyền trả 403. Chưa thử trực tiếp bản sửa trên trình duyệt/MySQL thật.
Đã cập nhật [hợp đồng API](backend/API_DOCUMENTATION.md) và [đường dẫn frontend](docs/FRONTEND_ROUTES.md).

## Cách đọc bảng

**Có** = có mã triển khai; **một phần** = thiếu nghiệp vụ của story; **chưa** = chưa có triển khai đáp ứng story. Cột kết nối nói về hợp đồng API/luồng sử dụng, không thay thế nghiệm thu AC. “Quyền một phần” nghĩa là đã kiểm vai trò nhưng chưa chặn đồng bộ ở BE/FE khi thu hồi quyền trong ma trận.

## Sprint 1 — Tài khoản và phân quyền

| Task | Tính năng | Backend | Frontend | BE ↔ FE | Quyền BE / FE | Nghiệm thu / còn thiếu |
|---|---|---|---|---|---|---|
| S1-01 | Đăng nhập, khóa sau 5 lần sai | Có | Có | Đã nối `/auth/login` | Công khai trước đăng nhập | Đã sửa thông báo sai mật khẩu giống tài khoản không tồn tại; giữ khóa 15 phút. Có test. |
| S1-02 | Gia hạn phiên, đăng xuất | Có | Có | Đã nối refresh/logout | JWT, blacklist, trạng thái tài khoản | FE còn lưu token/hồ sơ trong `sessionStorage`, trái skill backend; refresh token có hạn cố định. |
| S1-03 | Quên và đặt lại mật khẩu | Có | Có | Đã nối forgot/reset | Token 30 phút, dùng một lần theo service | Có test; cần thử nhận email SMTP thật. |
| S1-04 | Đổi mật khẩu | Có | Có | Đã nối từ hồ sơ | Người đăng nhập; tăng `tokenVersion` | Kiểm mật khẩu hiện tại/độ mạnh, thu hồi phiên cũ. Có test. |
| S1-05 | Ma trận quyền | Một phần | Có | Đã nối đọc/lưu ma trận | **Chưa đồng bộ toàn hệ thống** | Nhiều API dùng quyền hoặc vai trò nên gỡ quyền vẫn gọi được. Đã chặn giá vốn/biên lợi nhuận với ADMIN; cổng có test thu hồi quyền thật. |
| S1-06 | Menu theo quyền, mobile | Có | Có | Menu lấy quyền từ phiên | Một phần, phụ thuộc S1-05 | Đã thêm quyền menu Đơn hàng/Duyệt đơn, sửa menu cổng. Cần kiểm toàn bộ trang ở 360px. |
| S1-07 | Trang lỗi chung | Có | Có | Đã nối 403/404/phiên hết hạn | Route lỗi/xác thực | Có trang chung và hành động quay lại; test định tuyến qua. |
| S1-08 | CRUD, tìm và phân trang tài khoản | Có | Có | Đã nối `/admin/users` | ADMIN hai phía | Có gửi mật khẩu tạm; cần SMTP thật. Đã nối chọn đại lý cho tài khoản đại lý. |
| S1-09 | Nhiều vai trò, kho/địa bàn | Một phần | Có | Đã nối dữ liệu gán | ADMIN; kiểm kho bắt buộc, chống tự bỏ ADMIN | Gán được kho nhưng service phiếu kho/chuyển kho chưa giới hạn đủ theo kho được giao. |
| S1-10 | Khóa/mở tài khoản, bàn giao | Có | Có | Đã nối lock/unlock | ADMIN; thu hồi phiên server | Có lý do, tăng phiên bản token, cảnh báo đại lý cần bàn giao; có test. |

## Sprint 2 — Danh mục và bảng giá

| Task | Tính năng | Backend | Frontend | BE ↔ FE | Quyền BE / FE | Nghiệm thu / còn thiếu |
|---|---|---|---|---|---|---|
| S2-01 | Nhập người dùng Excel | Một phần | Có | Đã nối mẫu/preview/import | ADMIN hai phía | `importUsers()` gọi `create()` cùng service nên không qua transaction proxy; lỗi mail có thể để lại tài khoản dù báo dòng nhập thất bại. |
| S2-02 | Cập nhật hồ sơ | Có | Có | Đã nối `/users/profile` | Chỉ hồ sơ người đăng nhập | DTO chỉ sửa tên/điện thoại, kiểm định dạng; có test. |
| S2-03 | Ảnh đại diện | Có | Có | Đã nối upload/đọc ảnh | Người đăng nhập | JPG/PNG, 2 MB, ảnh vuông/thumbnail có service và test. |
| S2-04 | Nhật ký tồn, giá, công nợ, hóa đơn | Một phần | Có | Đã nối `/admin/audit-logs` | ADMIN hai phía | Có lọc/snapshot nhiều luồng; sửa công nợ qua hồ sơ đại lý vẫn tránh được lý do/audit công nợ. |
| S2-05 | Sản phẩm/SKU | Có | Có | Đã nối CRUD/ảnh/trạng thái | Một phần về thu hồi quyền | SKU duy nhất, chặn xóa theo giao dịch thật; giá vốn chỉ QL kinh doanh. |
| S2-06 | Nhóm hàng nhiều cấp | Có | Có | Đã nối cây/chuyển nhóm/doanh số | Một phần về thu hồi quyền | Có cây 3 cấp, chặn vòng lặp/xóa nhóm còn sản phẩm; có test. |
| S2-07 | Quy đổi đơn vị | Có | Có | Đã nối đơn vị/đơn/phiếu kho | Một phần; còn phạm vi kho | Có snapshot hệ số giao dịch cũ; test tính toàn vẹn qua. |
| S2-08 | Nhập sản phẩm Excel | Có | Có | Đã nối mẫu/preview/import | Một phần về thu hồi quyền | Có nhận diện SKU cập nhật và lỗi dòng; chưa thử 5.000 SKU đợt này. |
| S2-09 | Nhà cung cấp | Có | Có | Đã nối CRUD/dữ liệu phiếu nhập | Một phần về thu hồi quyền | Chặn xóa NCC có giao dịch; FE phiếu nhập mới chưa chọn NCC, xem S5-04. |
| S2-10 | Bảng giá nhóm và phiên bản | Một phần | Có | Đã nối quản lý bảng giá | Một phần về thu hồi quyền | Có hiệu lực/giá sàn/khóa bảng đã dùng; tạo/tính đơn nhận `priceListId` chưa kiểm đủ nhóm, active và hiệu lực. |

## Sprint 3 — Chiết khấu và đại lý

| Task | Tính năng | Backend | Frontend | BE ↔ FE | Quyền BE / FE | Nghiệm thu / còn thiếu |
|---|---|---|---|---|---|---|
| S3-01 | Chiết khấu sản lượng | Có | Có | Đã nối chính sách/tính đơn nội bộ | Một phần về thu hồi quyền | SKU/nhóm, %/số tiền, chọn mức có lợi nhất. Cổng chưa xem trước chiết khấu server. |
| S3-02 | Lịch sử giá | Có | Có | Đã nối `/products/{id}/price-history` | Có vai trò; còn hạn chế ma trận | Có giá cũ/mới/người sửa/thời điểm, API chỉ đọc. Hiện là giá sản phẩm; cần phân biệt giá từng bảng/nhóm. |
| S3-03 | Hồ sơ đại lý | Một phần | Có | Đã nối CRUD/nhóm/phụ trách | Có RLS danh sách; quyền một phần | Đường chỉ định bảng giá ở đơn chưa chặt. Ô ghi chú hồ sơ chưa có trường lưu. |
| S3-04 | Nhiều điểm giao | Một phần | Có | Đã nối CRUD/chọn điểm | **Thiếu kiểm vai trò và phạm vi API điểm giao** | Còn cho bỏ hết điểm mặc định; chưa tự chọn lại khi xóa điểm mặc định. |
| S3-05 | Hạn mức và ngày nợ | Một phần | Có | Đã nối `/finance/debt/{id}/limit` | **BE/FE chưa thống nhất** | FE chỉ kế toán/QL kinh doanh, BE còn cho ADMIN; PUT hồ sơ còn đường sửa công nợ khác. |
| S3-06 | Phân công và bàn giao | Một phần | Có | Đã nối phân công/bàn giao/lịch sử | **Phạm vi chưa xuyên suốt** | Danh sách giới hạn theo sales; tạo đơn nội bộ/điểm giao còn đường thao tác ngoài tuyến. Ghi chú bàn giao chưa lưu. |
| S3-07 | Khóa/mở giao dịch đại lý | Có | Có | Đã nối nội bộ/cổng | Server kiểm khóa; quyền một phần | Đơn cổng bị chặn khi khóa; đơn cũ có cảnh báo. Cần nghiệm thu cùng các đường xử lý vòng đời. |
| S3-08 | Tìm và lọc đại lý | Có | Có | Đã nối tìm kiếm/bộ lọc | Có RLS danh sách | Có khu vực/nhóm/phụ trách/trạng thái; còn hạn chế ma trận chung. |
| S3-09 | Tạo đơn, nháp, ngày/điểm giao | Một phần | Có | Đã nối đơn/nháp DB/tính tiền/tồn | **Còn hở phạm vi tạo đơn nội bộ** | Cần chặn tạo ngoài tuyến, kiểm bảng giá; chưa thử lại 15 dòng ở 360px. |

## Sprint 4 — Kiểm tra và xử lý đơn

| Task | Tính năng | Backend | Frontend | BE ↔ FE | Quyền BE / FE | Nghiệm thu / còn thiếu |
|---|---|---|---|---|---|---|
| S4-01 | Áp giá và chiết khấu | Có | Có | Đã nối `/orders/calculate`, `/orders/line-item/pricing` và `/price-lists/effective` | Toàn bộ luồng đặt hàng dùng `ORDER_CREATE` từ ma trận; Admin luôn được phép; không còn role fallback ở API bảng giá hiệu lực | Tự động áp bảng giá/chiết khấu, kiểm giá sàn; `priceListId` bắt buộc đúng nhóm, `active` và hiệu lực theo ngày. |
| S4-02 | Kiểm hạn mức công nợ | Có | Có | Đã nối `GET /dealers/{id}/debt` và `POST /orders/check-debt`; frontend hiển thị hiện tại/hạn mức/còn lại, cảnh báo vượt hạn mức và chặn nợ quá hạn | Đã nối `ORDER_CREATE`/`FINANCE_VIEW_DEBT` theo ma trận; `ROLE_ADMIN` luôn được phép | Vượt hạn mức chuyển đơn sang chờ duyệt; nợ quá hạn chặn tạo đơn. Chưa có sổ thu tiền riêng nên số dư hiện tính từ đơn `RECORDED`. |
| S4-03 | Tồn khả dụng, chống bán vượt tồn | Có | Có | Frontend gọi `/orders/check-inventory`, hiển thị tồn thực tế/giữ chỗ/khả dụng và chặn gửi đơn khi vượt tồn; backend giữ chỗ bằng khóa và cập nhật nguyên tử | `ORDER_CREATE` hoặc `WAREHOUSE_VIEW`; frontend kiểm tra `ORDER_CREATE`; `ROLE_ADMIN` luôn được phép | Đã bổ sung kiểm tra quyền theo ma trận và hàng rào cập nhật nguyên tử để hai đơn đồng thời không cùng giữ chỗ vượt tồn. Đơn chờ duyệt vẫn giữ chỗ khi được duyệt theo luồng S5-06. |
| S4-04 | Lịch sử 3 tháng, thêm đơn gần nhất | Có | Có | **Đã sửa nối luồng chính** | `ORDER_CREATE`/`ORDER_VIEW`/`CUSTOMER_VIEW` theo ma trận; menu và API chi tiết không còn role fallback | FE chờ đồng bộ quyền trước khi guard; màn Đại lý hiển thị theo `CUSTOMER_VIEW` hoặc `ORDER_VIEW`, các nút lịch sử/đơn đang dở chỉ hiện khi có `ORDER_VIEW`; thêm tất cả chỉ lấy đơn gần nhất qua áp giá/tồn. |
| S4-05 | Duyệt/Từ chối/Trả lại sửa | Có | Có | Đã nối PATCH, danh sách phân trang và lịch sử duyệt | Frontend yêu cầu cả `ORDER_VIEW` và `ORDER_APPROVE`; backend dùng `ORDER_VIEW` cho danh sách/chi tiết và `ORDER_APPROVE` cho thao tác; ADMIN luôn được phép | Bắt buộc lý do từ chối/trả lại, lưu lịch sử `APPROVED/REJECTED/RETURNED` bất biến, chỉ giữ chỗ sau khi kiểm quyền; đã bỏ role fallback và chặn sales duyệt đơn cổng có vi phạm. |
| S4-06 | Vòng đời và hủy đơn | Có | Có | Đã nối hủy, tracking và lịch sử trạng thái; frontend hiển thị 7 bước chính và nhánh `Huỷ` từ `/orders/{id}/tracking` | `ORDER_CANCEL`/`ORDER_VIEW` và `WAREHOUSE_IMPORT`/`WAREHOUSE_EXPORT` từ ma trận; cập nhật giao hàng dùng `WAREHOUSE_EXPORT`; ADMIN luôn được phép; giới hạn thao tác theo kho được gán | Bắt buộc lý do hủy, tự nhả giữ chỗ, nhánh hủy hiển thị đúng bước dừng và chặn sau xuất kho; chỉ cho chuyển từng bước kế tiếp, không nhảy/lùi; không còn role fallback trên luồng giao hàng. |
| S4-07 | Danh sách, lọc, tổng tiền | Có | Có | **Đã nối, thống nhất phân trang/DTO** | `ORDER_VIEW` theo ma trận; API dữ liệu phụ dùng `ORDER_VIEW`/`CUSTOMER_VIEW`, không còn role fallback | GET `/orders` trả tổng toàn tập lọc, số phần tử, phân trang tùy chọn; trang `/orders` cũng lọc phía FE. |
| S4-08 | In/PDF và barcode | Có | Có | **Đã nối PDF backend sau tạo đơn** | `ORDER_VIEW` theo ma trận; endpoint HTML/PDF/barcode đều kiểm tra quyền xem và RLS | Đã đồng bộ quyền giữa nút frontend và cả endpoint cũ `/print`; nhân viên kinh doanh/đại lý không thể in đơn ngoài phạm vi. |
| S4-09 | Sao chép đơn, áp lại giá | Có | Có | Đã nối nháp DB/nạp giá mới | API copy yêu cầu đồng thời `ORDER_CREATE` + `ORDER_VIEW`; kiểm tra phạm vi theo dữ liệu đơn | Nút và API thống nhất; dùng kiểm tra phạm vi xem đơn thay vì fallback role khi sao chép. |
| S4-10 | Cổng đại lý tự đặt | Một phần | Có | **Đã sửa dùng `/portal/**`** | `ORDER_VIEW`/`ORDER_CREATE`/`PRODUCT_VIEW` theo ma trận; `DEALER`/`ADMIN` chỉ là phạm vi cổng; ADMIN luôn được phép | Đã nối gán đại lý cho tài khoản; chặn API nội bộ/giả giá, luôn PENDING_APPROVAL. Thiếu preview chiết khấu server và UI sales xác nhận đơn cổng. |

## Sprint 5 — Theo dõi đơn và tồn kho

| Task | Tính năng | Backend | Frontend | BE ↔ FE | Quyền BE / FE | Nghiệm thu / còn thiếu |
|---|---|---|---|---|---|---|
| S5-01 | Đại lý theo dõi đơn, thực giao/giao thiếu | Có | Có | Đã nối `/portal/orders`/chi tiết | **Đại lý tài khoản + ORDER_VIEW** | Có trạng thái/ngày/dòng hàng/giao thiếu. Kho nhập thực giao còn thiếu UI và cần thống nhất vòng đời. |
| S5-02 | Đặt lại toàn bộ/một phần | Một phần | Có | **Đã sửa dùng catalog/POST cổng thật** | ORDER_CREATE/ORDER_VIEW; BE kiểm lại | Bỏ `/portal/orders/reorder` chưa có, bỏ retry POST sang API khác/giá cũ; loại hàng không còn catalog. Thiếu tổng sau chiết khấu xem trước. |
| S5-03 | Nhiều kho, khu/kệ, kho phục vụ | Có | **Chưa** | **Chưa có màn hình quản lý tương ứng** | Có WAREHOUSE_MANAGE ở BE; thiếu FE | Có API kho/vị trí/gán SKU/kho đại lý/picking list. Dropdown kho không thay thế màn hình quản lý kho/kệ. |
| S5-04 | Phiếu nhập NCC, lô, nháp/xác nhận | Có | **Một phần** | **Chưa nối đủ** | Có quyền nhập/điều chỉnh; thiếu scope kho | Test cộng tồn đúng lúc. FE phiếu cũ chưa gửi đủ supplierId/chứng từ/lô/HSD, chưa có xác nhận nháp mới. |
| S5-05 | Sổ tồn thực tế/giữ chỗ/khả dụng | Có | **Một phần** | **Chưa đủ 3 cột** | Có API tồn; quyền/scope chưa đủ | API có ba số; FE chỉ nạp `inventoryBaseQty`. Cần hai cột còn lại và lọc trạng thái tồn. |
| S5-06 | Giữ chỗ, hủy/quá hạn nhả tồn | Một phần | Có qua duyệt/hủy | Đã nối luồng chính | Quyền duyệt/hủy còn hạn chế chung | Có scheduler/transaction/khóa tồn. Cần xử lý đồng thời cùng đơn, đơn thiếu kho, bảo vệ tồn đã giữ trong xuất/chuyển. |
| S5-07 | Chuyển kho, đang đi, nhận chênh lệch | Có | Có | Đã nối tạo/danh sách/chi tiết/nhận/hủy; xem phần cập nhật S5-07 đầu file | WAREHOUSE_TRANSFER để ghi, VIEW hoặc TRANSFER để đọc; kiểm scope kho và thu hồi quyền, ADMIN luôn được phép | Đã kiểm khóa đồng thời, bảo vệ tồn đã giữ, nhận/hủy lặp, lý do chênh lệch và rollback. Chưa nghiệm thu MySQL thật. |
| S5-08 | Kiểm kê, chốt chênh lệch | Có | Có | Đã nối API `/warehouse/stock-counts`, phiếu/snapshot/số đếm lưu DB; xem cập nhật S5-08 đầu file | VIEW hoặc ADJUST để đọc/tạo/đếm, ADJUST để chốt; ADMIN luôn được phép; kiểm phạm vi kho và grant/revoke | Đã kiểm 4 tiêu chí ảnh backlog theo xác nhận quyền từ người dùng, khóa cạnh tranh, snapshot cũ, tồn giữ chỗ và rollback; chưa nghiệm thu MySQL thật. |
| S5-09 | Tồn tối thiểu và cảnh báo | Có | Có | Đã nối cấu hình, cảnh báo sổ tồn và dashboard; xem cập nhật S5-09 đầu file | WAREHOUSE_MANAGE để khai báo, WAREHOUSE_VIEW để xem; ma trận DB ở cả BE/FE, thu hồi trên phiên cũ, kiểm phạm vi kho | Đã triển khai hai AC; cảnh báo dựa trên tồn khả dụng, bằng ngưỡng không cảnh báo. Có bảng DB mới; chưa nghiệm thu MySQL thật. |

## Sprint 6 — Quản lý nâng cao và hạn sử dụng

| Task | Tính năng | Backend | Frontend | BE ↔ FE | Quyền BE / FE | Nghiệm thu / còn thiếu |
|---|---|---|---|---|---|---|
| S6-01 | Theo dõi lô và hạn sử dụng hàng hoá | Có | **Chưa** (chỉ làm BE theo yêu cầu) | **Đã có API sẵn sàng kết nối** | Đầy đủ ma trận: `PRODUCT_EDIT`/`WAREHOUSE_MANAGE` bật/tắt lô; `WAREHOUSE_VIEW` xem lô/cảnh báo; `WAREHOUSE_MANAGE` sửa ngưỡng; `WAREHOUSE_APPROVE` ghi đè xuất lô quá hạn kèm lý do; ADMIN toàn quyền | Đã hoàn thành 100% backend theo 4 tiêu chí trong ảnh mô tả. Đã kiểm thử 24 test (unit, service, security MockMvc). Chưa làm frontend theo chỉ đạo "chỉ làm BE". |

## Việc cần làm tiếp

1. Đồng bộ quyền với ma trận và giới hạn dữ liệu kho/đại lý ở mọi đường đọc/ghi, đặc biệt điểm giao, tạo đơn, sửa công nợ và phiếu kho.
2. Sửa transaction import tài khoản; kiểm bảng giá chỉ định; thống nhất nguồn công nợ/vòng đời đơn/xuất hàng.
3. Hoàn thiện UI S5-04; nghiệm thu S5-03/S5-05/S5-07/S5-08/S5-09 trên MySQL thật theo các cập nhật đầu file.
4. Nối preview chiết khấu cho cổng/đặt lại, UI sales xác nhận đơn cổng; nghiệm thu MySQL/SMTP/360px theo AC.

Chi tiết: [báo cáo rà soát](docs/SPRINT_1_5_AUDIT_20261018.md).
