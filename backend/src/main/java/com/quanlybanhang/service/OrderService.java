package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.OrderRequest;
import com.quanlybanhang.dto.response.OrderResponse;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(OrderRequest request, String username);

    List<OrderResponse> getAllOrders();

    OrderResponse getOrderById(Long id);

    OrderResponse cancelOrder(Long id, String username);

    /**
     * Quản lý kinh doanh duyệt đơn hàng có dòng bán dưới giá sàn (S2-10).
     */
    OrderResponse approveOrder(Long id, String username);

    /**
     * Quản lý kinh doanh từ chối đơn hàng có dòng bán dưới giá sàn (S2-10).
     */
    OrderResponse rejectOrder(Long id, String reason, String username);

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
     * Kiểm tra tồn khả dụng của SKU trong kho phục vụ đại lý và gợi ý số lượng tối đa (S4-03).
     */
    com.quanlybanhang.dto.response.OrderInventoryCheckResponse checkInventory(
            com.quanlybanhang.dto.request.OrderInventoryCheckRequest request);
}
