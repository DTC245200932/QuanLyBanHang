package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.AssignDealerRequest;
import com.quanlybanhang.dto.request.BulkHandoverRequest;
import com.quanlybanhang.dto.request.DealerRequest;
import com.quanlybanhang.dto.response.*;
import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.service.DealerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller quản lý hồ sơ đại lý (S3-03) và phân công/chuyển giao địa bàn (S3-06).
 *
 * Phân quyền:
 * - Xem danh sách, chi tiết, options: ACCOUNTANT, SALES_MANAGER, ADMIN, SALES, VIEWER
 *   (Lưu ý: SALES chỉ nhìn thấy đại lý do mình phụ trách)
 * - Thêm, sửa, đổi trạng thái, xóa: ACCOUNTANT, SALES_MANAGER, ADMIN
 * - Phân công phụ trách, bàn giao hàng loạt: SALES_MANAGER, ADMIN
 * - Tra cứu lịch sử bàn giao: SALES_MANAGER, ADMIN, ACCOUNTANT, VIEWER
 */
@RestController
@RequestMapping("/api/dealers")
@RequiredArgsConstructor
public class DealerController {

    private final DealerService dealerService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'SALES_MANAGER', 'ADMIN', 'SALES', 'VIEWER')")
    public ApiResponse getAllDealers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CustomerGroup customerGroup,
            @RequestParam(required = false) Long territoryId,
            @RequestParam(required = false) Long assignedUserId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size) {

        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("Trang phải không âm; số dòng mỗi trang phải từ 1 đến 200");
        }

        Page<DealerResponse> dealers = dealerService.getAllDealers(
                keyword,
                customerGroup,
                territoryId,
                assignedUserId,
                active,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));

        return ApiResponse.success("Lấy danh sách đại lý thành công", dealers);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'SALES_MANAGER', 'ADMIN', 'SALES', 'VIEWER')")
    public ApiResponse getActiveDealers() {
        List<DealerResponse> dealers = dealerService.getActiveDealers();
        return ApiResponse.success("Lấy danh sách đại lý đang hoạt động thành công", dealers);
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'SALES_MANAGER', 'ADMIN', 'SALES', 'VIEWER')")
    public ApiResponse getDealerOptions() {
        DealerOptionsResponse options = dealerService.getDealerOptions();
        return ApiResponse.success("Lấy danh mục tham chiếu đại lý thành công", options);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'SALES_MANAGER', 'ADMIN', 'SALES', 'VIEWER')")
    public ApiResponse getDealerById(@PathVariable Long id) {
        DealerResponse dealer = dealerService.getDealerById(id);
        return ApiResponse.success("Lấy chi tiết đại lý thành công", dealer);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'SALES_MANAGER', 'ADMIN')")
    public ApiResponse createDealer(
            @Valid @RequestBody DealerRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        DealerResponse dealer = dealerService.createDealer(request, username);
        return ApiResponse.success("Tạo hồ sơ đại lý thành công", dealer);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'SALES_MANAGER', 'ADMIN')")
    public ApiResponse updateDealer(
            @PathVariable Long id,
            @Valid @RequestBody DealerRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        DealerResponse dealer = dealerService.updateDealer(id, request, username);
        return ApiResponse.success("Cập nhật hồ sơ đại lý thành công", dealer);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'SALES_MANAGER', 'ADMIN')")
    public ApiResponse updateStatus(
            @PathVariable Long id,
            @RequestParam boolean active,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        DealerResponse dealer = dealerService.updateStatus(id, active, username);
        return ApiResponse.success(
                active ? "Đã khôi phục giao dịch cho đại lý" : "Đã ngừng giao dịch cho đại lý",
                dealer);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'SALES_MANAGER', 'ADMIN')")
    public ApiResponse deleteDealer(
            @PathVariable Long id,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        dealerService.deleteDealer(id, username);
        return ApiResponse.success("Xóa hồ sơ đại lý thành công (nếu chưa có giao dịch)", null);
    }

    // ── Phân công & Chuyển giao địa bàn (S3-06) ──

    /**
     * Gán hoặc thay đổi một nhân viên phụ trách chính cho một đại lý (S3-06).
     */
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse assignDealer(
            @PathVariable Long id,
            @Valid @RequestBody AssignDealerRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        DealerResponse response = dealerService.assignDealer(id, request, username);
        return ApiResponse.success("Phân công nhân viên phụ trách đại lý thành công", response);
    }

    /**
     * Chuyển giao địa bàn / đại lý hàng loạt khi nhân viên nghỉ việc hoặc điều chuyển (S3-06).
     */
    @PostMapping("/handover/bulk")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse bulkHandover(
            @Valid @RequestBody BulkHandoverRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        BulkHandoverResponse response = dealerService.bulkHandover(request, username);
        return ApiResponse.success("Chuyển giao địa bàn hàng loạt thành công", response);
    }

    /**
     * Lấy danh sách các đại lý đang cần bàn giao (nhân viên bị khoá hoặc có cờ handoverRequired) (S3-06).
     */
    @GetMapping("/handover/pending")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse getPendingHandoverDealers() {
        List<DealerResponse> pendingDealers = dealerService.getPendingHandoverDealers();
        return ApiResponse.success("Lấy danh sách đại lý cần bàn giao thành công", pendingDealers);
    }

    /**
     * Tra cứu lịch sử chuyển giao địa bàn và đại lý (S3-06).
     */
    @GetMapping("/handover/history")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN', 'ACCOUNTANT', 'VIEWER')")
    public ApiResponse getHandoverHistory(
            @RequestParam(required = false) Long fromUserId,
            @RequestParam(required = false) Long toUserId,
            @RequestParam(required = false) Long dealerId,
            @RequestParam(required = false) Long territoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size) {

        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("Trang phải không âm; số dòng mỗi trang phải từ 1 đến 200");
        }

        Page<DealerAssignmentHistoryResponse> historyPage = dealerService.getHandoverHistory(
                fromUserId,
                toUserId,
                dealerId,
                territoryId,
                startDate,
                endDate,
                PageRequest.of(page, size));

        return ApiResponse.success("Lấy lịch sử chuyển giao đại lý thành công", historyPage);
    }

    /**
     * Lấy lịch sử phân công/chuyển giao của riêng một đại lý cụ thể (S3-06).
     */
    @GetMapping("/{id}/assignment-history")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN', 'ACCOUNTANT', 'SALES', 'VIEWER')")
    public ApiResponse getDealerAssignmentHistory(@PathVariable Long id) {
        List<DealerAssignmentHistoryResponse> history = dealerService.getDealerAssignmentHistory(id);
        return ApiResponse.success("Lấy lịch sử phân công của đại lý thành công", history);
    }
}
