# Sửa lỗi Sprint 4 sau audit — 20/10/2026

Nhánh `fix/ma-tran-phan-quyen`, repo `E:\pjQLBH\QuanLyBanHang`. Đọc AGENTS.md và hai hướng dẫn trong SKILLS; giữ ma trận DB làm nguồn quyền, phân biệt quyền với phạm vi dữ liệu, không lưu nghiệp vụ trong browser storage. Không lấy code/merge từ nhánh khác, không sửa workbook hay DB thật.

Audit gốc: [SPRINT_4_AUDIT_20261020.md](SPRINT_4_AUDIT_20261020.md). Các phát hiện ở audit là trạng thái trước sửa; bảng hiện tại trong [status.md](../status.md).

## Thay đổi

1. S4-01: catalog/preview/tạo đơn cùng giá ngày đặt hiện tại, ngày giao vẫn lưu độc lập. Chặn sản phẩm ngừng kinh doanh; pricing theo đại lý có kiểm phạm vi nhân viên phụ trách.
2. S4-02: nợ và quá hạn giữ nguyên qua các bước giao hàng đến CLOSED/COMPLETED; không chỉ đếm RECORDED. Cả dịch vụ nợ và summary đại lý dùng chung tập trạng thái.
3. S4-03: kho gửi từ client phải khớp kho phục vụ đại lý đang hoạt động; đại lý chưa cấu hình kho không được tự dùng một kho khác. Tạo đơn, calculate và check-inventory dùng cùng quy tắc, giữ khóa tồn/atomic reserve hiện có.
4. S4-05/10: bỏ fallback duyệt theo role/tạo đơn. `ORDER_CONFIRM` chỉ xác nhận đơn cổng không vi phạm của đại lý phụ trách; `ORDER_APPROVE` riêng cho duyệt ngoại lệ. Kiểm lại công nợ và giữ tồn trong transaction. Tài khoản role khác được cấp quyền vẫn dùng được nếu đúng phạm vi, không hardcode chỉ SALES.
5. S4-06: server trả `nextLifecycleStatus` dựa trên quyền/kho/trạng thái; FE có nút chuyển từng bước và cập nhật tracking. Hủy sau xuất vẫn bị chặn như trước.
6. S4-10: endpoint preview dùng cùng tính giá/chiết khấu với tạo đơn; giỏ dùng tổng server, khóa gửi khi preview lỗi, bỏ phản hồi cũ, kiểm lại giá trước gửi. Giá thay đổi sau khi xác nhận phải xác nhận lại.
7. Seed demo: context ADMIN tạm thời chỉ trong thao tác bootstrap riêng, khôi phục trong finally; không dùng null-auth để vượt quyền ở service công khai.

## Ma trận quyền và triển khai

- Quyền mới hiển thị tên **Xác nhận đơn đại lý** (`ORDER_CONFIRM`). DB đã lưu cấu hình không tự được cấp thêm quyền: Admin phải tích và lưu. SALES/SALES_MANAGER mặc định có quyền này chỉ khi DB mới hoặc người quản trị chủ động khôi phục mặc định.
- `confirm` vẫn kiểm người phụ trách và vi phạm; cấp ORDER_CONFIRM không cho phép duyệt đơn dưới sàn/vượt hạn mức. ADMIN toàn quyền nhưng không bỏ qua quy tắc trạng thái/loại đơn.
- Thử cấp/thu hồi trên token đang đăng nhập: HTTP từ chối 403 khi bỏ quyền; FE đóng/ẩn nút liên quan khi đồng bộ. Thử thêm role VIEWER nhận quyền xác nhận và có phân công, không chỉ test admin.
- Bước tiếp theo yêu cầu WAREHOUSE_EXPORT và kho được giao; có kiểm thử thu hồi quyền, đổi sang kho khác, cấp lại kho đúng.

## Kiểm chứng

- Kết quả cuối: `mvn -q test` **538 test** qua (17 ca trong SprintMergeIntegrationTest); `node --test tests/*.test.cjs` **79 test** qua; `python -m unittest discover -s tests -p test_frontend_server.py` **5 test** qua. Không failure/error/skip. `git diff --check` không lỗi whitespace.
- Backend dùng H2 tách biệt, HTTP thật qua Spring Security/JWT/JPA/transaction. Có các ca giá ngày đặt, sản phẩm ngừng bán, kho không khớp/thiếu, nợ xuyên vòng đời/quá hạn, preview bằng tổng lúc tạo, vi phạm không được xác nhận thường, quyền thu hồi/cấp lại trên token cũ, kho thao tác và giữ tồn đồng thời.
- JavaScript có ca tổng/chiết khấu server, preview lỗi không tạo đơn, phản hồi trễ không ghi đè giỏ mới, đổi giá sau xác nhận không tự gửi, bỏ quyền khi trang đang mở.
- `tests/s4-workflows.browser.cjs`: Chrome thật với HTTP mock, 1440×900 và 390×844; chiết khấu 20.000đ/tổng 80.000đ; lỗi preview khóa gửi và hiện tính lại; xác nhận/chuyển vòng đời và thu hồi quyền đều qua, không pageerror. Đã xem ảnh mobile để kiểm bố cục.
- `tests/navigation.browser.cjs`: hồi quy chuyển trang, menu vẫn 312px → 312px; Back/Forward/Reload, mobile/reduced-motion qua, không pageerror.
- Chạy browser bằng server `serve_frontend.py --port 5519` và Playwright/Chrome; không dùng DB thật.

## Giới hạn còn lại

Chưa nghiệm thu với dữ liệu MySQL thật, máy in thật, tài khoản thật hay mọi trình duyệt. Hệ thống chưa có sổ thanh toán để đối trừ tiền đã thu: nợ hiện tính trên các đơn đã ghi nhận và không tự mất khi giao/đóng. Đây không phải kết luận mọi story đã hoàn thành tuyệt đối; các luồng không đổi được kiểm hồi quy, không thay thế UAT theo dữ liệu thực tế.
