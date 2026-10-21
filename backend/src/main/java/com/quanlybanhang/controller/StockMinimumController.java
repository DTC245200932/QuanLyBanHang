package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.StockMinimumRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/warehouse") @RequiredArgsConstructor
public class StockMinimumController {
    private final StockMinimumService minimums;
    private final StockLedgerService ledger;

    @GetMapping("/stock-minimums/options")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('WAREHOUSE_MANAGE')")
    public ApiResponse options() { return ApiResponse.success("Bộ lọc mức tồn tối thiểu", minimums.options()); }

    @GetMapping("/stock-minimums")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('WAREHOUSE_MANAGE')")
    public ApiResponse settings(@RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Khai báo mức tồn tối thiểu", minimums.search(warehouseId, categoryId, keyword, page, size));
    }

    @PutMapping("/stock-minimums/{warehouseId}/{productId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('WAREHOUSE_MANAGE')")
    public ApiResponse save(@PathVariable Long warehouseId, @PathVariable Long productId, @Valid @RequestBody StockMinimumRequest request) {
        return ApiResponse.success("Đã lưu mức tồn tối thiểu", minimums.save(warehouseId, productId, request));
    }

    @DeleteMapping("/stock-minimums/{warehouseId}/{productId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('WAREHOUSE_MANAGE')")
    public ApiResponse clear(@PathVariable Long warehouseId, @PathVariable Long productId) {
        minimums.clear(warehouseId, productId);
        return ApiResponse.success("Đã bỏ khai báo mức tồn tối thiểu", null);
    }

    @GetMapping("/stock-alerts")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('WAREHOUSE_VIEW')")
    public ApiResponse alerts(@RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.success("Hàng dưới mức tồn tối thiểu", ledger.searchBelowMinimum(warehouseId, categoryId, keyword, null, page, size));
    }
}
