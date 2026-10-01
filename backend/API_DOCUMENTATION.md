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
| GET | `/users/profile` | Đã đăng nhập | Xem hồ sơ, vai trò, kho và địa bàn hiện tại |
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

### Danh sách API Đơn hàng (`/api/orders`)

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| GET | `/orders` | Đã đăng nhập | Xem danh sách đơn hàng |
| GET | `/orders/{id}` | Đã đăng nhập | Xem chi tiết đơn hàng (kèm số lượng theo đơn vị đặt và số lượng cơ sở đã đóng băng) |
| POST | `/orders` | `ROLE_SALES`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_WAREHOUSE_STAFF` | Tạo và ghi sổ đơn hàng (tự động quy đổi về đơn vị cơ sở) |
| PUT | `/orders/{id}/cancel` | `ROLE_SALES`, `ROLE_SALES_MANAGER`, `ROLE_ADMIN` | Hủy đơn hàng |

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
