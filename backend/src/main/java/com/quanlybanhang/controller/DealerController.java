package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.DealerRequest;
import com.quanlybanhang.dto.request.LockDealerRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.DealerOptionsResponse;
import com.quanlybanhang.dto.response.DealerResponse;
import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.service.DealerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Controller quản lý hồ sơ đại lý (S3-03 & S3-07).
 *
 * Phân quyền:
 * - Xem danh sách, chi tiết, options, đơn đang dở: ACCOUNTANT, SALES_MANAGER, ADMIN, SALES, VIEWER
 * - Thêm, sửa, đổi trạng thái, xoá, khoá/mở giao dịch: ACCOUNTANT, SALES_MANAGER, ADMIN
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
            @RequestParam(required = false) Boolean locked,
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
                locked,
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

    /**
     * Khoá giao dịch với một đại lý (S3-07).
     * Bắt buộc có lý do khoá. Cảnh báo nếu có đơn hàng đang dở dang.
     */
    @RequestMapping(value = "/{id}/lock", method = {RequestMethod.POST, RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'SALES_MANAGER', 'ADMIN')")
    public ApiResponse lockDealer(
            @PathVariable Long id,
            @Valid @RequestBody LockDealerRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        DealerResponse dealer = dealerService.lockDealer(id, request.getReason(), username);
        String message = dealer.getInProgressOrderCount() != null && dealer.getInProgressOrderCount() > 0
                ? String.format("Đã khoá giao dịch với đại lý '%s'. Cảnh báo: Đại lý hiện có %d đơn hàng đang dở dang cần theo dõi xử lý.",
                        dealer.getName(), dealer.getInProgressOrderCount())
                : String.format("Đã khoá giao dịch với đại lý '%s' thành công", dealer.getName());
        return ApiResponse.success(message, dealer);
    }

    /**
     * Mở khoá giao dịch với một đại lý (S3-07).
     */
    @RequestMapping(value = "/{id}/unlock", method = {RequestMethod.POST, RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'SALES_MANAGER', 'ADMIN')")
    public ApiResponse unlockDealer(
            @PathVariable Long id,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        DealerResponse dealer = dealerService.unlockDealer(id, username);
        return ApiResponse.success(String.format("Đã mở khoá giao dịch cho đại lý '%s'", dealer.getName()), dealer);
    }

    /**
     * Xem danh sách các đơn hàng đang dở dang của một đại lý (S3-07).
     */
    @GetMapping("/{id}/in-progress-orders")
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'SALES_MANAGER', 'ADMIN', 'SALES', 'VIEWER')")
    public ApiResponse getInProgressOrders(@PathVariable Long id) {
        List<com.quanlybanhang.dto.response.OrderResponse> orders = dealerService.getInProgressOrders(id);
        return ApiResponse.success("Lấy danh sách đơn hàng đang dở dang của đại lý thành công", orders);
    }
}

