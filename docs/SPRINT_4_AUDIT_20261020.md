# Đối chiếu Sprint 4 và sửa chuyển trang — 20/10/2026

## Phạm vi

- Nguồn: `D:\downloads\HỆ THỐNG BÁN HÀNG & KHO.xlsx`, sheet `4. Product Backlog`, vùng `A42:F51`: S4-01 đến S4-10, đặc biệt cột F là tiêu chí chấp nhận.
- Đối chiếu code trên `fix/ma-tran-phan-quyen`, baseline `9dd8c7e`, cùng các thay đổi điều hướng của đợt này. Không mặc định đây là trạng thái mới nhất của remote `develop`.
- Không dùng trạng thái “Chưa bắt đầu” trong Excel để kết luận trạng thái code. Không sửa Excel, backend hay nghiệp vụ Sprint 4 trong đợt này; chỉ sửa hiệu ứng/điều hướng frontend và bổ sung kiểm thử/tài liệu.
- “Cơ bản đạt” nghĩa là đã tìm được BE, FE, kết nối và kiểm thử cho luồng chính của AC; không phải nghiệm thu toàn bộ dữ liệu thật/UI hay bảo đảm không còn lỗi.

## Kết quả theo user story

| Story | AC cần đối chiếu | Backend | Frontend / nối API | Ma trận quyền | Đánh giá |
|---|---|---|---|---|---|
| S4-01 | Giá đúng nhóm/hiệu lực; đổi SL tính lại chiết khấu; giá dưới sàn cần duyệt; SKU thiếu giá bị chặn | Có `resolveOrderPriceList`, pricing từng dòng và toàn đơn; kiểm SKU có trong bảng; đánh dấu giá sàn | Có tại Tạo đơn hàng; nối `/price-lists/effective`, `/orders/line-item/pricing`, `/orders/calculate` | ORDER_CREATE; danh mục tham chiếu không buộc thêm PRODUCT_VIEW | Cơ bản đạt luồng chính; cần thống nhất nghiệp vụ ngày áp giá: luồng tạo đơn đang dùng ngày giao mong muốn nếu có |
| S4-02 | Hiện dư nợ/hạn mức/còn lại; vượt hạn mức cần duyệt; quá hạn chặn tạo | Có nhưng dư nợ và nợ quá hạn chỉ truy vấn đơn RECORDED | Có bảng công nợ/cảnh báo/chặn; nối `/dealers/{id}/debt`, `/orders/check-debt` | ORDER_CREATE hoặc FINANCE_VIEW_DEBT; sửa hạn mức cần FINANCE_MANAGE | **Chưa ổn**: đơn chưa thanh toán chuyển sang PICKING/EXPORTED/DELIVERED sẽ không còn nằm trong tập tính nợ hiện tại |
| S4-03 | Tồn của kho phục vụ đại lý; thực tế trừ giữ chỗ; chặn vượt/gợi ý; chốt đồng thời đúng | Có khóa tồn và cập nhật giữ chỗ có điều kiện; test hai giao dịch đồng thời qua. Tuy nhiên warehouseId client được ưu tiên hơn kho đại lý; thiếu kho còn fallback kho đầu tiên | Có tồn từng dòng/gợi ý/chặn; nối `/orders/check-inventory`, giá/tính toàn đơn và kiểm trước khi gửi | ORDER_CREATE hoặc WAREHOUSE_VIEW; việc chọn đúng kho vẫn là kiểm soát phạm vi dữ liệu riêng | **Chưa ổn hoàn toàn**: chưa ràng buộc warehouseId gửi lên phải đúng kho phục vụ đại lý; dữ liệu đại lý thiếu kho có thể dùng nhầm kho |
| S4-04 | Lịch sử 3 tháng/SL bình quân; thêm nhóm hàng lần trước; chỉ đại lý phụ trách | Có thống kê theo kỳ, đơn gần nhất, tính bình quân; có kiểm đại lý phụ trách | Có panel ngay Tạo đơn hàng; nối purchase-history và đường sao chép vào nháp; không gọi lịch sử nếu thiếu ORDER_VIEW | ORDER_VIEW; thêm/sao chép cần thêm ORDER_CREATE; RLS theo đại lý | Cơ bản đạt |
| S4-05 | Danh sách lý do/mức vi phạm; duyệt/từ chối/trả lại; hai thao tác sau cần ý kiến; giữ tồn sau duyệt; lịch sử bất biến | Có dữ liệu violations/debtExceeded/priceGap; bắt buộc lý do; giữ chỗ khi duyệt; entity chặn update/delete lịch sử | Có Duyệt đơn hàng, popup chi tiết và ba thao tác; nối approve/reject/return/history | ORDER_APPROVE và ORDER_VIEW để dùng trang; API không dùng tên role thay quyền | Cơ bản đạt; vấn đề xác nhận đơn cổng bởi nhân viên phụ trách được ghi riêng tại S4-10 |
| S4-06 | 7 bước vòng đời và nhánh Hủy; hủy có lý do/nhả giữ chỗ; sau xuất không được hủy | Có tracking/lịch sử, chuyển từng bước, hủy/nhả tồn, chặn hủy sau xuất; có test | Đơn hàng → chi tiết có vòng đời và hủy; tracking từ server | ORDER_VIEW/ORDER_CANCEL; chuyển vòng đời/giao hàng cần WAREHOUSE_EXPORT và phạm vi kho | Cơ bản đạt cho AC theo dõi/hủy; UI kho chưa có đầy đủ thao tác chuyển từng bước nên chưa nghiệm thu toàn chuỗi bằng thao tác web |
| S4-07 | Lọc trạng thái/đại lý/nhân viên/khu vực/thời gian; tổng tập lọc; sales chỉ xem tuyến mình | Có specification/RLS và tổng toàn tập trước phân trang | Có menu Đơn hàng, bộ lọc và tổng tiền theo tập lọc | ORDER_VIEW; menu không còn chặn Thủ kho chỉ vì tên role | Cơ bản đạt |
| S4-08 | In/PDF đủ đại lý/điểm giao/dòng hàng/chiết khấu/tổng; mã đơn/mã vạch | Có PDF với font tiếng Việt, chi tiết giao hàng, chiết khấu, Code128; có API barcode | Có nối in/PDF; không chỉ mở một mẫu tĩnh phía client | ORDER_VIEW; kiểm phạm vi trước xuất/in | Cơ bản đạt; chưa in máy in thật trong đợt này |
| S4-09 | Sao chép toàn dòng; giá/chiết khấu hiện hành, không giá cũ; bản sao Nháp | Có `/copy` tạo DRAFT với giá/chiết khấu mới; `/copy-to-draft` lưu nháp, bỏ giá cũ để FE tính lại | Có nút sao chép, mở nháp DB và tính lại pricing | Cả ORDER_CREATE + ORDER_VIEW cho copy/copy-to-draft/đơn gần nhất; có RLS | Cơ bản đạt |
| S4-10 | Đại lý thấy catalog/giá nhóm mình; giỏ/tổng/công nợ; luôn Chờ duyệt, NV phụ trách xác nhận; dùng trên điện thoại | Có tài khoản liên kết/giá nhóm/phạm vi đại lý; đơn cổng luôn PENDING_APPROVAL. Endpoint approve/reject chỉ nhận ORDER_APPROVE | Có catalog/giỏ/công nợ và responsive; giỏ đang hiện chiết khấu 0, tổng là subtotal phía client | Cổng dùng ORDER_VIEW/PRODUCT_VIEW/ORDER_CREATE; sales mặc định không có ORDER_APPROVE | **Chưa ổn**: luồng NV phụ trách xác nhận không đi được qua API với quyền mặc định; tổng xem trước có thể khác tổng server khi có chiết khấu |

