package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.DebtLimitRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.model.Dealer;
import com.quanlybanhang.repository.DealerRepository;
import com.quanlybanhang.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
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
@RequiredArgsConstructor
public class FinanceController {

    private final DealerRepository dealerRepository;
    private final AuditLogService auditLogService;

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
        List<Map<String, Object>> debts = dealerRepository.findAll().stream()
                .map(dealer -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("dealerId", dealer.getId());
                    item.put("customerName", dealer.getName());
                    item.put("debtAmount", BigDecimal.ZERO);
                    item.put("debtLimit", normalizeDebtLimit(dealer.getDebtLimit()));
                    return item;
                })
                .toList();
        return ApiResponse.success("Dữ liệu công nợ", debts);
    }

    /**
     * Cập nhật hạn mức công nợ và ghi lại giá trị trước/sau vào nhật ký.
     * Cùng một transaction đảm bảo không có trường hợp cập nhật thành công nhưng thiếu audit log.
     */
    @PutMapping("/debt/{dealerId}/limit")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Transactional
    public ApiResponse updateDebtLimit(
            @PathVariable Long dealerId,
            @Valid @RequestBody DebtLimitRequest request,
            Authentication authentication) {
        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đại lý"));

        BigDecimal oldLimit = normalizeDebtLimit(dealer.getDebtLimit());
        BigDecimal newLimit = request.getDebtLimit();

        if (oldLimit.compareTo(newLimit) != 0) {
            dealer.setDebtLimit(newLimit);
            dealerRepository.save(dealer);
            auditLogService.logAction(
                    "DEBT_LIMIT",
                    dealerId.toString(),
                    "UPDATE",
                    oldLimit.toPlainString(),
                    newLimit.toPlainString(),
                    authentication.getName());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dealerId", dealer.getId());
        result.put("customerName", dealer.getName());
        result.put("debtLimit", newLimit);
        return ApiResponse.success(
                oldLimit.compareTo(newLimit) == 0
                        ? "Hạn mức công nợ không thay đổi"
                        : "Đã cập nhật hạn mức công nợ",
                result);
    }

    private BigDecimal normalizeDebtLimit(BigDecimal debtLimit) {
        return debtLimit == null ? BigDecimal.ZERO : debtLimit;
    }
}
