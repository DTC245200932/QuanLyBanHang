package com.quanlybanhang.controller;

import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.model.StockStatus;
import com.quanlybanhang.service.StockLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/warehouse/stock-ledger")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasAuthority('WAREHOUSE_VIEW')")
public class StockLedgerController {
    private final StockLedgerService service;

    @GetMapping("/options")
    public ApiResponse options() { return ApiResponse.success("Bộ lọc sổ tồn kho", service.options()); }

    @GetMapping
    public ApiResponse search(@RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) StockStatus stockStatus,
            @RequestParam(defaultValue = "false") boolean belowMinimum,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Sổ tồn theo SKU và kho", belowMinimum
                ? service.searchBelowMinimum(warehouseId, categoryId, keyword, stockStatus, page, size)
                : service.search(warehouseId, categoryId, keyword, stockStatus, page, size));
    }
}
