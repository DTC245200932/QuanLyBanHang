# SaleStock API — Sprint 1

Base URL: `http://localhost:8080/api`

Mọi response nghiệp vụ dùng cấu trúc `{ "success": boolean, "message": string, "data": any }`.

## Xác thực

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| POST | `/auth/login` | Công khai | Đăng nhập; khóa 15 phút ở lần sai thứ 5 |
| POST | `/auth/refresh` | Công khai | Gia hạn access token bằng refresh token |
| POST | `/auth/forgot-password` | Công khai | Gửi liên kết đặt lại có hạn 30 phút |
| POST | `/auth/reset-password` | Công khai | Đặt lại mật khẩu; token dùng một lần |
| POST | `/auth/logout` | Có access hoặc refresh token | Thu hồi ngay các token hiện có; vẫn xử lý khi access token vừa hết hạn |
| POST | `/auth/change-password` | Đã đăng nhập | Đổi mật khẩu và thu hồi mọi phiên khác |
| GET | `/auth/me` | Đã đăng nhập | Hồ sơ, vai trò và phạm vi làm việc hiện tại |

### Đăng nhập

```json
POST /api/auth/login
{
  "username": "admin",
  "password": "<APP_BOOTSTRAP_ADMIN_PASSWORD>"
}
```

Sai tên đăng nhập hoặc mật khẩu luôn trả `401` cùng thông báo `Tên đăng nhập hoặc mật khẩu không đúng`. Lần sai thứ 5 trả `423` với `data.type = TEMPORARY_LOCK`.

Response thành công gồm `accessToken`, `refreshToken`, thông tin người dùng, `roles`, `permissions`, `warehouses`, `territories` và `mustChangePassword`.

### Đăng xuất

```json
POST /api/auth/logout
Authorization: Bearer <access-token>
{
  "refreshToken": "<refresh-token>"
}
```

### Đổi mật khẩu

```json
POST /api/auth/change-password
Authorization: Bearer <access-token>
{
  "currentPassword": "TempPass123",
  "newPassword": "MatKhauMoi123"
}
```

Mật khẩu mới tối thiểu 8 ký tự, có chữ và số. `tokenVersion` của tài khoản được tăng nên mọi JWT cũ mất hiệu lực ngay phía server.

## Quản trị tài khoản

