package com.quanlybanhang.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.dto.request.OrderRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.OrderResponse;
import com.quanlybanhang.model.OrderDraft;
import com.quanlybanhang.repository.OrderDraftRepository;
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
    private final OrderDraftRepository orderDraftRepository;
    private final ObjectMapper objectMapper;

    private static final String DRAFT_ROLES = "hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')";

    @GetMapping
    public ApiResponse getAllOrders() {
        List<OrderResponse> orders = orderService.getAllOrders();
        return ApiResponse.success("Danh sách đơn hàng", orders);
    }

    /** Lấy bản nháp tạo đơn của chính người dùng hiện tại (S3-09). */
    @GetMapping("/draft")
    @PreAuthorize(DRAFT_ROLES)
    public ApiResponse getDraft(Principal principal) throws Exception {
        return orderDraftRepository.findByUsername(principal.getName())
                .map(draft -> {
                    try {
                        return ApiResponse.success("Lấy bản nháp thành công", objectMapper.readTree(draft.getDraftData()));
                    } catch (Exception e) {
                        throw new IllegalStateException("Không đọc được bản nháp đã lưu", e);
                    }
                })
                .orElseGet(() -> ApiResponse.success("Chưa có bản nháp", null));
    }

    /** Lưu đè bản nháp của chính người dùng hiện tại (S3-09). */
    @PutMapping("/draft")
    @PreAuthorize(DRAFT_ROLES)
    public ApiResponse saveDraft(@RequestBody JsonNode draftData, Principal principal) throws Exception {
        if (draftData == null || draftData.isNull() || !draftData.isObject()) {
            throw new IllegalArgumentException("Dữ liệu bản nháp không hợp lệ");
        }

        OrderDraft draft = orderDraftRepository.findByUsername(principal.getName())
                .orElseGet(() -> OrderDraft.builder().username(principal.getName()).build());
        draft.setDraftData(objectMapper.writeValueAsString(draftData));
        orderDraftRepository.save(draft);
        return ApiResponse.success("Đã lưu bản nháp tạo đơn", draftData);
    }

    /** Xoá bản nháp sau khi ghi nhận đơn hoặc người dùng bỏ qua (S3-09). */
    @DeleteMapping("/draft")
    @PreAuthorize(DRAFT_ROLES)
    public ApiResponse deleteDraft(Principal principal) {
        orderDraftRepository.findByUsername(principal.getName()).ifPresent(orderDraftRepository::delete);
        return ApiResponse.success("Đã xoá bản nháp tạo đơn", null);
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
