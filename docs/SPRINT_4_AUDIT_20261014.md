# Rà soát Sprint 4 — 14/10/2026

## Kết luận

**Sprint 4 chưa đủ điều kiện nghiệm thu.** Có mã backend và frontend không đồng nghĩa với đã nối và đáp ứng đủ tiêu chí backlog.

Phạm vi: S4-01 đến S4-10, 10 story / 45 point trong backlog được cung cấp; `main` tại `6ccb36b`,
mã của các PR đang mở #65, #66, #67, #71 và các PR đã merge #64, #68, #70.
Đã đọc hai hướng dẫn trong `SKILLS`, cấu trúc controller/service/model/repository, DTO, bảo mật,
các trang và API frontend, kiểm thử liên quan và tài liệu API.

| Task | Backend | Frontend | Kết nối và mức hoàn thành |
|---|---|---|---|
| S4-01 — Áp giá, chiết khấu | Có, #68 đã merge | Có, #64 đã merge | Luồng chính đã nối. Chưa đạt đầy đủ: server nhận `priceListId` sai nhóm; nhánh thêm từ lịch sử chưa dùng chung kiểm tra bảng giá. |
| S4-02 — Hạn mức công nợ | Có trong #71 | Có trong #65 | Đã sửa nối API và DTO trên hai PR. Hiển thị, cảnh báo vượt hạn mức và chặn quá hạn có code/test. Công nợ hiện suy ra từ đơn `RECORDED`, chưa có số dư kế toán sau thanh toán. |
| S4-03 — Tồn khả dụng | Có API tồn thực tế; thiếu kiểm tồn/giữ chỗ trong luồng đơn | Có khung giao diện và module ở #67 | Chưa nối: module không được gọi từ `create-order.js`; SALES bị 403 khi gọi API tồn. Chưa xử lý chốt đồng thời. |
| S4-04 — Lịch sử mua hàng | Chưa có API lịch sử | Có ở #66 | Frontend gọi API chưa tồn tại. Thêm tất cả hiện lấy danh sách tổng hợp ba tháng, chưa lấy nhóm hàng của đơn gần nhất. |
| S4-05 — Duyệt đơn | Có Duyệt/Từ chối và nhật ký; thiếu Trả lại sửa, bắt buộc ý kiến, giữ chỗ | Có hai nút trong danh sách đơn của đại lý | Một phần. Chưa có màn hình chờ duyệt thể hiện đầy đủ lý do và mức vi phạm; chưa có cả ba hành động. |
| S4-06 — Vòng đời, huỷ | Có huỷ cơ bản; thiếu lý do và các bước vòng đời/nhả giữ chỗ | Có modal và nhãn trạng thái, #70 đã merge | Một phần. Frontend gửi lý do nhưng server bỏ qua. Các nhãn Đang soạn/Đã xuất/Đã giao/Đóng chưa có luồng backend tương ứng. |
| S4-07 — Danh sách, lọc đơn | Có GET danh sách; thiếu bộ lọc, tổng tập lọc và phạm vi đại lý | Có danh sách trong modal đại lý; chưa có trang danh sách toàn công ty | Chưa đầy đủ. GET `/orders` trả cả đơn của đại lý chưa được phân công cho SALES. |
| S4-08 — In/PDF đơn hàng | Chưa có | Chưa có | Chưa triển khai. Barcode đơn hàng cũng chưa có; barcode quy cách sản phẩm là dữ liệu khác. |
| S4-09 — Sao chép đơn | Chưa có | Chưa có | Chưa triển khai. Lưu/mở bản nháp cá nhân chưa đáp ứng sao chép đơn cũ và áp lại giá hiện hành. |
| S4-10 — Cổng đại lý | Chưa có tài khoản/liên kết đại lý hoặc API cổng | Chưa có trang cổng/giỏ hàng | Chưa triển khai. Chưa có cơ chế chỉ thấy giá của chính nhóm mình và gửi đơn cho nhân viên phụ trách xác nhận. |

## PR và các sửa đổi đã thực hiện

