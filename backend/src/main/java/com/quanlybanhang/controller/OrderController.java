package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.OrderRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.OrderResponse;
import com.quanlybanhang.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Controller Quản lý Đơn hàng (S2-07).
 * Khi ghi sổ đơn hàng, dù đặt theo thùng hay lốc đều được quy về đơn vị cơ sở (số lon).
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ApiResponse getAllOrders() {
        List<OrderResponse> orders = orderService.getAllOrders();
        return ApiResponse.success("Danh sách đơn hàng", orders);
    }

    @GetMapping("/{id}")
    public ApiResponse getOrderById(@PathVariable Long id) {
        OrderResponse order = orderService.getOrderById(id);
        return ApiResponse.success("Chi tiết đơn hàng", order);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<ApiResponse> createOrder(
            @Valid @RequestBody OrderRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        OrderResponse response = orderService.createOrder(request, username);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo đơn hàng thành công", response));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN')")
    public ApiResponse cancelOrder(@PathVariable Long id, Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        OrderResponse response = orderService.cancelOrder(id, username);
        String message = Boolean.TRUE.equals(response.getDealerLocked())
                ? "Hủy đơn hàng thành công (Cảnh báo: Đại lý đang bị khoá giao dịch)"
                : "Hủy đơn hàng thành công";
        return ApiResponse.success(message, response);
    }

    /**
     * Quản lý kinh doanh phê duyệt đơn hàng bán dưới giá sàn (S2-10).
     * Đơn hàng của đại lý bị khoá vẫn được xử lý tiếp nhưng có cảnh báo (S3-07).
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse approveOrder(@PathVariable Long id, Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        OrderResponse response = orderService.approveOrder(id, username);
        String message = Boolean.TRUE.equals(response.getDealerLocked())
                ? "Phê duyệt đơn hàng thành công (Cảnh báo: Đại lý đang bị khoá giao dịch)"
                : "Phê duyệt đơn hàng thành công";
        return ApiResponse.success(message, response);
    }

    /**
     * Quản lý kinh doanh từ chối đơn hàng bán dưới giá sàn (S2-10).
     * Đơn hàng của đại lý bị khoá vẫn được xử lý tiếp nhưng có cảnh báo (S3-07).
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse rejectOrder(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        OrderResponse response = orderService.rejectOrder(id, reason, username);
        String message = Boolean.TRUE.equals(response.getDealerLocked())
                ? "Từ chối đơn hàng thành công (Cảnh báo: Đại lý đang bị khoá giao dịch)"
                : "Từ chối đơn hàng thành công";
        return ApiResponse.success(message, response);
    }
}
