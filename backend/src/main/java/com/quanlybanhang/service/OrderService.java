package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.OrderRequest;
import com.quanlybanhang.dto.response.OrderResponse;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(OrderRequest request, String username);

    List<OrderResponse> getAllOrders();

    /**
     * Lấy danh sách đơn hàng kèm bộ lọc và tính tổng tiền tập kết quả (S4-07).
     * Áp dụng phân quyền dòng dữ liệu: Nhân viên kinh doanh chỉ thấy đơn của đại lý mình phụ trách.
     */
    com.quanlybanhang.dto.response.OrderFilterResult getOrders(com.quanlybanhang.dto.request.OrderFilterRequest filter);

    OrderResponse getOrderById(Long id);

    OrderResponse getOrderById(Long id, String username);

    /**
     * Huỷ đơn hàng có lý do và người thực hiện (S4-06).
     * Bắt buộc có lý do (reason không được để trống).
     * Đơn đã xuất kho thì không huỷ được.
     * Tự động nhả tồn kho đã giữ chỗ (reservedQuantity).
     */
    OrderResponse cancelOrder(Long id, String reason, String username);

    /** Overload huỷ đơn giữ tính tương thích ngược */
    OrderResponse cancelOrder(Long id, String username);

    /**
     * Cập nhật trạng thái vòng đời của đơn hàng (S4-06):
     * Nháp -> Chờ duyệt -> Đã duyệt -> Đang soạn hàng -> Đã xuất -> Đã giao -> Đóng
     */
    OrderResponse updateOrderStatus(Long id, String status, String note, String username);

    /**
     * Theo dõi chi tiết vòng đời đơn hàng và lịch sử chuyển trạng thái (S4-06).
     */
    com.quanlybanhang.dto.response.OrderTrackingResponse getOrderTracking(Long id, String username);

    /**
     * Quản lý kinh doanh duyệt đơn hàng có dòng bán dưới giá sàn (S2-10).
     */
    OrderResponse approveOrder(Long id, String username);

    /**
     * Quản lý kinh doanh từ chối đơn hàng có dòng bán dưới giá sàn (S2-10).
     */
    OrderResponse rejectOrder(Long id, String reason, String username);

    /**
     * Kiểm tra tồn khả dụng của SKU trong kho phục vụ đại lý và gợi ý số lượng tối đa (S4-03).
     */
    com.quanlybanhang.dto.response.OrderInventoryCheckResponse checkInventory(
            com.quanlybanhang.dto.request.OrderInventoryCheckRequest request);

    /**
     * Tính giá và chiết khấu tự động khi thêm một dòng hàng cho đại lý (S4-01).
     * Chặn thêm dòng hàng nếu SKU chưa có trong bảng giá hiệu lực của nhóm khách hàng.
     */
    com.quanlybanhang.dto.response.OrderLineItemPricingResponse calculateLineItemPricing(
            com.quanlybanhang.dto.request.OrderLineItemPricingRequest request);

    /**
     * Tính lại toàn bộ giá và chiết khấu cho danh sách dòng hàng khi số lượng thay đổi hoặc sửa giá (S4-01).
     * Chặn nếu có SKU chưa có trong bảng giá hiệu lực của nhóm khách hàng.
     */
    com.quanlybanhang.dto.response.OrderCalculateResponse calculateOrder(
            com.quanlybanhang.dto.request.OrderCalculateRequest request);

    /**
     * S4-09: Sao chép một đơn cũ thành đơn mới ở trạng thái Nháp (DRAFT).
     * Toàn bộ dòng hàng của đơn đã chọn được sao chép.
     * Giá và chiết khấu được áp lại theo bảng giá và chính sách hiện hành, không kế thừa giá cũ.
     * Bản sao luôn bắt đầu ở trạng thái Nháp.
     */
    OrderResponse copyOrder(Long id, String username);

    /**
     * S4-09: Sao chép các dòng hàng của đơn cũ trực tiếp vào bản nháp tạo đơn của người dùng.
     */
    com.fasterxml.jackson.databind.JsonNode copyOrderToDraft(Long id, String username);
}
