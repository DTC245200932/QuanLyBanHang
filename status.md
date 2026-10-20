# Trạng thái sprint 1–5

Cập nhật **20/10/2026**, bổ sung sửa lỗi ma trận quyền trên nhánh hiện tại; không merge hoặc lấy code từ nhánh khác.

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
| S5-07 | Chuyển kho, đang đi, nhận chênh lệch | Có | **Chưa** | **Chưa có màn hình chuyển/nhận** | Có WAREHOUSE_TRANSFER ở BE; thiếu FE | Test tách kho đi/trên đường/kho nhận qua. Cần scope, khóa đồng thời và bảo vệ tồn đã giữ. |
| S5-08 | Kiểm kê, chốt chênh lệch | **Chưa** | **Chưa** | **Chưa** | Chưa có quyền/luồng chốt riêng | Tab hiện tại không có phiếu snapshot, số đếm, lý do chốt và API duyệt QL kho. Điều chỉnh chung không đáp ứng story. |
| S5-09 | Tồn tối thiểu và cảnh báo | **Chưa** | **Chưa** | **Chưa** | Chưa có luồng cấu hình | Chưa có ngưỡng theo SKU + kho, API cấu hình và cảnh báo dashboard từ dữ liệu đó. |

## Việc cần làm tiếp

1. Đồng bộ quyền với ma trận và giới hạn dữ liệu kho/đại lý ở mọi đường đọc/ghi, đặc biệt điểm giao, tạo đơn, sửa công nợ và phiếu kho.
2. Sửa transaction import tài khoản; kiểm bảng giá chỉ định; thống nhất nguồn công nợ/vòng đời đơn/xuất hàng.
3. Hoàn thiện UI S5-03/S5-04/S5-05/S5-07; triển khai cả BE/FE S5-08/S5-09.
4. Nối preview chiết khấu cho cổng/đặt lại, UI sales xác nhận đơn cổng; nghiệm thu MySQL/SMTP/360px theo AC.

Chi tiết: [báo cáo rà soát](docs/SPRINT_1_5_AUDIT_20261018.md).