Kết luận: **7 story cơ bản đạt luồng chính; S4-02, S4-03 và S4-10 còn vấn đề rõ trong code**. Không kết luận toàn Sprint 4 hoàn tất chỉ vì có cả hai phía hoặc tất cả test hiện tại qua.

## Bằng chứng cho các điểm còn thiếu

1. **S4-02 — tính nợ theo trạng thái đơn**: `backend/src/main/java/com/quanlybanhang/service/DealerDebtServiceImpl.java:67` cộng nợ bằng `sumTotalAmountByDealerIdAndStatus(..., "RECORDED")`; dòng 74 cũng chỉ tìm RECORDED để tính quá hạn. Chuyển trạng thái giao hàng không phải bằng chứng đã thu tiền. Cần xác định nguồn số dư/thu tiền hoặc các trạng thái còn nợ phù hợp, rồi kiểm thử việc chuyển vòng đời không tự làm giảm nợ.
2. **S4-03 — chọn kho chưa ràng buộc đại lý**: `OrderServiceImpl.java:313` nhận warehouseId client trước kho gắn với đại lý; `checkInventory` tại dòng 1713 có cùng thứ tự ưu tiên và fallback kho active đầu tiên. Khóa tồn/atomic update chống vượt tồn có thật ở dòng 1260/1311, nhưng không chứng minh đã chọn đúng kho của đại lý. Cần tách quyền thao tác khỏi kiểm tra kho phục vụ.
3. **S4-10 — xác nhận khác với duyệt ngoại lệ**: `controller/OrderController.java:322` bảo vệ approve bằng ORDER_APPROVE. `RolePermissionConfig.java` không cấp ORDER_APPROVE mặc định cho ROLE_SALES. `OrderServiceImpl.java:2967` còn phân biệt sales phụ trách xác nhận đơn cổng không vi phạm, nhưng nhánh đó không đi được qua controller nếu thiếu quyền duyệt. Cấp quyền duyệt rộng chỉ để vượt 403 không phải giải pháp xác nhận theo tuyến.
4. **S4-10 — số tiền trước gửi**: `frontend/js/dealer-portal.js:515` đặt chiết khấu bằng 0, tổng giỏ và công nợ dự kiến tính từ subtotal; backend lại áp chính sách chiết khấu khi tạo đơn. Workbook không yêu cầu một endpoint preview cụ thể, nhưng yêu cầu tổng tiền xem trước; khi có chiết khấu thì cần đồng bộ số này với server.
5. **Cần chốt nghiệp vụ, không tự quy thành lỗi**: S4-01 dùng ngày giao mong muốn để tìm giá trong luồng tạo đơn (`OrderServiceImpl.java:288`), trong khi AC chỉ nói bảng giá “đang hiệu lực”. Cần thống nhất là giá ngày đặt hay ngày giao. S4-06 có API vòng đời nhưng chưa có đủ UI kho để diễn tập mọi bước; AC của sales vẫn đã có theo dõi/hủy.

