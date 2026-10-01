package com.quanlybanhang.controller;

import com.quanlybanhang.dto.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controller Tài chính.
 *
 * Quy tắc nghiệp vụ cốt lõi:
 * - Giá vốn (/api/finance/cost-price) và biên lợi nhuận (/api/finance/profit-margin)
 *   CHỈ DUY NHẤT vai trò Quản lý kinh doanh (ROLE_SALES_MANAGER) được phép truy cập.
 * - Thủ kho, nhân viên KD, kế toán, admin đều bị từ chối ở tầng server.
 * - Công nợ (/api/finance/debt): dành cho ADMIN, SALES_MANAGER, ACCOUNTANT.
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    /**
     * API xem giá vốn — CHỈ Quản lý kinh doanh.
     */
    @GetMapping("/cost-price")
    @PreAuthorize("hasRole('SALES_MANAGER')")
    public ApiResponse getCostPrice() {
        List<Map<String, Object>> costPrices = List.of(
                Map.of("productCode", "SP001", "productName", "Sản phẩm A", "costPrice", 120000),
                Map.of("productCode", "SP002", "productName", "Sản phẩm B", "costPrice", 250000)
        );
        return ApiResponse.success("Dữ liệu giá vốn sản phẩm", costPrices);
    }

    /**
     * API xem biên lợi nhuận — CHỈ Quản lý kinh doanh.
     */
    @GetMapping("/profit-margin")
    @PreAuthorize("hasRole('SALES_MANAGER')")
    public ApiResponse getProfitMargin() {
        List<Map<String, Object>> margins = List.of(
                Map.of("productCode", "SP001", "sellingPrice", 180000, "costPrice", 120000, "marginPercent", 33.33),
                Map.of("productCode", "SP002", "sellingPrice", 350000, "costPrice", 250000, "marginPercent", 28.57)
        );
        return ApiResponse.success("Dữ liệu biên lợi nhuận", margins);
    }

    /**
     * API xem công nợ — ADMIN, SALES_MANAGER, ACCOUNTANT.
     */
    @GetMapping("/debt")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'ACCOUNTANT')")
    public ApiResponse getDebt() {
        List<Map<String, Object>> debts = List.of(
                Map.of("customerCode", "KH001", "customerName", "Đại lý Hà Nội", "debtAmount", 15000000)
        );
        return ApiResponse.success("Dữ liệu công nợ", debts);
    }
}