Tất cả endpoint dưới `/admin/**` yêu cầu `ROLE_ADMIN` và được kiểm tra tại server.

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/admin/users` | Tìm kiếm, lọc và phân trang (mặc định 20) |
| GET | `/admin/users/{id}` | Chi tiết tài khoản |
| POST | `/admin/users` | Tạo tài khoản, sinh mật khẩu tạm và gửi email |
| POST | `/admin/users/import/preview` | Kiểm tra trước từng dòng dữ liệu Excel đã được frontend chuyển thành JSON |
| POST | `/admin/users/import` | Nhập các dòng hợp lệ, bỏ qua dòng lỗi và trả báo cáo tổng kết |
| PUT | `/admin/users/{id}` | Sửa hồ sơ, nhiều vai trò, kho và địa bàn |
| POST | `/admin/users/{id}/lock` | Khóa, thu hồi phiên và cảnh báo bàn giao đại lý |
| POST | `/admin/users/{id}/unlock` | Mở khóa |
| GET | `/admin/assignment-options` | Danh sách 7 vai trò, kho và địa bàn |
| GET | `/admin/permissions` | Ma trận quyền của 7 vai trò |
| PUT | `/admin/permissions/{role}` | Khai báo/cập nhật quyền cho vai trò (chặn gán giá vốn ngoài Sales Manager, chặn gán sửa tồn kho cho Sales) |
| POST | `/admin/permissions/reset` | Khôi phục ma trận quyền 7 vai trò về mặc định ban đầu |

### Tìm kiếm

```text
GET /api/admin/users?search=an&role=ROLE_SALES&active=true&page=0&size=20
```

`search` khớp tên, tài khoản hoặc số điện thoại. `size` tối đa 100.

### Tạo/cập nhật

```json
{
  "username": "nguyenvana",
  "email": "a@company.vn",
  "fullName": "Nguyễn Văn A",
  "phone": "0901234567",
  "roles": ["ROLE_SALES", "ROLE_VIEWER"],
  "warehouseIds": [],
  "territoryIds": [1]
}
```

Người có `ROLE_WAREHOUSE_MANAGER` hoặc `ROLE_WAREHOUSE_STAFF` bắt buộc có ít nhất một `warehouseId`. Quản trị viên không thể tự bỏ `ROLE_ADMIN` hoặc tự khóa tài khoản đang dùng.

### Khóa tài khoản

```json
POST /api/admin/users/15/lock
{
  "reason": "Nhân sự đã nghỉ việc"
}
```

Lý do là bắt buộc. Response trả `assignedDealerCount`; các đại lý liên quan được đánh dấu `handoverRequired = true`.

## Hồ sơ cá nhân

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| GET | `/users/profile` | Đã đăng nhập | Xem hồ sơ, vai trò, kho, địa bàn và URL ảnh đại diện/thumbnail hiện tại |
| PUT | `/users/profile` | Đã đăng nhập | Chỉ cập nhật họ tên và số điện thoại Việt Nam |

## Kho & Tồn kho

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| POST | `/warehouse/adjust` | `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN` | Điều chỉnh tồn kho; Nhân viên kinh doanh và Thủ kho bị cấm |
| POST | `/warehouse/import` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN` | Nhập kho |
| POST | `/warehouse/export` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN` | Xuất kho |
| GET | `/warehouse/options` | Vai trò được xem kho | Danh sách kho đang hoạt động dùng cho bộ chọn frontend |
| GET | `/warehouse/inventory` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN`, `ROLE_VIEWER` | Xem danh sách tồn kho |
| GET | `/warehouse/vouchers` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN`, `ROLE_VIEWER` | Xem các phiếu kho và snapshot quy đổi |

Mỗi thay đổi tồn kho được ghi tự động vào `/admin/audit-logs` với người thực hiện và giá trị trước/sau.

## Tài chính (Bảo mật giá vốn & biên lợi nhuận)

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| GET | `/finance/cost-price` | `ROLE_SALES_MANAGER` | Xem giá vốn sản phẩm — chỉ Quản lý kinh doanh; Admin, Thủ kho, Sales, Kế toán đều bị từ chối |
| GET | `/finance/profit-margin` | `ROLE_SALES_MANAGER` | Xem biên lợi nhuận — chỉ Quản lý kinh doanh |
| GET | `/finance/debt` | `ROLE_ADMIN`, `ROLE_SALES_MANAGER`, `ROLE_ACCOUNTANT` | Xem công nợ khách hàng |

## Quản lý nhóm hàng nhiều cấp & Sản phẩm (S2-06)

Hỗ trợ Quản lý kinh doanh xem và phân tích doanh số theo ngành hàng/phân nhóm nhiều cấp thay vì chỉ xem từng mã sản phẩm lẻ.

### Quy tắc nghiệp vụ cốt lõi:
1. **Cấu trúc cây tối thiểu 3 cấp**: Cấp 1 (Ngành hàng, `parent = null`, `level = 1`) -> Cấp 2 (Phân nhóm, `level = 2`) -> Cấp 3+ (Nhóm con / chi tiết, `level >= 3`). Tự động tính cấp bậc và chống vòng lặp chu trình (không thể chọn con cháu làm cha).
2. **Chuyển sản phẩm giữa các nhóm**: Hỗ trợ chuyển một hoặc nhiều sản phẩm đồng thời sang nhóm đích.
3. **Nhóm còn sản phẩm thì không xoá được**: Nếu nhóm hàng trực tiếp hoặc bất kỳ nhóm con nào của nó còn sản phẩm, thao tác xoá bị chặn và trả về lỗi `400 Bad Request` (`CategoryNotEmptyException`).
4. **Xem doanh số theo ngành hàng**: Tự động tính tổng doanh số (`totalRevenue`) và tổng số lượng sản phẩm (`totalProductCount`) cộng dồn của toàn bộ cây con bên dưới.

### Danh sách API Nhóm hàng (`/api/categories`)

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| GET | `/categories/tree` | Đã đăng nhập | Xem cấu trúc cây phân cấp kèm tổng hợp doanh số và số sản phẩm từng nhánh |
| GET | `/categories` | Đã đăng nhập | Danh sách nhóm hàng dạng phẳng |
| GET | `/categories/{id}` | Đã đăng nhập | Xem chi tiết nhóm hàng và cây con |
| GET | `/categories/{id}/revenue` | Đã đăng nhập | Xem tổng hợp doanh số toàn nhánh của ngành hàng / nhóm hàng |
| POST | `/categories` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Tạo nhóm hàng mới (tự động gán `level = parent.level + 1`) |
| PUT | `/categories/{id}` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Cập nhật tên, mô tả, chuyển nhóm cha (ngăn ngừa chu trình) |
| DELETE | `/categories/{id}` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Xóa nhóm hàng (**Chặn nếu còn sản phẩm** -> 400 Bad Request) |
| POST | `/categories/move-products` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Chuyển hàng loạt sản phẩm sang nhóm hàng đích |
| PUT | `/categories/products/{productId}/move?targetCategoryId={id}` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Chuyển 1 sản phẩm sang nhóm hàng đích |

### Danh sách API Quản lý danh mục sản phẩm (S2-05)

Hỗ trợ Quản lý kinh doanh quản lý danh mục sản phẩm chuẩn hóa toàn công ty (gọi tên và mã SKU thống nhất).

#### Quy tắc nghiệp vụ cốt lõi:
1. **Khai báo đầy đủ**: Mã SKU, tên sản phẩm, nhóm hàng, đơn vị tính cơ sở (cái, chai, hộp, kg...), quy cách đóng gói (Thùng 24 lon, Hộp 10 vỉ...), giá vốn, giá bán, ảnh sản phẩm, trạng thái.
2. **Mã SKU là duy nhất**: Hệ thống ràng buộc duy nhất trên toàn cơ sở dữ liệu (`unique`) và kiểm tra khi tạo mới / cập nhật.
3. **Giá vốn bảo mật**: CHỈ DUY NHẤT Quản lý kinh doanh (`ROLE_SALES_MANAGER`) xem và chỉnh sửa được giá vốn. Với các vai trò khác (kể cả Admin, Sales, Thủ kho, Kế toán), trường `costPrice` luôn trả về `null` và không thể sửa đổi giá vốn khi cập nhật.
4. **Bảo vệ sản phẩm có giao dịch**: Sản phẩm đã phát sinh giao dịch bán hoặc nhập kho (`hasTransactions = true` hoặc `revenue > 0`) thì **KHÔNG ĐƯỢC PHÉP XOÁ**, chỉ có thể chuyển sang trạng thái **Ngừng kinh doanh** (`active = false`). Thao tác xoá sẽ trả về lỗi `400 Bad Request` (`ProductHasTransactionsException`).

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| GET | `/products?categoryId={id}&search={q}&active={true/false}` | Đã đăng nhập | Danh sách sản phẩm (hỗ trợ lọc nhóm, tìm kiếm theo SKU/tên, trạng thái; giá vốn chỉ trả về cho Sales Manager) |
| GET | `/products/{id}` | Đã đăng nhập | Xem chi tiết sản phẩm kèm danh sách đơn vị quy đổi (giá vốn bảo mật) |
| POST | `/products` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Tạo sản phẩm mới, có thể khai báo `unitConversions` (chỉ Sales Manager gán được giá vốn ban đầu) |
| PUT | `/products/{id}` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Cập nhật thông tin sản phẩm (chỉ Sales Manager sửa được giá vốn; vai trò khác giữ nguyên giá vốn cũ) |
| PATCH | `/products/{id}/status?active={true/false}` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Chuyển đổi trạng thái Đang kinh doanh / Ngừng kinh doanh |
| DELETE | `/products/{id}` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Xóa sản phẩm (**Chặn nếu đã phát sinh giao dịch** -> 400 Bad Request) |
| POST | `/products/upload-image` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Tải ảnh sản phẩm lên server (hỗ trợ JPG, PNG, WEBP tối đa 5MB) |
| GET | `/products/images/{fileName}` | Đã đăng nhập | Xem ảnh sản phẩm |
| GET | `/products/import/template` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Tải tệp Excel mẫu (`.xlsx`) kèm dữ liệu mẫu, sheet nhóm hàng tra cứu và hướng dẫn |
| POST | `/products/import/preview` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Xem trước và kiểm tra lỗi từng dòng từ tệp Excel (`multipart/form-data`) hoặc JSON (`application/json`) |
| POST | `/products/import` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Nhập danh mục sản phẩm hàng loạt từ Excel (`multipart/form-data`) hoặc JSON (`application/json`). SKU đã tồn tại thì cập nhật thay vì tạo mới |

### Nhập danh mục sản phẩm hàng loạt từ Excel (S2-08)

Hỗ trợ Quản lý kinh doanh đưa đến 5.000 mã hàng hiện có vào hệ thống một cách nhanh chóng, chính xác mà không phải gõ tay.

#### Quy tắc nghiệp vụ cốt lõi:
1. **Tải tệp mẫu chuẩn (.xlsx)**:
   - Tệp gồm 3 sheet:
     - Sheet 1: `Sản phẩm mẫu` với các cột chuẩn: `Mã SKU (*)`, `Tên sản phẩm (*)`, `Mã/Tên nhóm hàng (*)`, `Đơn vị tính cơ sở`, `Quy cách đóng gói`, `Giá bán`, `Giá vốn`, `Trạng thái`, `Mô tả`.
     - Sheet 2: `Danh mục nhóm hàng` hiển thị toàn bộ mã và tên nhóm hàng hiện hành giúp người dùng dễ dàng sao chép.
     - Sheet 3: `Hướng dẫn` quy định rõ nguyên tắc nhập liệu.
2. **Xem trước & báo lỗi theo từng dòng**:
   - Endpoint: `POST /api/products/import/preview` (hỗ trợ cả tệp Excel `multipart/form-data` hoặc JSON array `application/json`).
   - Kiểm tra và báo lỗi chi tiết cho từng dòng:
     - SKU trống hoặc vượt quá 50 ký tự.
     - Tên sản phẩm trống hoặc vượt quá 255 ký tự.
     - Nhóm hàng không tìm thấy (tra cứu thông minh theo ID, Mã nhóm hoặc Tên nhóm hàng).
     - Trùng lặp mã SKU nội bộ trong cùng tệp tải lên (chỉ rõ dòng bị trùng).
     - Giá bán hoặc giá vốn âm.
     - Độ dài đơn vị tính, quy cách đóng gói, mô tả.
   - Thống kê tổng hợp: `totalRows`, `validRows`, `invalidRows`, `createCount`, `updateCount`.
3. **Cập nhật thay vì tạo mới khi SKU đã tồn tại**:
   - Tự động đối chiếu mã SKU trong cơ sở dữ liệu (không phân biệt hoa thường).
   - Trong bản xem trước:
     - SKU đã có trong hệ thống: đánh dấu `action = "UPDATE"` và `isUpdate = true`, kèm `existingProductId`.
     - SKU mới: đánh dấu `action = "CREATE"` và `isUpdate = false`.
   - Khi thực hiện nhập (`POST /api/products/import`):
     - Dòng `UPDATE`: cập nhật tên, nhóm hàng, đơn vị, quy cách, giá bán, giá vốn (nếu có quyền), trạng thái, mô tả cho sản phẩm hiện có. Tự động ghi nhật ký kiểm toán (Audit Log) nếu giá bán thay đổi.
     - Dòng `CREATE`: tạo mới sản phẩm với trạng thái ban đầu `revenue = 0`, `hasTransactions = false`.
4. **Bảo mật giá vốn**:
   - CHỈ Quản lý kinh doanh (`ROLE_SALES_MANAGER` hoặc quyền `FINANCE_VIEW_COST_PRICE`) mới được xem và sửa giá vốn khi nhập từ Excel.
   - Nếu người dùng khác (ví dụ Quản trị viên không có quyền giá vốn) tải tệp có cột giá vốn: trường giá vốn sẽ được bỏ qua, giữ nguyên giá vốn hiện tại của SKU để đảm bảo tuyệt đối bí mật kinh doanh.
5. **Hiệu năng xử lý 5.000 mã hàng**:
   - Cơ chế truy vấn theo lô (`chunk` 500 SKU) và lưu hàng loạt (`saveAll`), tránh tình trạng N+1 query và không nghẽn cơ sở dữ liệu.

#### Cấu trúc Response kiểm tra / nhập (`ProductImportResponse`):
```json
{
  "success": true,
  "message": "Kết quả kiểm tra tệp nhập sản phẩm",
  "data": {
    "totalRows": 2,
    "validRows": 2,
    "invalidRows": 0,
    "createCount": 1,
    "updateCount": 1,
    "importedRows": 0,
    "rows": [
      {
        "rowNumber": 2,
        "sku": "HEIN-330",
        "name": "Bia Heineken lon 330ml",
        "category": "BEV-BEER",
        "resolvedCategoryId": 1,
        "resolvedCategoryName": "Bia các loại",
        "unit": "lon",
        "packingSpecification": "Thùng 24 lon",
        "price": 18000,
        "costPrice": 14500,
        "active": true,
        "description": "Bia cao cấp",
        "action": "UPDATE",
        "isUpdate": true,
        "existingProductId": 5,
        "valid": true,
        "errors": []
      },
      {
        "rowNumber": 3,
        "sku": "NEW-PROD-01",
        "name": "Sản phẩm mới 01",
        "category": "BEV-WATER",
        "resolvedCategoryId": 2,
        "resolvedCategoryName": "Nước giải khát",
        "unit": "chai",
        "packingSpecification": "Lốc 6 chai",
        "price": 10000,
        "costPrice": 7500,
        "active": true,
        "description": "Sản phẩm mới",
        "action": "CREATE",
        "isUpdate": false,
        "existingProductId": null,
        "valid": true,
        "errors": []
      }
    ]
  }
}
```

## Đơn vị tính quy đổi của SKU & Nhập xuất kho theo thùng/lon (S2-07)

Hỗ trợ Nhân viên kho khai báo đơn vị quy đổi theo cách kho đang gọi hàng (thùng, lốc...), để nhập xuất theo thùng mà sổ sách tồn kho và đơn hàng vẫn ghi nhận chính xác theo số lượng đơn vị cơ sở (số lon).

### Quy tắc nghiệp vụ cốt lõi:
1. **Mỗi SKU khai báo được nhiều đơn vị**: 1 đơn vị cơ sở (`unit`, vd: "lon" với hệ số 1) và nhiều đơn vị quy đổi (vd: "lốc" hệ số 6, "thùng" hệ số 24). Hệ số quy đổi luôn > 0.
2. **Đơn hàng và phiếu kho nhập theo đơn vị nào cũng quy về đơn vị cơ sở khi ghi sổ**:
   - Khi nhập/xuất kho theo "thùng", hệ thống tính: `baseQuantity = quantity * conversionFactor` (vd: 10 thùng * 24 = 240 lon). Sổ sách tồn kho (`warehouse_inventories`) luôn lưu trữ và cộng/trừ theo đơn vị cơ sở.
   - Khi tạo đơn hàng theo "thùng" hoặc "lốc", dòng đơn hàng lưu trữ số lượng quy về đơn vị cơ sở tương ứng.
3. **Đổi hệ số quy đổi không làm sai lệch các giao dịch đã ghi trước đó**:
   - Mọi dòng phiếu kho (`warehouse_voucher_items`) và dòng đơn hàng (`order_items`) đều **đóng băng** (snapshot) các giá trị `unit`, `conversionFactor`, `baseUnit`, `baseQuantity` tại thời điểm ghi sổ.
   - Khi nhà sản xuất thay đổi quy cách đóng gói hoặc người dùng sửa hệ số quy đổi của SKU (vd: đổi thùng từ 24 lon thành 20 lon), toàn bộ phiếu kho, đơn hàng và số tồn kho đã ghi sổ trước đó **hoàn toàn không bị thay đổi hay sai lệch**. Giao dịch mới phát sinh sau đó sẽ áp dụng hệ số mới.

### Danh sách API Đơn vị quy đổi (`/api/products/{productId}/unit-conversions`)

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| GET | `/products/{productId}/unit-conversions` | Đã đăng nhập | Xem danh sách các đơn vị tính của SKU (luôn có đơn vị cơ sở hệ số 1.0 và các đơn vị phụ) |
| POST | `/products/{productId}/unit-conversions` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Khai báo đơn vị quy đổi mới (vd: "thùng" với hệ số 24) |
| PUT | `/products/{productId}/unit-conversions/{id}` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Cập nhật tên đơn vị, hệ số quy đổi |
| DELETE | `/products/{productId}/unit-conversions/{id}` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Xóa đơn vị quy đổi |

### Danh sách API Kho & Phiếu kho (`/api/warehouse`)

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| POST | `/warehouse/import` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN` | Lập phiếu nhập kho (hỗ trợ nhập theo thùng/lốc, tự động quy về đơn vị cơ sở khi ghi sổ) |
| POST | `/warehouse/export` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN` | Lập phiếu xuất kho (quy về đơn vị cơ sở và kiểm tra tồn kho theo đơn vị cơ sở) |
| POST | `/warehouse/adjust` | `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN` | Điều chỉnh tồn kho |
| GET | `/warehouse/inventory` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN`, `ROLE_VIEWER` | Xem tồn kho sổ sách theo đơn vị cơ sở (số lon) kèm bảng quy đổi tương đương theo thùng, lốc |
| GET | `/warehouse/vouchers` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN`, `ROLE_VIEWER` | Danh sách phiếu kho đã ghi sổ kèm thông tin đóng băng hệ số |
| GET | `/warehouse/vouchers/{id}` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN`, `ROLE_VIEWER` | Chi tiết phiếu kho |

