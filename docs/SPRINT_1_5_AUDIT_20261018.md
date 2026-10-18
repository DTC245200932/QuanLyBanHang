# Rà soát và gộp PR sprint 1–5 — 18/10/2026

## Phạm vi và kết luận

Đối chiếu 48 story trong backlog được gửi với controller/service/model/repository, DTO, frontend, route, cấu hình quyền và kiểm thử. Đã đọc hai hướng dẫn trong `SKILLS`. Bảng từng story và phần còn thiếu nằm trong [status.md](../status.md).

**Cả 5 sprint vẫn còn tiêu chí chưa đạt.** Đợt này xử lý conflict, ghép PR, sửa lỗi tích hợp thấy được và kiểm chứng bản ghép. Tính năng thiếu được ghi rõ, không đánh dấu xong chỉ vì có file hoặc test unit qua.

`develop` ban đầu: `54cb772d88c9ccf978b35f2fe6282f552b85f921`.
Nhánh tích hợp: `feature/ra-soat-sprint-1-5`. Đích gộp: `develop`.
Giữ commit gốc của thành viên trong lịch sử, không force push nhánh của thành viên.

## Các PR trong bản tích hợp

| PR | Nội dung |
|---|---|
| [#78](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/78) | FE cổng tự đặt hàng |
| [#82](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/82) | BE vòng đời/hủy đơn |
| [#87](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/87) | FE danh sách/lọc đơn |
| [#91](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/91) | BE theo dõi đơn/thực giao |
| [#92](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/92) | BE lịch sử mua hàng |
| [#95](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/95) | FE đặt lại đơn; chuyển base `main` → `develop` |
| [#96](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/96) | BE cổng đặt hàng |
| [#97](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/97) | BE nhập NCC/nháp/xác nhận/lô |
| [#98](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/98) | BE chuyển kho |
| [#99](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/99) | BE duyệt/từ chối/trả lại/lịch sử |
| [#100](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/100) | BE PDF/barcode; chuyển base `main` → `develop` |
| [#101](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/101) | BE kho/khu/kệ/picking list |
| [#102](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/102) | BE giữ chỗ/nhả tồn quá hạn |

## Sửa đổi khi ghép

- Giữ đồng thời lọc, sao chép, vòng đời, giữ chỗ, lịch sử duyệt, giao thiếu và PDF; loại field/constructor bị khai báo hai lần khi các PR chạm cùng entity/DTO/service.
- Giữ kho/vị trí khi ghép nhập lô và chuyển kho; tách tồn thực tế, giữ chỗ, hàng trên đường.
- Thống nhất GET `/orders` bằng `OrderFilterRequest`: bộ lọc cũ, alias ngày, `createdBy`, `page`, `size`; giữ tổng toàn tập trước phân trang. Trả `data` và alias `content` cho hai giao diện.
- Cổng chuyển sang `/portal/profile`, `/portal/catalog`, `/portal/debt`, `/portal/delivery-points`, `/portal/orders`. Không tải danh mục toàn công ty rồi lọc ở client, không tự chọn đại lý đầu tiên khi không tìm được liên kết.
- Server bỏ giá client gửi trong đơn cổng, lấy đại lý/nhóm từ tài khoản. Giá quy đổi catalog bằng giá nhóm × hệ số, không lấy giá quy cách chung.
- Thêm filter chặn DEALER gọi API nội bộ. Cổng kiểm vai trò, mã quyền và đại lý liên kết. Có test thu hồi quyền thật.
- Nối chọn đại lý ở quản trị tài khoản; trả `dealers` trong assignment options; bắt buộc tài khoản DEALER có hồ sơ liên kết. Có test cập nhật HTTP/DB.
- Đặt lại dùng catalog cổng hiện hành và một POST `/portal/orders`; bỏ endpoint reorder chưa có/chuỗi retry sang API khác/giá cũ khi thiếu giá mới.
- Lịch sử mua loại nháp/trả lại/quá hạn. FE thêm hàng đơn gần nhất qua cùng hàm áp giá/kiểm tồn với hàng thường.
- Sửa `openPrintModal` chưa tồn tại sau tạo đơn. Module PDF gọi bản đã lưu trên server; lỗi PDF không báo sai rằng đơn chưa được lưu.
- Xuất qua vòng đời khóa dòng tồn theo SKU, cộng gộp và kiểm đủ tồn/giữ chỗ trước khi trừ; hủy dùng khóa dòng tồn để nhả.
- Khôi phục release Java 17. Sửa fixture lệch do hợp đồng API/khóa tồn/giá; giữ các bộ test tính năng liên quan.
- Đăng nhập sai có cùng thông báo cho tài khoản có/không tồn tại. Giá vốn/biên lợi nhuận chỉ QL kinh doanh theo backlog, kể cả ADMIN bị chặn.

## Kiểm chứng

| Bộ kiểm tra | Kết quả | Phạm vi |
|---|---|---|
| `backend/mvnw.cmd -f backend/pom.xml verify` | **499 test, 0 fail/error/skip; tạo JAR thành công** | Unit/controller/security và 5 test tích hợp mới |
| `node --test tests/*.test.cjs` | **55/55 qua** | Công nợ, cổng, đặt lại, PDF, menu, giao dịch, tồn, import, mật khẩu, toast |
| `python -m unittest discover -s tests -p test_frontend_server.py` | **5/5 qua** | HTTP frontend, route/asset/link cũ, bảo vệ thư mục/trang lỗi; có cổng/duyệt đơn |
| `node --check` JS cấp trang | Qua | Cú pháp, không thay thao tác trình duyệt |

`SprintMergeIntegrationTest` dùng Spring Boot, JWT login, Security, JPA và H2 tạm, **không mock service/repository**:

1. Đại lý nhận đúng hồ sơ/catalog, giá thùng bằng giá nhóm × hệ số; không đọc được catalog/đơn nội bộ. Giả giá 1đ nhưng server tính đúng 960.000đ. Đơn PENDING_APPROVAL chưa giữ tồn; sales khác bị chặn, sales phụ trách duyệt giữ 48 lon; lọc trả tổng đúng, tạo PDF thật, có lịch sử mua, hủy trả giữ chỗ về 0.
2. Nháp nhập không cộng tồn; xác nhận mới cộng theo quy đổi; phiếu đã xác nhận không sửa được. Chuyển kho trừ kho đi, chỉ tăng hàng trên đường ở kho nhận; nhận mới cộng tồn thực tế.
3. Hai request cùng đặt 192 lon khi có 240: một request 201, một 400; thực tế vẫn 240, giữ chỗ 192.
4. Gỡ PRODUCT_VIEW/ORDER_CREATE của DEALER trong ma trận thì JWT đang có bị 403 ở catalog/tạo đơn.
5. ADMIN đọc danh sách đại lý để gán tài khoản; thiếu liên kết bị 400, lưu hợp lệ trả đúng `dealerId` và lưu DB.

Không ghi vào MySQL đang dùng. Các kết quả này không chứng minh toàn bộ AC của 48 story đạt.

## Còn thiếu trước nghiệm thu

| Nhóm | Code đối chiếu | Việc còn lại |
|---|---|---|
| Ma trận quyền | `RolePermissionConfig`, `@PreAuthorize`, menu/guard FE | Nhiều API dùng authority hoặc role; phải chặn gọi theo role khi quyền đã gỡ. Token/hồ sơ còn ở sessionStorage, trái skill backend. |
| Kho được giao | `WarehouseServiceImpl.resolveWarehouse`, phiếu nhập/xuất/chuyển | Các đường xử lý chưa kiểm đủ kho được giao; cần cả kho đi/đến và chi tiết phiếu. |
| Đại lý được giao | `DealerDeliveryPointController/ServiceImpl`, `OrderServiceImpl.createOrder` | Điểm giao thiếu role/scope; tạo đơn nội bộ chưa kiểm phụ trách xuyên suốt. Scope danh sách không tự bảo vệ ghi. |
| Công nợ | `DealerServiceImpl`, `FinanceController`, `DealerDebtServiceImpl` | Chặn sửa hạn mức qua hồ sơ để tránh lý do/audit; đồng bộ hai vai trò theo AC. Cộng RECORDED chưa phản ánh tiền chưa thu và thay đổi theo trạng thái đơn. |
| Excel tài khoản | `AdminUserService.importUsers/create` | Tách transaction từng dòng; gọi nội bộ service hiện làm mất transaction riêng, lỗi SMTP có thể lưu tài khoản nhưng báo thất bại. |
| Bảng giá | `OrderServiceImpl.createOrder/calculateOrder` | Kiểm bảng chỉ định đúng nhóm/active/thời gian, không tin ID client. |
| Vòng đời/giữ tồn | `updateOrderStatus`, `updateOrderDelivery`, `releaseExpiredReservations`, phiếu xuất/chuyển | Chặn nhảy/lùi bước; thống nhất thực giao/xuất/nhả tồn. Bảo vệ đồng thời cùng đơn/phiếu, pending vượt tồn, đơn thiếu kho, xuất/chuyển dùng tồn đã giữ. |
| Cổng | `dealer-portal.js`, `dealer-orders.js`, luồng sales | Tổng chưa preview chiết khấu server. Chưa có UI sales xác nhận cổng; siết đơn vi phạm chỉ QL kinh doanh duyệt. |
| Kho sprint 5 | `warehouse-units.js`, service/model/route | Thiếu UI S5-03/S5-07; S5-04 chưa nối trường/luồng mới; S5-05 thiếu ba cột. Chưa có BE/FE kiểm kê S5-08 và ngưỡng tồn S5-09. |

Các vấn đề cũ được cập nhật theo bản ghép hiện tại, không dùng báo cáo sprint trước làm bằng chứng bản này đã nghiệm thu.

## Bước nghiệm thu tiếp

- Kiểm transaction/khóa đồng thời trên **MySQL**, nhất là duyệt/hủy/xuất cùng đơn và chuyển/nhận cùng phiếu. H2 không thay thế hành vi khóa MySQL.
- Thử **SMTP thật**, Excel 5.000 SKU, tải lại trang/mất mạng khi chốt và luồng 15 dòng ở **360px**. Đợt này chưa thực hiện những phép thử đó.
- Sửa theo bảng, cập nhật trạng thái từng story, nghiệm thu theo AC. Tổng test xanh không phải tỷ lệ hoàn thành sprint.
