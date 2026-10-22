package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.VolumeDiscountCalculateRequest;
import com.quanlybanhang.dto.request.VolumeDiscountPolicyRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.VolumeDiscountCalculationResponse;
import com.quanlybanhang.dto.response.VolumeDiscountPolicyResponse;
import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.model.DiscountTargetType;
import com.quanlybanhang.service.VolumeDiscountPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Controller Quản lý Chính sách chiết khấu theo sản lượng (S3-01).
 * Cho phép Quản lý kinh doanh khai báo chính sách chiết khấu theo sản lượng,
 * để khuyến khích đại lý lấy nhiều hàng mà không phải thoả thuận miệng từng lần.
 */
@RestController
@RequestMapping("/api/volume-discounts")
@RequiredArgsConstructor
public class VolumeDiscountController {

    private final VolumeDiscountPolicyService volumeDiscountPolicyService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or (hasAnyAuthority('PRODUCT_VIEW', 'PRODUCT_EDIT', 'ORDER_CREATE'))")
    public ApiResponse getAllPolicies(
            @RequestParam(required = false) DiscountTargetType targetType,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) CustomerGroup customerGroup,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword) {
        List<VolumeDiscountPolicyResponse> list = volumeDiscountPolicyService.getAllPolicies(
                targetType, productId, categoryId, customerGroup, active, keyword);
        return ApiResponse.success("Danh sách chính sách chiết khấu theo sản lượng", list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAnyAuthority('PRODUCT_VIEW', 'PRODUCT_EDIT', 'ORDER_CREATE'))")
    public ApiResponse getPolicyById(@PathVariable Long id) {
        VolumeDiscountPolicyResponse policy = volumeDiscountPolicyService.getPolicyById(id);
        return ApiResponse.success("Chi tiết chính sách chiết khấu theo sản lượng", policy);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('PRODUCT_EDIT'))")
    public ResponseEntity<ApiResponse> createPolicy(
            @Valid @RequestBody VolumeDiscountPolicyRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        VolumeDiscountPolicyResponse response = volumeDiscountPolicyService.createPolicy(request, username);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo chính sách chiết khấu theo sản lượng thành công", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('PRODUCT_EDIT'))")
    public ApiResponse updatePolicy(
            @PathVariable Long id,
            @Valid @RequestBody VolumeDiscountPolicyRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        VolumeDiscountPolicyResponse response = volumeDiscountPolicyService.updatePolicy(id, request, username);
        return ApiResponse.success("Cập nhật chính sách chiết khấu theo sản lượng thành công", response);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('PRODUCT_EDIT'))")
    public ApiResponse togglePolicyStatus(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean active,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        VolumeDiscountPolicyResponse response = volumeDiscountPolicyService.toggleActive(id, active, username);
        return ApiResponse.success("Cập nhật trạng thái chính sách chiết khấu thành công", response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('PRODUCT_EDIT'))")
    public ApiResponse deletePolicy(@PathVariable Long id, Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        volumeDiscountPolicyService.deletePolicy(id, username);
        return ApiResponse.success("Xóa chính sách chiết khấu theo sản lượng thành công", null);
    }

    @PostMapping("/calculate")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('ORDER_CREATE'))")
    public ApiResponse calculateDiscounts(@Valid @RequestBody VolumeDiscountCalculateRequest request) {
        VolumeDiscountCalculationResponse response = volumeDiscountPolicyService.calculateDiscounts(request);
        return ApiResponse.success("Kết quả tính toán chiết khấu sản lượng", response);
    }
}