### Danh sách API Danh mục nhà cung cấp (S2-09)

Hỗ trợ Nhân viên kho quản lý danh mục nhà cung cấp, để phiếu nhập kho luôn gắn đúng nguồn hàng nhằm truy nguyên khi có lô lỗi.

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| GET | `/suppliers` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN`, `ROLE_VIEWER` | Lấy danh sách nhà cung cấp (phân trang, tìm kiếm) |
| GET | `/suppliers/{id}` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN`, `ROLE_VIEWER` | Xem chi tiết nhà cung cấp |
| POST | `/suppliers` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN` | Tạo mới nhà cung cấp |
| PUT | `/suppliers/{id}` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN` | Cập nhật thông tin nhà cung cấp |
| DELETE | `/suppliers/{id}` | `ROLE_WAREHOUSE_STAFF`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_ADMIN` | Xóa nhà cung cấp (Chặn và trả lỗi nếu đã có phiếu kho, chỉ cho phép ngừng giao dịch `active=false`) |


### Danh sách API Đơn hàng (`/api/orders`)

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| GET | `/orders` | Đã đăng nhập | Xem danh sách đơn hàng |
| GET | `/orders/{id}` | Đã đăng nhập | Xem chi tiết đơn hàng (kèm thông tin bảng giá, giá sàn và trạng thái phê duyệt) |
| POST | `/orders` | `ROLE_SALES`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_WAREHOUSE_STAFF` | Tạo đơn hàng (tự động áp bảng giá theo nhóm khách hàng; bán dưới giá sàn chuyển sang `PENDING_APPROVAL`) |
| PUT | `/orders/{id}/cancel` | `ROLE_SALES`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Hủy đơn hàng |
| PUT | `/orders/{id}/approve` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Phê duyệt đơn hàng bán dưới giá sàn (chuyển trạng thái sang `RECORDED`) |
| PUT | `/orders/{id}/reject?reason={ly_do}` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Từ chối đơn hàng bán dưới giá sàn (chuyển trạng thái sang `REJECTED`) |

