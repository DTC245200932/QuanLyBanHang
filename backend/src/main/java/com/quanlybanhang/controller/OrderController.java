package com.quanlybanhang.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.dto.request.OrderDebtCheckRequest;
import com.quanlybanhang.dto.response.OrderDebtCheckResponse;
import com.quanlybanhang.dto.request.OrderCalculateRequest;
import com.quanlybanhang.dto.request.OrderLineItemPricingRequest;
import com.quanlybanhang.dto.request.OrderRequest;
import com.quanlybanhang.dto.request.OrderStatusUpdateRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.OrderApprovalHistoryResponse;
import com.quanlybanhang.dto.response.OrderCalculateResponse;
import com.quanlybanhang.dto.response.OrderLineItemPricingResponse;
import com.quanlybanhang.dto.response.OrderResponse;
import com.quanlybanhang.model.OrderDraft;
import com.quanlybanhang.repository.OrderDraftRepository;
import com.quanlybanhang.service.DealerDebtService;
import com.quanlybanhang.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controller Quản lý Đơn hàng (S2-07).
 * Khi ghi sổ đơn hàng, dù đặt theo thùng hay lốc đều được quy về đơn vị cơ sở (số lon).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final DealerDebtService dealerDebtService;
    private final OrderDraftRepository orderDraftRepository;
    private final ObjectMapper objectMapper;

    public OrderController(OrderService orderService,
                           DealerDebtService dealerDebtService,
                           OrderDraftRepository orderDraftRepository,
                           ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.dealerDebtService = dealerDebtService;
        this.orderDraftRepository = orderDraftRepository;
        this.objectMapper = objectMapper;
    }

    private static final String DRAFT_ROLES = "hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')";

    /**
     * Lấy danh sách đơn hàng. Hỗ trợ lọc theo trạng thái, lý do cần duyệt, tìm kiếm, đại lý,
     * người tạo, khu vực, khoảng ngày và phân trang (S4-05, S4-07).
     */
    @GetMapping
    public ApiResponse getAllOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String approvalReason,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean approvalRequired,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) Long territoryId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {

        boolean hasFilter = status != null || approvalReason != null || search != null
                || approvalRequired != null || dealerId != null || createdBy != null
                || territoryId != null || dateFrom != null || dateTo != null
                || page != null || size != null || sort != null;

        if (!hasFilter) {
            List<OrderResponse> orders = orderService.getAllOrders();
            return ApiResponse.success("Danh sách đơn hàng", orders);
        }

        int pageIndex = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        Sort sortObj = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String prop = parts[0].trim();
            Sort.Direction dir = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()))
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;
            sortObj = Sort.by(dir, prop);
        }

        LocalDate fromDate = dateFrom != null && !dateFrom.isBlank() ? LocalDate.parse(dateFrom) : null;
        LocalDate toDate = dateTo != null && !dateTo.isBlank() ? LocalDate.parse(dateTo) : null;

        Pageable pageable = PageRequest.of(pageIndex, pageSize, sortObj);
        Page<OrderResponse> orderPage;
        if (dealerId == null && createdBy == null && territoryId == null && fromDate == null && toDate == null) {
            orderPage = orderService.getOrders(status, approvalReason, search, approvalRequired, pageable);
        } else {
            orderPage = orderService.getOrders(
                    status, approvalReason, search, approvalRequired,
                    dealerId, createdBy, territoryId, fromDate, toDate, pageable);
        }
        return ApiResponse.success("Danh sách đơn hàng", orderPage.getContent(), orderPage.getTotalElements(), orderPage.getTotalPages());
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

    /**
     * S4-01: Tính giá và chiết khấu áp tự động khi thêm dòng hàng cho đại lý.
     * Giá lấy từ bảng giá đang hiệu lực của nhóm khách hàng mà đại lý thuộc về.
     * Chiết khấu theo sản lượng tính lại tự động.
     * Sửa giá thủ công được nhưng dưới giá sàn thì cảnh báo cần duyệt.
     * Không có bảng giá hiệu lực cho SKU đó thì chặn thêm dòng hàng kèm thông báo rõ lý do.
     */
    @PostMapping("/line-item/pricing")
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ApiResponse calculateLineItemPricing(@Valid @RequestBody OrderLineItemPricingRequest request) {
        OrderLineItemPricingResponse response = orderService.calculateLineItemPricing(request);
        return ApiResponse.success("Áp giá và chiết khấu dòng hàng thành công", response);
    }

    /**
     * S4-01: Tính lại toàn bộ giá và chiết khấu cho danh sách dòng hàng khi số lượng thay đổi hoặc sửa giá thủ công.
     * Chặn thêm dòng hàng nếu có SKU chưa có trong bảng giá hiệu lực.
     */
    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ApiResponse calculateOrder(@Valid @RequestBody OrderCalculateRequest request) {
        OrderCalculateResponse response = orderService.calculateOrder(request);
        return ApiResponse.success("Tính toán giá và chiết khấu đơn hàng thành công", response);
    }

    /**
     * S4-06: Huỷ đơn hàng - bắt buộc cung cấp lý do huỷ.
     * Không cho huỷ đơn đã xuất kho (EXPORTED) hoặc đã giao hàng (DELIVERED).
     */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN')")
    public ApiResponse cancelOrder(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        String cancelReason = reason;
        if ((cancelReason == null || cancelReason.isBlank()) && body != null) {
            cancelReason = body.getOrDefault("reason", body.getOrDefault("cancelReason", body.get("comment")));
        }
        OrderResponse response = (cancelReason != null && !cancelReason.isBlank())
                ? orderService.cancelOrder(id, cancelReason, username)
                : orderService.cancelOrder(id, username);
        String message = Boolean.TRUE.equals(response.getDealerLocked())
                ? "Hủy đơn hàng thành công (Cảnh báo: Đại lý đang bị khoá giao dịch)"
                : "Hủy đơn hàng thành công";
        return ApiResponse.success(message, response);
    }

    /**
     * S4-05: Cập nhật trạng thái duyệt đơn hàng qua PATCH (APPROVED, REJECTED, RETURNED).
     * Bắt buộc có ý kiến khi REJECTED hoặc RETURNED (400 Bad Request nếu thiếu/rỗng).
     * Đơn được duyệt mới chuyển sang giữ chỗ tồn kho (RECORDED) và chờ xuất kho.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ORDER_APPROVE') or hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        OrderResponse response = orderService.updateOrderStatus(id, request, username);
        String action = request.getStatus() != null ? request.getStatus().trim().toUpperCase() : "";
        String message;
        switch (action) {
            case "APPROVED":
                message = Boolean.TRUE.equals(response.getDealerLocked())
                        ? "Phê duyệt đơn hàng thành công (Cảnh báo: Đại lý đang bị khoá giao dịch). Đơn chuyển sang giữ chỗ tồn kho."
                        : "Phê duyệt đơn hàng thành công. Đơn chuyển sang giữ chỗ tồn kho.";
                break;
            case "REJECTED":
                message = Boolean.TRUE.equals(response.getDealerLocked())
                        ? "Từ chối đơn hàng thành công (Cảnh báo: Đại lý đang bị khoá giao dịch)"
                        : "Từ chối đơn hàng thành công";
                break;
            case "RETURNED":
                message = Boolean.TRUE.equals(response.getDealerLocked())
                        ? "Trả lại đơn hàng để chỉnh sửa thành công (Cảnh báo: Đại lý đang bị khoá giao dịch)"
                        : "Trả lại đơn hàng để chỉnh sửa thành công";
                break;
            default:
                message = "Cập nhật trạng thái đơn hàng thành công";
        }
        return ApiResponse.success(message, response);
    }

    /**
     * Quản lý kinh doanh phê duyệt đơn hàng bán dưới giá sàn / vượt hạn mức (S2-10, S4-05).
     * Đơn hàng của đại lý bị khoá vẫn được xử lý tiếp nhưng có cảnh báo (S3-07).
     * Đơn được duyệt mới chuyển sang giữ chỗ tồn kho và chờ xuất kho.
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ORDER_APPROVE') or hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse approveOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String comment,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        String approvalComment = comment;
        if (approvalComment == null && body != null) {
            approvalComment = body.getOrDefault("comment", body.get("reason"));
        }
        OrderResponse response = (approvalComment != null && !approvalComment.isBlank())
                ? orderService.approveOrder(id, approvalComment, username)
                : orderService.approveOrder(id, username);
        String message = Boolean.TRUE.equals(response.getDealerLocked())
                ? "Phê duyệt đơn hàng thành công (Cảnh báo: Đại lý đang bị khoá giao dịch)"
                : "Phê duyệt đơn hàng thành công";
        return ApiResponse.success(message, response);
    }

    /**
     * Quản lý kinh doanh từ chối đơn hàng (S2-10, S4-05).
     * Bắt buộc có lý do từ chối (400 Bad Request nếu thiếu hoặc để trống).
     * Đơn hàng của đại lý bị khoá vẫn được xử lý tiếp nhưng có cảnh báo (S3-07).
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('ORDER_APPROVE') or hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse rejectOrder(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        String rejectReason = reason;
        if ((rejectReason == null || rejectReason.isBlank()) && body != null) {
            rejectReason = body.getOrDefault("reason", body.get("comment"));
        }
        OrderResponse response = orderService.rejectOrder(id, rejectReason, username);
        String message = Boolean.TRUE.equals(response.getDealerLocked())
                ? "Từ chối đơn hàng thành công (Cảnh báo: Đại lý đang bị khoá giao dịch)"
                : "Từ chối đơn hàng thành công";
        return ApiResponse.success(message, response);
    }

    /**
     * Quản lý kinh doanh trả lại đơn hàng để nhân viên sửa lại (S4-05).
     * Bắt buộc có ý kiến/lý do trả lại (400 Bad Request nếu thiếu hoặc để trống).
     */
    @PutMapping("/{id}/return")
    @PreAuthorize("hasAuthority('ORDER_APPROVE') or hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse returnOrder(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        String returnReason = reason;
        if ((returnReason == null || returnReason.isBlank()) && body != null) {
            returnReason = body.getOrDefault("reason", body.get("comment"));
        }
        OrderResponse response = orderService.returnOrder(id, returnReason, username);
        String message = Boolean.TRUE.equals(response.getDealerLocked())
                ? "Trả lại đơn hàng để chỉnh sửa thành công (Cảnh báo: Đại lý đang bị khoá giao dịch)"
                : "Trả lại đơn hàng để chỉnh sửa thành công";
        return ApiResponse.success(message, response);
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('ORDER_APPROVE') or hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse returnOrderPost(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal) {
        return returnOrder(id, reason, body, principal);
    }

    /**
     * S4-05: Lấy lịch sử duyệt đơn hàng (bất biến, không thể sửa/xoá).
     */
    @GetMapping("/{id}/approval-history")
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ApiResponse getOrderApprovalHistory(@PathVariable Long id) {
        List<OrderApprovalHistoryResponse> history = orderService.getOrderApprovalHistory(id);
        return ApiResponse.success("Lịch sử duyệt đơn hàng", history);
    }

    /**
     * Kiểm tra sơ bộ hạn mức và điều kiện nợ khi đặt đơn cho đại lý (S4-02).
     */
    @PostMapping("/check-debt")
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ApiResponse checkOrderDebt(@Valid @RequestBody OrderDebtCheckRequest request) {
        OrderDebtCheckResponse response = dealerDebtService.checkOrderDebt(request);
        return ApiResponse.success("Kiểm tra công nợ thành công", response);
    }

    /**
     * Tra cứu nhanh hạn mức công nợ theo query param (S4-02).
     */
    @GetMapping("/check-debt")
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ApiResponse checkOrderDebtByParam(
            @RequestParam Long dealerId,
            @RequestParam(required = false, defaultValue = "0") BigDecimal orderAmount) {
        OrderDebtCheckResponse response = dealerDebtService.checkOrderDebt(dealerId, orderAmount);
        return ApiResponse.success("Kiểm tra công nợ thành công", response);
    }

    /**
     * S4-09: Sao chép đơn hàng cũ thành đơn nháp mới, áp lại giá/chiết khấu hiện hành.
     * Đại lý bị khoá thì không được sao chép.
     */
    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse> copyOrder(@PathVariable Long id, Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        OrderResponse response = orderService.copyOrder(id, username);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Sao chép đơn hàng thành công", response));
    }

    /**
     * S4-08: Tạo HTML để in / xuất PDF đơn hàng.
     * Trả về text/html để client mở tab in hoặc chuyển thành PDF.
     */
    @GetMapping(value = "/{id}/print", produces = "text/html;charset=UTF-8")
    @PreAuthorize("hasAnyRole('SALES', 'SALES_MANAGER', 'ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public org.springframework.http.ResponseEntity<String> printOrder(@PathVariable Long id) {
        String html = orderService.generateOrderPrintHtml(id);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Type", "text/html;charset=UTF-8")
                .body(html);
    }
}