| PR | Sửa conflict / kết nối | Kết quả trên GitHub |
|---|---|---|
| [#71](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/71) | Giữ cả endpoint áp giá và công nợ, cả hai bộ test; giữ nhóm giá theo đại lý; bổ sung fixture giá và bean công nợ cho test seed; seed đơn cần duyệt đi qua hành động duyệt trước khi xuất. | `MERGEABLE / CLEAN`, head `a9c899f` |
| [#65](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/65) | Giữ reset bảng giá và công nợ; đổi API sai `/finance/debt/{id}/check` sang `/dealers/{id}/debt`; đọc đúng cờ quá hạn; hiển thị lỗi/chặn chốt đơn khi tải công nợ lỗi; bỏ cờ client không thuộc DTO; thêm 4 test hồi quy. | `MERGEABLE / CLEAN`, head `6ab5d8d` |
| [#66](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/66) | Giải quyết conflict phần đầu file và danh sách API, giữ áp giá lẫn lịch sử. | `MERGEABLE / CLEAN`, head `24c9ddb` |
| [#67](https://github.com/KoAiBitTuiLaAi/QuanLyBanHang/pull/67) | Giải quyết conflict CSS, giữ trường kho phục vụ và bố cục tạo đơn hiện tại. | `MERGEABLE / CLEAN`, head `f55f022` |

Đã push commit bình thường lên các branch hiện có, không rewrite lịch sử.
Branch `feature/kiem-tra-ket-noi-sprint-4` ghép toàn bộ commit của bốn PR để kiểm tra chung,
giải quyết thêm conflict giữa phần công nợ và lịch sử, giữ cả hai request và CSS.
Branch này cũng sửa toast huỷ đơn để giữ đúng mã đơn và bỏ thông báo nhả tồn chưa được backend thực hiện.
Các PR gốc và `main` trên GitHub được giữ nguyên trạng thái mở/chưa merge để nhóm review.

## Kiểm chứng

- Backend: **336 test, 0 failure, 0 error, 0 skipped**, chạy trên mã Java của #71 sau khi sửa conflict.
  Các PR frontend ghép tiếp không thay đổi mã Java này.
- Frontend bản ghép: **44 test đều qua**, gồm 4 test công nợ mới và 7 test module tồn của #67.
- GitHub chưa có CI checks trên bốn PR; `CLEAN` là kết quả kiểm tra merge, không phải chứng nhận chức năng.
- Kiểm tra API thực chạy Spring Boot + H2 trong bộ nhớ, fixture demo riêng: **14 phép kiểm tra, 5 đạt, 9 tiêu chí chưa đạt**.
  Kết quả chi tiết: [sprint4-api-checks-20261014.json](sprint4-api-checks-20261014.json).
  Script: [audit_sprint4_api.py](../tests/audit_sprint4_api.py). Script tạo đơn/đại lý thử nghiệm,
  dùng server H2 riêng ở cổng 18184. Exit 0 nghĩa là audit chạy xong; đọc `failedAcceptanceCriteria` để đánh giá chức năng.
- API tạo đơn 15 dòng cho số tiền xem trước và lưu khớp nhau. Đây là kiểm chứng API;
  chưa xác nhận nhập 15 dòng trên điện thoại bằng trình duyệt ở 360px.
- Chưa kiểm chứng trên MySQL thật. Database sản phẩm và dữ liệu người dùng không bị thay đổi bởi các phép thử này;
  server H2 đã được dừng sau audit.

### Các lỗi đã tái hiện bằng API

| Task | Kết quả thực chạy | Tiêu chí chưa đạt |
|---|---|---|
| S4-01 | `/orders/calculate` nhận bảng giá `KHACH_LE` cho đại lý `DAI_LY_CAP_1`, trả 200. | Giá phải đúng nhóm đại lý; cần kiểm tra nhóm, trạng thái và ngày hiệu lực của `priceListId` ở server. |
| S4-03 | SALES gọi `/warehouse/inventory` bị 403. | Nhân viên tạo đơn phải xem được tồn khả dụng đúng kho qua API phù hợp. |
| S4-03 | Kho thử có 840 lon; hai request đồng thời mỗi request đặt 630 lon đều trả 201 / `RECORDED`. | Chưa chặn tổng đặt vượt tồn; chưa có giữ chỗ/giao dịch đồng thời đúng. `warehouseId` gửi lên cũng chưa thuộc DTO đơn hàng. |
| S4-04 | API `/dealers/{id}/purchase-history?months=3` trả 500; log là `NoResourceFoundException`. | Chưa có endpoint frontend đang gọi; lỗi thiếu route đang bị handler chung đổi thành 500. |
| S4-05 | Từ chối đơn không nhập ý kiến vẫn trả 200. | Server phải bắt buộc ý kiến cho Từ chối và Trả lại sửa. |
| S4-06 | JSON `{ "reason": "..." }` huỷ thành công nhưng lý do không có trong chi tiết đơn hoặc nhật ký. | Lý do phải được nhận, kiểm tra và lưu phía server. |
| S4-06 | Huỷ đơn không gửi lý do vẫn trả 200. | Backend phải bắt buộc lý do, không chỉ validate trên frontend. |
| S4-07 | SALES xem được đơn của đại lý chưa phân công trong GET `/orders`. | Quyền theo đại lý phải áp ở danh sách, chi tiết và thao tác đơn. |
| S4-07 | GET `/orders?dealerId=...&status=RECORDED` trả 13 dòng gồm các đại lý/trạng thái khác. | Query bộ lọc chưa được xử lý; chưa có tổng tiền của tập kết quả lọc. |

## Đối chiếu từng tiêu chí chấp nhận

### S4-01

1. Giá theo bảng giá hiệu lực của nhóm đại lý: có luồng tự chọn, nhưng có thể chọn `priceListId` sai nhóm;
   nhánh truyền ID cũng chưa kiểm tra active/ngày bắt đầu/ngày kết thúc.
2. Tính lại chiết khấu khi đổi số lượng: frontend gọi `/orders/calculate`, backend tính theo tổng sản lượng; có test.
3. Sửa giá thủ công dưới giá sàn cần duyệt: có cả frontend/backend, test API xác nhận cờ `requiresApproval`.
4. SKU không có giá hiệu lực bị chặn: có kiểm tra trong luồng thêm thông thường/backend;
   `addHistoryProduct` của #66 tạo dòng trực tiếp nên cần nối chung luồng thêm hàng.

### S4-02

1. Hiện công nợ/hạn mức/còn lại: đã nối đúng DTO sau sửa #65, kiểm tra API và test JS đều qua.
2. Vượt hạn mức cần duyệt: có pre-check và kiểm tra lại khi tạo đơn, test qua.
3. Quá số ngày nợ cho phép chặn tạo: backend có `DealerDebtOverdueException`, test service có trường hợp quá hạn;
   frontend đã đọc đúng `hasOverdueDebt/isBlocked`. Chưa đối soát với phiếu thu/công nợ thật vì thiếu các dữ liệu đó.

`DealerDebtServiceImpl` hiện cộng tổng đơn `RECORDED`, kiểm tra quá hạn theo ngày ghi sổ;
`debtLimit = 0` được xem như không giới hạn, `maxDebtDays = 0` bỏ qua quá hạn. Cần thống nhất các quy tắc này với nghiệp vụ.

### S4-03

1. Hiện tồn đúng kho phục vụ: HTML có kho và cột tồn nhưng select chưa được nạp, dòng JS vẫn tạo 8 cột trong bảng 9 cột.
   `OrderInventory` chưa được gọi từ màn hình. Chưa có quan hệ kho phục vụ trong đại lý/đơn hàng.
2. Thực tế trừ giữ chỗ: module có phép tính, DB chưa có giữ chỗ. Module đang cộng đơn ở mọi kho và cả `PENDING_APPROVAL`,
   trong khi S4-05 yêu cầu được duyệt mới giữ chỗ.
3. Chặn vượt tồn/gợi ý tối đa: hàm và test riêng có, chưa gắn vào nhập số lượng và chốt đơn.
4. Đồng thời không vượt tồn: API thực chưa đạt; các repository tồn/đơn hiện chưa có khoá phù hợp cho thao tác này.

### S4-04

1. Ba tháng gần nhất, số lượng bình quân: FE có panel; thiếu truy vấn/API backend.
2. Thêm nhanh nhóm hàng đơn gần nhất: FE đang thêm từ toàn danh sách tổng hợp, chưa lưu/lấy đơn gần nhất.
3. Chỉ đại lý được phân công: API lịch sử chưa có nên chưa có kiểm quyền để xác nhận.

### S4-05

1. Danh sách chờ duyệt thể hiện lý do/mức vi phạm: backend có `approvalReason`, nhưng bảng FE chưa hiển thị lý do/mức vi phạm.
2. Duyệt/Từ chối/Trả lại sửa, hai hành động sau có ý kiến: mới có hai hành động; Từ chối cho phép bỏ trống.
3. Duyệt mới giữ chỗ/chờ xuất: approve chuyển `RECORDED`, chưa giữ chỗ.
4. Lịch sử không sửa/xoá: thao tác ghi vào audit log, API hiện chỉ đọc log; cần hoàn thiện lịch sử cho cả ba quyết định.

### S4-06

1. Đủ vòng đời: backend chưa có chuyển bước soạn hàng, xuất, giao, đóng; bản nháp là JSON riêng theo người dùng.
2. Huỷ có lý do và nhả giữ chỗ: frontend yêu cầu lý do; backend chưa nhận/lưu, chưa có giữ chỗ để nhả.
3. Đã xuất không huỷ: frontend ẩn nút theo nhãn; `cancelOrder` backend chưa kiểm trạng thái,
   phiếu xuất cũng chưa liên kết với đơn để bảo vệ điều kiện này.

### S4-07

1. Lọc trạng thái/đại lý/nhân viên/khu vực/thời gian: controller GET không nhận các bộ lọc.
2. Tổng tiền tập lọc: chưa có API/hiển thị tương ứng.
3. SALES chỉ thấy đơn đại lý mình: modal từng đại lý có scope, nhưng GET danh sách và GET chi tiết đơn chưa có scope tương ứng.

### S4-08

1. Mẫu in/PDF đủ thông tin đại lý, điểm giao, dòng hàng, chiết khấu, tổng: chưa có.
2. Mã đơn và barcode trên mẫu: mã đơn đã có trong dữ liệu, chưa có mẫu và barcode đơn.

### S4-09

1. Sao chép toàn bộ dòng đơn cũ: chưa có luồng/API.
2. Áp lại giá/chiết khấu hiện hành: chưa có luồng sao chép để gọi lại pricing.
3. Bản sao luôn Nháp: chưa có.

### S4-10

1. Đăng nhập đại lý/chỉ giá nhóm mình: chưa có tài khoản gắn đại lý; bảy role hiện tại là nhân sự nội bộ.
2. Giỏ hàng/tổng/công nợ trước gửi: chưa có.
3. Gửi luôn Chờ duyệt, nhân viên phụ trách xác nhận: chưa có nguồn đơn cổng hoặc quy tắc này.
4. Mobile: chưa có trang cổng để kiểm chứng.

## Công việc còn lại cho nhóm

1. **Backend S4-03 + S4-05/S4-06:** gắn kho phục vụ, tồn giữ chỗ trong DB, kiểm tồn nguyên tử, giữ khi duyệt,
   nhả khi huỷ và chuyển giữ chỗ sang xuất thực tế. Kiểm thử hai người chốt trên MySQL.
2. **Backend S4-01/S4-07:** kiểm bảng giá đúng nhóm/hiệu lực và giới hạn đọc/thao tác đơn theo đại lý được phân công.
3. **Backend S4-05/S4-06:** nhận và lưu ý kiến/lý do bắt buộc, Trả lại sửa, kiểm trạng thái đã xuất, liên kết phiếu kho và vòng đời.
4. **Backend S4-04:** lịch sử ba tháng và đơn gần nhất, có scope nhân viên; **Frontend S4-04** thêm từ đơn gần nhất qua cùng luồng áp giá/kiểm tồn.
5. **Frontend S4-03:** nạp kho, gọi API khả dụng, hiển thị từng dòng, chặn vượt tồn/gợi ý tối đa và kiểm lại trước gửi.
6. **Backend + Frontend S4-07:** danh sách toàn công ty, các bộ lọc và tổng tập lọc; **Frontend S4-05** danh sách chờ duyệt đủ lý do/mức vi phạm.
7. **S4-02:** xác nhận mô hình công nợ và ý nghĩa hạn mức/số ngày bằng 0; nối số dư/thu tiền khi có nghiệp vụ kế toán tương ứng.
8. **Backend + Frontend S4-08/S4-09/S4-10:** triển khai PDF/barcode, sao chép với giá hiện hành và cổng đặt hàng đại lý.
9. Kiểm tra bằng trình duyệt ở 360px với 15 dòng và các vai trò sau khi hoàn thiện các luồng còn thiếu.
