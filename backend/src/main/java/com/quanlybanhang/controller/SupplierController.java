package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.SupplierRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.SupplierResponse;
import com.quanlybanhang.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or (hasAnyAuthority('WAREHOUSE_VIEW', 'WAREHOUSE_MANAGE'))")
    public ApiResponse getSuppliers(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("Trang phải không âm; số dòng mỗi trang phải từ 1 đến 200");
        }
        Page<SupplierResponse> suppliers = supplierService.getAllSuppliers(keyword, PageRequest.of(page, size));
        return ApiResponse.success("Lấy danh sách nhà cung cấp thành công", suppliers);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAnyAuthority('WAREHOUSE_VIEW', 'WAREHOUSE_MANAGE'))")
    public ApiResponse getSupplierById(@PathVariable Long id) {
        SupplierResponse supplier = supplierService.getSupplierById(id);
        return ApiResponse.success("Lấy chi tiết nhà cung cấp thành công", supplier);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_MANAGE'))")
    public ApiResponse createSupplier(@Valid @RequestBody SupplierRequest request) {
        SupplierResponse supplier = supplierService.createSupplier(request);
        return ApiResponse.success("Tạo nhà cung cấp thành công", supplier);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_MANAGE'))")
    public ApiResponse updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request) {
        SupplierResponse supplier = supplierService.updateSupplier(id, request);
        return ApiResponse.success("Cập nhật nhà cung cấp thành công", supplier);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_MANAGE'))")
    public ApiResponse updateStatus(@PathVariable Long id, @RequestParam boolean active) {
        return ApiResponse.success(active ? "Đã khôi phục giao dịch" : "Đã ngừng giao dịch",
                supplierService.updateStatus(id, active));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_MANAGE'))")
    public ApiResponse deleteSupplier(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ApiResponse.success("Xóa nhà cung cấp thành công (nếu không có giao dịch)", null);
    }
}