## Khai báo Bảng giá theo nhóm khách hàng & Thời gian hiệu lực (S2-10)

Hỗ trợ Quản lý kinh doanh khai báo bảng giá theo nhóm khách hàng và thời gian hiệu lực, để đại lý cấp 1 và cấp 2 tự động nhận đúng giá của mình.

### Quy tắc nghiệp vụ cốt lõi:
1. **Nhiều bảng giá song song**: Hệ thống hỗ trợ đồng thời nhiều bảng giá cho từng phân khúc khách hàng: Đại lý cấp 1 (`DAI_LY_CAP_1`), Đại lý cấp 2 (`DAI_LY_CAP_2`), Khách lẻ (`KHACH_LE`).
2. **Hiệu lực theo thời gian**: Mỗi bảng giá bắt buộc có ngày bắt đầu (`startDate`) và ngày kết thúc (`endDate`). Tại một thời điểm, hệ thống tự động tìm bảng giá có hiệu lực mới nhất theo nhóm khách hàng (`startDate <= ngày <= endDate`).
3. **Giá bán & Giá sàn**: Mỗi dòng giá khai báo giá bán (`sellingPrice`) và giá sàn (`floorPrice` <= `sellingPrice`).
   - Nếu nhân viên bán dưới giá sàn (hoặc quy đổi theo đơn vị tính thùng/lốc mà đơn giá < giá sàn quy đổi): Đơn hàng **không được ghi nhận ngay** mà chuyển sang trạng thái **`PENDING_APPROVAL` (Chờ duyệt)** và gán cờ `requiresApproval = true`.
   - **Chỉ Quản lý kinh doanh (`ROLE_SALES_MANAGER`)** hoặc Admin mới có quyền phê duyệt (`/api/orders/{id}/approve`) hoặc từ chối (`/api/orders/{id}/reject`).
