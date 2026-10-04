# Kiểm tra tích hợp Sprint 2

Ngày kiểm tra: **04/10/2026**. Phạm vi: **S2-01 đến S2-10, 44 point**.

## Kết luận

**Sprint 2 chưa nối và hoàn thiện đầy đủ.** Có 2 story qua các tiêu chí đã kiểm tra,
1 story đạt trong PR còn mở, và 7 story còn lỗi hoặc thiếu luồng sử dụng.
Không dùng số test xanh để thay cho nghiệm thu từng story.

Đã đọc hướng dẫn trong `AGENTS.md`, hai file trong `SKILLS`, khảo sát mã frontend,
controller, DTO, service, entity, repository, security và các test liên quan.
Đối chiếu dựa trên mã nguồn, gọi API thật với MySQL và thao tác trình duyệt.

### Các revision được kiểm tra

- `main`: `a2965c6c2c9cf134df1cca04f24967d125d86fae`.
- [PR #26 — Nhà cung cấp](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/26):
  nhánh `FE/S209`, bao gồm bản sửa conflict và tích hợp trong lần kiểm tra này.
- [PR #29 — Avatar](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/29):
  `adf4cc0f7c0b2d72f08402499e80763c1a7d2bb1`, đã có merge từ `main`, không có conflict.

## Kết quả từng story

| Story | Frontend ↔ Backend | Kết quả theo AC và luồng sử dụng |
|---|---|---|
| **S2-01** — Import người dùng | Đã nối mẫu Excel, preview và import JSON | **Cần sửa.** Preview phân biệt dòng hợp lệ/lỗi. Khi gửi mail lỗi, import báo `importedRows=0` nhưng tài khoản vẫn tồn tại trong DB. Báo cáo nhập không phản ánh dữ liệu thực tế. |
| **S2-02** — Hồ sơ cá nhân | Đã nối `GET/PUT /users/profile` | **Đạt các AC đã kiểm tra.** Sửa họ tên/điện thoại, từ chối số điện thoại sai. Gửi thêm `roles` không thay đổi vai trò/kho của người dùng. Tài khoản, vai trò, kho và địa bàn không có điều khiển tự sửa. |
| **S2-03** — Avatar | Đã nối dashboard; PR #29 bổ sung hồ sơ và nhật ký | **Đạt trong PR #29, chờ merge.** Upload ảnh PNG 600×300 qua giao diện, backend trả ảnh 512×512 và thumbnail 96×96. Tải lại hồ sơ vẫn hiển thị ảnh. Test kiểm tra định dạng/giới hạn 2MB chạy qua. |
| **S2-04** — Nhật ký | Đã nối màn hình với `/admin/audit-logs` | **Cần sửa.** Xem/lọc được log; tồn kho, giá bán sản phẩm, hạn mức và thao tác đơn hàng có chỗ ghi log. Sửa bảng giá ghi `oldValue=null`, `newValue="Cập nhật bảng giá"`, thiếu giá trị trước/sau. Sửa giá vốn chưa ghi log. Loại `PRICELIST` chưa có trong bộ lọc frontend. |
| **S2-05** — Sản phẩm | Đã nối CRUD, ảnh, nhóm hàng, giá vốn, trạng thái | **Đã nối, còn lỗi.** SKU duy nhất và giá vốn được che với admin/kho. Sau nhập kho và tạo đơn, API vẫn trả `hasTransactions=false`, `canDelete=true`, nên giao diện còn cho bấm xóa sản phẩm đã giao dịch. Cần kiểm tra giao dịch thật ở backend và đồng bộ trạng thái; không dựa vào cờ client hoặc lỗi khóa ngoại để xử lý nghiệp vụ. |
| **S2-06** — Nhóm hàng | API cây/CRUD/chuyển nhóm đã có; giao diện bị chặn bởi quyền sai | **Cần sửa.** `PRODUCT_GROUP_VIEW` và `PRODUCT_GROUP_MANAGE` không có trong enum/quyền backend: menu bị ẩn và các nút quản lý không được bật. API tạo cây 3 cấp và chặn xóa nhóm có sản phẩm hoạt động. Doanh số sản phẩm/nhóm chưa được cập nhật từ đơn hàng thực tế. |
| **S2-07** — Quy đổi đơn vị | Đã nối giao diện SKU, phiếu kho, đơn hàng và tồn kho | **Đạt các AC đã kiểm tra.** Nhập 5 thùng ×24 ghi 120 lon; đơn 2 thùng ghi 48 lon. Đổi hệ số lên 30, đọc lại chứng từ vẫn là hệ số 24 và lượng cơ sở cũ. Giao diện đọc dữ liệu DB và hiển thị 120 lon ≈4 thùng theo hệ số hiện tại. |
| **S2-08** — Import sản phẩm | API template/preview/import có; màn hình sản phẩm chưa nối import | **Cần sửa.** Không có nút/màn hình import sản phẩm. Chính tệp mẫu backend sinh ra khi tải lên lại báo lỗi cả 3 dòng vì cột nhóm hàng bị đọc nhầm. Giá dạng chữ không hợp lệ bị nhận thành 0. Luồng JSON đã phân biệt CREATE/UPDATE, bỏ dòng lỗi và nhập dòng hợp lệ. |
| **S2-09** — Nhà cung cấp | PR #26 đã được sửa để CRUD/trạng thái chạy được | **Còn thiếu liên kết phiếu nhập.** Thông tin và trạng thái được lưu DB, quyền đọc/ghi được kiểm tra tại server. `WarehouseVoucherRequest` không có `supplierId`, service ghi phiếu không gắn supplier. Gửi `supplierId` bị bỏ qua; supplier vừa dùng trong request nhập kho vẫn xóa được. |
| **S2-10** — Bảng giá | Đã nối quản lý bảng giá; luồng đặt/duyệt đơn chưa đủ | **Cần sửa.** Backend áp giá theo nhóm, quy đổi giá theo đơn vị, khóa bảng giá đã dùng và tạo phiên bản mới hoạt động. Sửa dòng giá của bảng chưa dùng bị lỗi unique constraint trong MySQL. Payload frontend tạo đơn không gửi dealer/nhóm/bảng giá, không có giao diện duyệt/từ chối dưới giá sàn. Truyền `priceListId` trực tiếp còn dùng được bảng hết hạn, ngừng hoạt động và khác nhóm. |

## Đã sửa trong PR #26

1. Giải quyết conflict `frontend/js/menu-config.js`, giữ cả **Nhà cung cấp** và
   **Đơn vị tính**, giữ đường dẫn nhập/xuất của `main`.
2. Cho `/api/suppliers/**` đi qua tầng security và kiểm quyền từng thao tác ở controller.
   Trước sửa, toàn bộ API supplier bị `denyAll` chặn dù đã có controller.
3. Thay quyền frontend không tồn tại bằng đúng các vai trò backend cho phép.
   Người chỉ xem không có nút thêm/sửa/ngừng giao dịch.
4. Đọc `data.content` của Spring Page và tải các trang, dùng `active`/`contactPerson`
   thay cho `status`/`contactName` không khớp backend.
5. Bổ sung `PATCH /suppliers/{id}/status?active=true|false`, lưu trạng thái và giữ
   các thông tin khác. Chặn kích thước trang không hợp lệ.
6. Lưu điện thoại, email, địa chỉ, ghi chú vốn đã có trên form vào DB; đồng bộ
   DTO/entity/response, kiểm tra email/điện thoại và giới hạn độ dài.
7. Cập nhật `backend/API_DOCUMENTATION.md`, thêm test supplier cho quyền truy cập,
   dữ liệu form, trạng thái và quy tắc cấm xóa supplier có chứng từ.
8. Sửa flex layout để bảng NCC cuộn trong khung, không kéo tràn toàn trang.

Những sửa này làm màn hình nhà cung cấp chạy được; liên kết nhà cung cấp với phiếu
nhập vẫn là việc cần hoàn thiện theo S2-09.

## Lỗi cần ưu tiên và cách tái hiện

### 1. S2-10: không sửa được dòng giá trên MySQL — P1

- Tạo bảng giá chưa có đơn, có một SKU.
- Sửa giá của cùng SKU bằng `PUT /price-lists/{id}`.
- Nhận HTTP 400, lỗi `Duplicate entry` tại unique key `(price_list_id, product_id)`.
- [PriceListServiceImpl.java](../backend/src/main/java/com/quanlybanhang/service/PriceListServiceImpl.java#L159)
  xóa collection rồi tạo entity dòng giá mới; cần cập nhật dòng cũ hoặc xử lý thứ
  tự flush/xóa trước khi chèn. Unit test mock repository không bắt được lỗi này.

### 2. S2-10: bỏ qua hiệu lực và nhóm khi chọn bảng giá trực tiếp — P1

- Tạo bảng giá `DAI_LY_CAP_2`, `active=false`, kết thúc ngày `2025-12-31`.
- Tạo đơn ngày kiểm tra với `customerGroup=KHACH_LE` và `priceListId` của bảng đó.
- Backend vẫn trả HTTP 201, `status=RECORDED`, dùng dòng giá của bảng không hợp lệ.
- [OrderServiceImpl.java](../backend/src/main/java/com/quanlybanhang/service/OrderServiceImpl.java#L62)
  cần kiểm tra active, thời hạn, nhóm đại lý và SKU trước khi áp giá.

### 3. S2-01: nhập thất bại nhưng tài khoản đã được lưu — P1

- Nhập một dòng hợp lệ và một dòng lỗi; mô phỏng SMTP chưa cấu hình/thất bại.
- Kết quả nhập báo 0 tài khoản, nhưng truy vấn lại thấy 1 tài khoản đã tạo.
- [AdminUserService.java](../backend/src/main/java/com/quanlybanhang/service/AdminUserService.java#L135)
  gọi `create()` trong cùng bean nên `@Transactional` không bao bọc việc tạo từng
  dòng; repository đã lưu trước khi mail lỗi. Cần transaction theo dòng hoặc
  tách việc gửi mail bằng outbox và báo đúng trạng thái tạo/gửi.

### 4. S2-09: không truy nguyên được nhà cung cấp — P1

- Tạo supplier, nhập kho với `supplierId` của supplier đó.
- Response phiếu không có supplier; xóa supplier vẫn HTTP 200.
- Bổ sung `supplierId` vào request/response, validate supplier còn giao dịch,
  gắn relation trong [WarehouseServiceImpl.java](../backend/src/main/java/com/quanlybanhang/service/WarehouseServiceImpl.java#L85),
  thêm lựa chọn NCC trong form nhập và hiển thị NCC khi đọc chứng từ.
- Giữ kiểm tra `existsBySupplierId` để chặn xóa sau khi liên kết được lưu thật.

### 5. S2-06: quyền frontend không tồn tại — P1

- Đăng nhập admin hoặc quản lý kinh doanh: không thấy menu Nhóm hàng.
- Mở trang trực tiếp: các nút quản lý/chuyển sản phẩm vẫn bị ẩn.
- [menu-config.js](../frontend/js/menu-config.js#L71) và
  [product-groups.js](../frontend/js/product-groups.js#L22) dùng quyền backend chưa khai báo.
- Đồng bộ với vai trò/quyền backend hiện có hoặc bổ sung quyền thật vào enum và ma trận.

### 6. S2-08: chưa có frontend import; parser đọc sai dữ liệu — P1

- `products.html`/`products.js` chưa có luồng tải mẫu, chọn file, preview, xác nhận.
- Tải mẫu từ `/products/import/template`, tải lại nguyên file để preview:
  0 hợp lệ, 3 lỗi `Không tìm thấy nhóm hàng: ''`.
- [ProductExcelService.java](../backend/src/main/java/com/quanlybanhang/service/ProductExcelService.java#L295)
  nhận header chứa `ten` trước header nhóm: `Mã/Tên nhóm hàng (*)` bị đọc thành cột tên.
- Đổi tên header thành `Nhóm hàng`, nhập `not-a-price` vào Giá bán: dòng vẫn hợp lệ,
  giá được chuyển thành 0. Cần giữ lỗi parse để báo theo dòng, không mặc định giá sai thành 0.
- Multipart toàn ứng dụng giới hạn 2MB nhưng service import ghi giới hạn 20MB;
  cần thống nhất cấu hình/validation và thông báo. Chưa kiểm thử tải 5.000 SKU.

### 7. S2-10: thiếu luồng đặt/duyệt đơn ở frontend — P1

- [warehouse-units.js](../frontend/js/warehouse-units.js#L474) chỉ gửi `orderCode`,
  `customerName`, `note`, `items`; không gửi `dealerId`, `customerGroup`, `priceListId`
  hoặc giá thỏa thuận. Backend không đủ dữ liệu để áp đúng giá đại lý từ màn hình này.
- Menu Đơn hàng còn trỏ tới `#orders`; chưa có giao diện duyệt/từ chối.
- Cần nối chọn đại lý/nhóm, báo giá theo đơn vị, cảnh báo dưới sàn, trạng thái chờ duyệt
  và thao tác duyệt/từ chối của quản lý. API duyệt/từ chối đã có.

### 8. S2-04: log chưa có đủ giá trị trước/sau — P1

- Sửa bảng giá: log `PRICELIST UPDATE` có `oldValue=null`, newValue chỉ là mô tả.
- [PriceListServiceImpl.java](../backend/src/main/java/com/quanlybanhang/service/PriceListServiceImpl.java#L182)
  cần lưu snapshot các giá/điều kiện trước và sau; thêm log giá vốn khi thay đổi.
- Thêm `PRICELIST` vào bộ lọc frontend. Rà lại toàn bộ thao tác thuộc AC.
- KPI “lệch kiểm kê” hiện coi mọi lần tồn giảm là lệch, kể cả xuất kho bình thường;
  cần phân biệt loại điều chỉnh và xuất kho.

### 9. S2-05/S2-06: giao dịch chưa cập nhật cờ và doanh số — P2

- Nhập 120 lon và tạo đơn 432.000đ cho SKU mới.
- Đọc SKU: `hasTransactions=false`, `canDelete=true`, `revenue=0`.
- [Product.hasTransactions()](../backend/src/main/java/com/quanlybanhang/model/Product.java#L134)
  chỉ dựa vào cờ/revenue; service đơn hàng/phiếu kho chưa cập nhật các giá trị này.
- Dựa vào chứng từ thật để xác định quyền xóa; không cho client thay cờ giao dịch.
  Tính doanh số từ đơn đủ điều kiện hoặc cập nhật nhất quán khi ghi/duyệt/hủy đơn.

## Bằng chứng kiểm thử

- Backend sau sửa PR #26: **164 test, 0 fail/error/skip**.
- PR #29 độc lập: **148 test, 0 fail/error/skip**. Hai bộ có nhiều test trùng nhau.
- `node --check` các script frontend: qua; không còn conflict marker trong source.
- **34 kiểm tra HTTP với MySQL thật: 24 đạt, 10 chỉ ra lỗi còn tồn tại**.
  [Kết quả JSON](sprint2-api-checks.json) chứa từng kiểm tra và giá trị quan sát.
- Trình duyệt: tạo NCC, ngừng giao dịch và reload; upload avatar rồi reload;
  màn hình SKU đọc lượng tồn và hệ số hiện tại từ backend.
- [Ảnh NCC](evidence/sprint2-suppliers.jpg),
  [ảnh avatar PR #29](evidence/sprint2-avatar-pr29.jpg),
  [ảnh quy đổi đơn vị](evidence/sprint2-units.jpg).

Các phép ghi dùng database kiểm thử riêng và dữ liệu giả. Chưa kiểm tra SMTP thật,
production, nhập 5.000 SKU, hay toàn bộ hành vi đồng thời/nhiều người dùng.
Các kết quả “đạt” chỉ áp dụng cho AC và tình huống nêu trong báo cáo.

Theo quy định lưu trữ trong `SKILL_backend.md`, còn một điểm cần xử lý ở nền xác thực:
`auth.js`, `login.js` và `profile.js` vẫn lưu token/hồ sơ cache trong `sessionStorage`.
Việc lưu phiên và thông tin người dùng cần được đối chiếu lại với yêu cầu của repo.

## Thứ tự hoàn thiện đề xuất

1. Sửa lỗi DB bảng giá và kiểm tra hiệu lực/nhóm giá ở backend.
2. Sửa transaction import người dùng và liên kết supplier–phiếu nhập.
3. Đồng bộ quyền nhóm hàng; sửa parser Excel và nối frontend import sản phẩm.
4. Nối đặt/duyệt đơn, bổ sung audit snapshot, đồng bộ cờ giao dịch/doanh số.
5. Nghiệm thu lại 10 story trên cùng bản tích hợp, gồm PR #26 và PR #29.

PR được kiểm tra/sửa trong lần này vẫn mở để nhóm review; chưa merge vào `main`.
