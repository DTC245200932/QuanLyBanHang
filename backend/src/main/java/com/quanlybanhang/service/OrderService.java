package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.OrderRequest;
import com.quanlybanhang.dto.response.OrderResponse;

import java.time.LocalDate;
import java.util.List;

public interface OrderService {

    OrderResponse createOrder(OrderRequest request, String username);

    List<OrderResponse> getAllOrders();

    OrderResponse getOrderById(Long id);

    /**
     * Huỷ đơn hàng không cần lý do (tương thích ngược).
     * @deprecated Dùng cancelOrder(id, reason, username) thay thế.
     */
    @Deprecated
    OrderResponse cancelOrder(Long id, String username);

    /**
     * Huỷ đơn hàng với lý do bắt buộc (S4-06).
     * Không cho huỷ đơn đã ở trạng thái EXPORTED hoặc DELIVERED.
     */
    OrderResponse cancelOrder(Long id, String reason, String username);

    /**
     * Quản lý kinh doanh duyệt đơn hàng (S2-10, S4-05).
     */
    OrderResponse approveOrder(Long id, String username);

    /**
     * Quản lý kinh doanh duyệt đơn hàng kèm ý kiến phê duyệt tuỳ chọn (S4-05).
     */
    OrderResponse approveOrder(Long id, String comment, String username);

    /**
     * Quản lý kinh doanh từ chối đơn hàng (S2-10, S4-05). Bắt buộc nhập ý kiến/lý do.
     */
    OrderResponse rejectOrder(Long id, String reason, String username);

    /**
     * Quản lý kinh doanh trả lại sửa đơn hàng (S4-05). Bắt buộc nhập ý kiến/lý do.
     */
    OrderResponse returnOrder(Long id, String reason, String username);

    /**
     * Cập nhật trạng thái duyệt đơn hàng qua request chung (S4-05).
     */
    OrderResponse updateOrderStatus(Long id, com.quanlybanhang.dto.request.OrderStatusUpdateRequest request, String username);

    /**
     * Lấy danh sách đơn hàng có phân trang và bộ lọc đầy đủ (S4-05, S4-07).
     */
    org.springframework.data.domain.Page<OrderResponse> getOrders(
            String status,
            String approvalReason,
            String search,
            Boolean approvalRequired,
            Long dealerId,
            String createdBy,
            Long territoryId,
            LocalDate dateFrom,
            LocalDate dateTo,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Tương thích ngược - gọi getOrders đầy đủ.
     */
    default org.springframework.data.domain.Page<OrderResponse> getOrders(
            String status,
            String approvalReason,
            String search,
            Boolean approvalRequired,
            org.springframework.data.domain.Pageable pageable) {
        return getOrders(status, approvalReason, search, approvalRequired, null, null, null, null, null, pageable);
    }

    /**
     * Lấy lịch sử duyệt đơn hàng (S4-05).
     */
    List<com.quanlybanhang.dto.response.OrderApprovalHistoryResponse> getOrderApprovalHistory(Long orderId);

    /**
     * Tính giá và chiết khấu tự động khi thêm một dòng hàng cho đại lý (S4-01).
     */
    com.quanlybanhang.dto.response.OrderLineItemPricingResponse calculateLineItemPricing(
            com.quanlybanhang.dto.request.OrderLineItemPricingRequest request);

    /**
     * Tính lại toàn bộ giá và chiết khấu cho danh sách dòng hàng (S4-01).
     */
    com.quanlybanhang.dto.response.OrderCalculateResponse calculateOrder(
            com.quanlybanhang.dto.request.OrderCalculateRequest request);

    /**
     * Lấy lịch sử mua hàng của đại lý trong N tháng gần nhất (S4-04).
     */
    DealerPurchaseHistoryResult getDealerPurchaseHistory(Long dealerId, int months, String currentUsername);

    /**
     * Sao chép đơn hàng cũ thành đơn nháp mới, áp lại giá/chiết khấu hiện hành (S4-09).
     */
    OrderResponse copyOrder(Long sourceOrderId, String username);

    /**
     * Tạo nội dung HTML để in/xuất PDF đơn hàng (S4-08).
     */
    String generateOrderPrintHtml(Long orderId);

    // ── Data records (S4-04) ──

    record DealerPurchaseHistoryResult(
            Long dealerId,
            String dealerName,
            String dealerCode,
            int months,
            java.time.LocalDate fromDate,
            java.time.LocalDate toDate,
            List<ProductPurchaseSummary> products,
            List<RecentOrderSummary> recentOrders
    ) {}

    record ProductPurchaseSummary(
            Long productId,
            String productCode,
            String productName,
            String categoryName,
            java.math.BigDecimal totalBaseQuantity,
            String baseUnit,
            java.math.BigDecimal averageMonthlyQuantity,
            int orderCount,
            java.math.BigDecimal lastPrice,
            java.time.LocalDateTime lastOrderAt
    ) {}

    record RecentOrderSummary(
            Long orderId,
            String orderCode,
            String status,
            java.math.BigDecimal totalAmount,
            java.time.LocalDateTime createdAt,
            List<String> productNames
    ) {}
}
