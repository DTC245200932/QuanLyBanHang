# Bỏ dữ liệu hardcode và mock — 12/10/2026

Branch: `feature/hoan-thien-sprint-3`. Cập nhật PR #59.

## Đã thay đổi

| Vị trí | Dữ liệu hiện tại |
|---|---|
| API giá vốn và biên lợi nhuận | Đọc giá bán/giá vốn thực từ danh mục sản phẩm; không còn SP001/SP002 mẫu. Biên lợi nhuận bằng `null` nếu không thể tính. |
| Doanh số sản phẩm và nhóm hàng | Tổng thành tiền sau chiết khấu của dòng đơn `RECORDED`. Đơn chờ duyệt, huỷ, từ chối và bản nháp không góp doanh số. Cột doanh số nhập tay cũ bị bỏ qua. |
| Lịch sử giao dịch sản phẩm | Kiểm tra dòng đơn hàng và phiếu kho thực để quyết định quyền xoá; client không thể sửa doanh số/cờ giao dịch. Danh mục tải thống kê theo lô. |
| Hạn mức công nợ | Đọc hạn mức và số ngày nợ từ hồ sơ đại lý. Số dư trả `null` với `debtAmountAvailable=false` vì chưa có sổ công nợ/phiếu thu. |
| Phiếu kho | Chỉ báo thành công sau khi lưu phiếu và tồn kho. Body/dòng hàng rỗng hoặc thiếu số lượng trả 400; không tự nhập số lượng 1 hoặc tạo kho mặc định. |
| Khởi động database | Chỉ tạo vai trò và admin ban đầu với email/mật khẩu cấu hình. Không chèn kho, địa bàn, sản phẩm, đại lý, bảng giá, chiết khấu hoặc tài khoản demo. Biến seed demo cũ không còn tác dụng. |
| Tệp Excel nhập sản phẩm | Sheet sản phẩm chỉ có tiêu đề; sheet nhóm hàng lấy danh mục thực. Không có ba sản phẩm mẫu có thể nhập nhầm. |
| Tồn kho trên frontend | Bỏ nhãn “An toàn (Tồn tối thiểu 200)” gán cho mọi SKU; hiển thị “Chưa thiết lập” khi chưa có định mức. |
| Thiếu cấu hình kho | Vẫn tải danh mục/quy đổi. Phiếu kho bị khoá kèm thông báo; màn tồn kho không bịa số lượng 0; đơn hàng bán hoạt động độc lập. |
| Nút tải lại | Đổi tên mã nguồn từ reset demo sang tải API; làm mới cả bảng sản phẩm, tồn kho, chứng từ và bộ đếm, xử lý lỗi tải. |

Các nhãn menu, tên vai trò, trạng thái, giới hạn validation và placeholder nhập
liệu là cấu hình giao diện/nghiệp vụ; không phải bản ghi bán hàng mẫu.

## Kiểm chứng

- 273 kiểm thử Java đạt, không lỗi/không bỏ qua.
- 25 kiểm thử JavaScript và 5 kiểm thử server frontend đạt.
- Đóng gói Spring Boot JAR thành công.
- 35 kiểm tra MySQL/API đạt trên database riêng: trạng thái rỗng, phân quyền,
  giá trong DB, ghi phiếu và tồn kho, chiết khấu, đơn chờ duyệt/duyệt/huỷ/từ chối,
  doanh số cây ba cấp, bỏ số liệu legacy và chuyển nhóm.
- 5 kiểm tra UI đạt: thiếu kho khi nhập, đơn hàng không cần kho, không bịa tồn
  khi thiếu kho, tải lại từ API và bỏ ngưỡng an toàn giả.

Chi tiết:

- [Kết quả MySQL/API](no-mock-api-checks-20261012.json)
- [Kết quả UI](no-mock-ui-checks-20261012.json)
- [Doanh số nhóm hàng từ đơn kiểm thử](evidence/no-mock-category-revenue-20261012.png)
- [Phiếu kho khi chưa cấu hình kho](evidence/no-mock-missing-warehouse-20261012.png)
- [Tồn kho và định mức chưa thiết lập](evidence/no-mock-inventory-20261012.png)

Fixture kiểm thử được tạo riêng sau khi xác nhận database rỗng; không nằm trong
initializer và không được đưa vào database của nhóm.

## Dữ liệu cũ và chạy local

Kiểm tra chỉ đọc database được cấu hình trong `run_backend.ps1`: có 6 sản phẩm,
3 SKU trùng mã trong bộ seed cũ, 4 dòng có doanh số legacy dương và 5 tài khoản
trùng tên demo. Có **1 dòng phiếu kho liên kết một SKU seed cũ**. Tên/mã giống
demo không đủ để xác định bản ghi còn nguyên trạng hoặc đang được nhóm sử dụng.
Không xoá hay sửa các bản ghi đó trong lần cập nhật này; lịch sử giao dịch được
giữ nguyên. API mới không dùng doanh số/cờ giao dịch giả trong các cột legacy.

Backend đang chạy ở 8080 và frontend 5500 của người dùng được giữ nguyên tiến
trình. Sau khi lấy code cần khởi động lại backend và tải lại frontend để dùng
đầy đủ thay đổi. Database đã có admin không cần cấu hình bootstrap lại; database
mới cần `APP_BOOTSTRAP_ADMIN_PASSWORD` và `APP_BOOTSTRAP_ADMIN_EMAIL` thực tế.
Kho/địa bàn thực cần được cấu hình trong DB; hiện chưa có màn CRUD hai danh mục
này. Chưa có sổ công nợ/phiếu thu và chưa có cấu hình định mức tồn kho, nên không
thể cung cấp số dư công nợ hoặc đánh giá an toàn tồn kho thực tế.