4. **Bảo vệ bảng giá đã phát sinh đơn**:
   - Khi bảng giá đã được dùng để tạo bất kỳ đơn hàng nào (`hasOrders = true` hoặc đã có liên kết đơn hàng trong cơ sở dữ liệu), hệ thống **nghiêm cấm sửa hoặc xóa** bảng giá này (trả lỗi `400 Bad Request` kèm ngoại lệ `PriceListLockedException`).
   - Người dùng bắt buộc phải dùng tính năng **Tạo phiên bản mới** (`POST /api/price-lists/{id}/new-version`). Phiên bản mới sẽ tự động tăng số version (v2, v3...), sao chép danh sách sản phẩm và cho phép điều chỉnh giá bán/giá sàn cũng như thời gian hiệu lực mới.
5. **Đại lý tự động nhận đúng giá**: Khi tạo đơn hàng có gắn `dealerId` hoặc `customerGroup`, nếu không nhập giá bán, hệ thống tự động tra cứu bảng giá hiệu lực tương ứng và áp dụng đúng đơn giá (tự động nhân hệ số quy đổi nếu đặt theo thùng/lốc).

### Danh sách API Bảng giá (`/api/price-lists`)

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| GET | `/price-lists?customerGroup={cg}&active={true/false}&keyword={q}` | Đã đăng nhập | Xem danh sách các bảng giá (hỗ trợ lọc theo nhóm khách hàng, trạng thái, tìm kiếm mã/tên) |
| GET | `/price-lists/{id}` | Đã đăng nhập | Xem chi tiết bảng giá kèm toàn bộ danh sách dòng giá (sản phẩm, giá bán, giá sàn) |
| GET | `/price-lists/customer-groups` | Đã đăng nhập | Danh sách các nhóm khách hàng (Đại lý cấp 1, Đại lý cấp 2, Khách lẻ) |
| GET | `/price-lists/effective?customerGroup={cg}&date={yyyy-MM-dd}` | Đã đăng nhập | Tìm bảng giá đang có hiệu lực cho nhóm khách hàng tại thời điểm chỉ định |
| POST | `/price-lists` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Tạo bảng giá mới (bắt buộc mã, tên, nhóm khách hàng, ngày bắt đầu/kết thúc, danh sách dòng giá) |
| PUT | `/price-lists/{id}` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Sửa thông tin bảng giá (**Chặn nếu bảng giá đã phát sinh đơn hàng** -> 400 Bad Request) |
| DELETE | `/price-lists/{id}` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Xóa bảng giá (**Chặn nếu bảng giá đã phát sinh đơn hàng** -> 400 Bad Request) |
| POST | `/price-lists/{id}/new-version` | `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Tạo phiên bản mới kế thừa từ bảng giá cũ (tăng version, sao chép sản phẩm, reset trạng thái phát sinh đơn) |

#### Ví dụ Request tạo bảng giá (`POST /api/price-lists`):
```json
{
  "code": "BG-DLC1-2026",
  "name": "Bảng giá Đại lý cấp 1 năm 2026",
  "customerGroup": "DAI_LY_CAP_1",
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "active": true,
  "description": "Bảng giá ưu đãi dành cho hệ thống Đại lý cấp 1",
  "items": [
    {
      "productId": 10,
      "sellingPrice": 16500,
      "floorPrice": 15500,
      "note": "Giá áp dụng theo lon"
    }
  ]
}
```

#### Ví dụ Request tạo phiên bản mới (`POST /api/price-lists/{id}/new-version`):
```json
{
  "newCode": "BG-DLC1-2026-V2",
  "newName": "Bảng giá Đại lý cấp 1 năm 2026 (v2)",
  "startDate": "2026-06-01",
  "endDate": "2026-12-31",
  "description": "Điều chỉnh giá bán do biến động chi phí nguyên vật liệu"
}
```
*(Nếu `items` để trống, hệ thống tự động kế thừa toàn bộ danh sách dòng giá của phiên bản tiền nhiệm).*

## Bảy vai trò

- `ROLE_ADMIN`: quản trị tài khoản và hệ thống; không mặc nhiên xem giá vốn.
- `ROLE_SALES_MANAGER`: quản lý kinh doanh; vai trò duy nhất xem giá vốn và biên lợi nhuận.
- `ROLE_WAREHOUSE_MANAGER`: quản lý, điều chỉnh và phê duyệt kho.
- `ROLE_SALES`: tạo đơn, quản lý khách hàng và địa bàn; không sửa được tồn kho, không xem giá vốn.
- `ROLE_WAREHOUSE_STAFF`: nhập/xuất tại kho được gán; không sửa điều chỉnh tồn kho, không xem giá vốn.
- `ROLE_ACCOUNTANT`: công nợ và báo cáo tài chính, không xem giá vốn.
- `ROLE_VIEWER`: chỉ xem báo cáo được phép.

Mọi chức năng đều kiểm quyền ở tầng server. Endpoint chưa được khai báo hoặc không đủ quyền bị từ chối mặc định (401 khi chưa đăng nhập, 403 khi thiếu quyền).

## Chạy local

1. Tạo MySQL hoặc để ứng dụng tự tạo schema `quanlybanhang`.
2. Cấu hình SMTP thật nếu cần nhận email kích hoạt/reset.
3. Chạy `backend\\mvnw.cmd spring-boot:run`.
4. Phục vụ thư mục repo qua HTTP, ví dụ cổng `5500`, rồi mở `/frontend/index.html`.

Tài khoản khởi tạo cho môi trường phát triển là `admin`; mật khẩu lấy từ
`APP_BOOTSTRAP_ADMIN_PASSWORD`. Hãy đổi ngay sau lần đăng nhập đầu tiên.
