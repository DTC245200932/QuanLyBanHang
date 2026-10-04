/**
 * Giữ tương thích với đường dẫn Đơn vị tính cũ.
 * Toàn bộ nghiệp vụ S2-07 sử dụng màn hình đã nối backend tại warehouse/units.html.
 */
(() => {
  "use strict";
  window.location.replace("/warehouse/units?tab=units");
})();