## Sửa menu nhảy và chuyển trang giật

| Hạng mục | Backend | Frontend | Nối API / quyền |
|---|---|---|---|
| Giữ cuộn menu | Không đổi | Lưu tọa độ UI vào history của trình duyệt; trang mới kế thừa từ entry nguồn; Back/Forward/Reload dùng entry đích | Không lưu đơn hàng/giỏ vào storage; không mở quyền mới |
| Dựng sidebar | Không đổi | Dựng shell đồng bộ trước await; menu inert khi đồng bộ DB; không replaceChildren nếu menu không đổi; giữ scroll khi quyền thay đổi | Vẫn gọi Auth.ready và kiểm quyền trang sau đồng bộ |
| Hiệu ứng | Không đổi | Chuyển cảnh cross-document gốc của browser; chỉ vùng trắng fade 80–120ms, sidebar/topbar không trượt/fade; xử lý hủy transition từ head; giảm chuyển động tắt animation | Không đổi form, API, dữ liệu hay các script nghiệp vụ; vẫn là web nhiều trang, không chuyển thành SPA |
| Cuộn/chiều rộng | Không đổi | Chặn wheel lan từ menu sang nội dung, tắt scroll anchoring trong menu, giữ gutter scrollbar ổn định | Không ảnh hưởng phân quyền |

API trình duyệt không được hỗ trợ thì chuyển trang HTML bình thường; hiệu ứng chỉ là cải tiến hiển thị. Browser cũ không có Navigation API chỉ có fallback khôi phục entry hiện tại bằng history.state. Không tuyên bố đã kiểm mọi trình duyệt cũ.

Tham khảo kỹ thuật: [Chrome — cross-document view transitions](https://developer.chrome.com/docs/web-platform/view-transitions/cross-document), [MDN — Navigation state](https://developer.mozilla.org/en-US/docs/Web/API/Navigation/updateCurrentEntry).

## Kiểm chứng

- Backend: chạy lại `mvn -q test`, **529 test qua, không failure/error**. Các test này không bao phủ hết các thiếu sót AC vừa nêu.
- JavaScript: `node --test tests/*.test.cjs`, **74 test qua**, gồm khôi phục cuộn, không dựng lại menu giống nhau, thu hồi quyền vẫn 403 và xử lý transition hủy.
- Định tuyến: `python -m unittest discover -s tests -p test_frontend_server.py`, **5 test qua**.
- Chrome headless thật, viewport **1440×800** và **390×844**, API được mock để không đụng dữ liệu thật: bấm menu từ dưới, Back/Forward/Reload, đi qua Dashboard → Quản lý tài khoản → Tạo đơn → Sản phẩm → Hồ sơ → Đơn hàng; vẫn có nội dung và chỉ một sidebar/main. Cuộn trước/sau chuyển Dashboard → Users đều **312px**, nav cao **668px**. Native transition bật khi không giảm chuyển động; tắt khi giảm chuyển động; menu mobile đóng sau chuyển trang; **không có pageerror**. Đã xem ảnh desktop/mobile để kiểm tra bố cục.
- Có thể chạy lại bài browser bằng `tests/navigation.browser.cjs`, cài Playwright/Chrome sẵn hoặc dùng runtime bundled và NODE_PATH; NAV_TEST_URL mặc định là `http://127.0.0.1:5519`.
- Chưa nghiệm thu MySQL thật, đơn và công nợ thật, máy in thật, mọi kích thước/phiên bản browser; không sửa các vấn đề nghiệp vụ audit phát hiện trong đợt này.
