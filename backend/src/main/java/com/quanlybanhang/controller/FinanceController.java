package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.DealerDebtActionConfigRequest;
import com.quanlybanhang.dto.request.DebtLimitRequest;
import com.quanlybanhang.dto.request.DebtPaymentRequest;
import com.quanlybanhang.dto.request.DebtRiskConfigRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.DebtPaymentResponse;
import com.quanlybanhang.model.Dealer;
import com.quanlybanhang.repository.DealerRepository;
import com.quanlybanhang.repository.ProductRepository;
import com.quanlybanhang.service.AuditLogService;
import com.quanlybanhang.service.DealerDebtService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controller Tài chính & Kiểm soát rủi ro công nợ (S3-05 & S7-04).
 *
 * Quy tắc nghiệp vụ cốt lõi:
 * - Giá vốn (/api/finance/cost-price) và biên lợi nhuận (/api/finance/profit-margin)
 *   dành cho Quản lý kinh doanh và Quản trị viên (FINANCE_VIEW_COST_PRICE / FINANCE_VIEW_PROFIT_MARGIN).
 * - Công nợ (/api/finance/debt): FINANCE_VIEW_DEBT.
 * - Quản lý công nợ, cấu hình rủi ro, ghi nhận thu tiền: FINANCE_MANAGE.
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final DealerRepository dealerRepository;
    private final AuditLogService auditLogService;
    private final ProductRepository productRepository;
    private final DealerDebtService dealerDebtService;

    public FinanceController(DealerRepository dealerRepository,
                             AuditLogService auditLogService,
                             ProductRepository productRepository) {
        this(dealerRepository, auditLogService, productRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FinanceController(DealerRepository dealerRepository,
                             AuditLogService auditLogService,
                             ProductRepository productRepository,
                             DealerDebtService dealerDebtService) {
        this.dealerRepository = dealerRepository;
        this.auditLogService = auditLogService;
        this.productRepository = productRepository;
        this.dealerDebtService = dealerDebtService;
    }

    /**
     * API xem giá vốn — Quản lý kinh doanh hoặc Quản trị viên.
     */
    @GetMapping("/cost-price")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('FINANCE_VIEW_COST_PRICE'))")
    public ApiResponse getCostPrice() {
        List<Map<String, Object>> costPrices = productRepository.findAll().stream()
                .map(product -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productId", product.getId());
                    item.put("productCode", product.getCode());
                    item.put("productName", product.getName());
                    item.put("unit", product.getUnit());
                    item.put("costPrice", product.getCostPrice());
                    return item;
                }).toList();
        return ApiResponse.success("Dữ liệu giá vốn sản phẩm", costPrices);
    }

    /**
     * API xem biên lợi nhuận — Quản lý kinh doanh hoặc Quản trị viên.
     */
    @GetMapping("/profit-margin")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('FINANCE_VIEW_PROFIT_MARGIN'))")
    public ApiResponse getProfitMargin() {
        List<Map<String, Object>> margins = productRepository.findAll().stream()
                .map(product -> {
                    BigDecimal sellingPrice = product.getPrice();
                    BigDecimal costPrice = product.getCostPrice();
                    BigDecimal margin = sellingPrice != null && sellingPrice.signum() > 0 && costPrice != null
                            ? sellingPrice.subtract(costPrice).multiply(new BigDecimal("100"))
                                    .divide(sellingPrice, 2, RoundingMode.HALF_UP)
                            : null;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productId", product.getId());
                    item.put("productCode", product.getCode());
                    item.put("productName", product.getName());
                    item.put("unit", product.getUnit());
                    item.put("sellingPrice", sellingPrice);
                    item.put("costPrice", costPrice);
                    item.put("marginPercent", margin);
                    return item;
                }).toList();
        return ApiResponse.success("Dữ liệu biên lợi nhuận", margins);
    }

    /**
     * API xem công nợ — ADMIN, SALES_MANAGER, ACCOUNTANT.
     */
    @GetMapping("/debt")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('FINANCE_VIEW_DEBT'))")
    public ApiResponse getDebt() {
        List<Map<String, Object>> debts = dealerRepository.findAll().stream()
                .map(dealer -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("dealerId", dealer.getId());
                    item.put("customerName", dealer.getName());
                    item.put("debtAmount", null);
                    item.put("debtAmountAvailable", false);
                    item.put("debtLimit", normalizeDebtLimit(dealer.getDebtLimit()));
                    item.put("maxDebtDays", dealer.getMaxDebtDays() != null ? dealer.getMaxDebtDays() : 0);
                    item.put("debtFlagged", Boolean.TRUE.equals(dealer.getDebtFlagged()));
                    item.put("debtFlagType", dealer.getDebtFlagType() != null ? dealer.getDebtFlagType().name() : "NONE");
                    item.put("debtFlagReason", dealer.getDebtFlagReason());
                    return item;
                })
                .toList();
        return ApiResponse.success("Hạn mức công nợ; chưa có dữ liệu số dư công nợ", debts);
    }

    /**
     * Cập nhật hạn mức công nợ và số ngày nợ tối đa (S3-05).
     * Bắt buộc nhập lý do thay đổi và tự động ghi nhật ký giá trị trước/sau kèm lý do.
     * Chỉ Kế toán công nợ và Quản lý kinh doanh (và Quản trị hệ thống) được phép sửa.
     */
    @PutMapping("/debt/{dealerId}/limit")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('FINANCE_MANAGE'))")
    @Transactional
    public ApiResponse updateDebtLimit(
            @PathVariable Long dealerId,
            @Valid @RequestBody DebtLimitRequest request,
            Authentication authentication) {
        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đại lý"));

        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Lý do thay đổi hạn mức không được để trống");
        }

        BigDecimal oldLimit = normalizeDebtLimit(dealer.getDebtLimit());
        Integer oldDays = dealer.getMaxDebtDays() != null ? dealer.getMaxDebtDays() : 0;

        BigDecimal newLimit = request.getDebtLimit();
        Integer newDays = request.getMaxDebtDays() != null ? request.getMaxDebtDays() : oldDays;
        String reason = request.getReason().trim();

        boolean limitChanged = oldLimit.compareTo(newLimit) != 0;
        boolean daysChanged = request.getMaxDebtDays() != null && !Objects.equals(oldDays, newDays);

        if (limitChanged || daysChanged) {
            dealer.setDebtLimit(newLimit);
            dealer.setMaxDebtDays(newDays);
            dealerRepository.save(dealer);

            String oldValueStr;
            String newValueStr;
            if (limitChanged && daysChanged) {
                oldValueStr = oldLimit.toPlainString() + " (" + oldDays + " ngày)";
                newValueStr = newLimit.toPlainString() + " (" + newDays + " ngày)";
            } else if (limitChanged) {
                oldValueStr = oldLimit.toPlainString();
                newValueStr = newLimit.toPlainString();
            } else {
                oldValueStr = oldDays + " ngày";
                newValueStr = newDays + " ngày";
            }

            auditLogService.logAction(
                    "DEBT_LIMIT",
                    dealerId.toString(),
                    "UPDATE",
                    oldValueStr,
                    newValueStr,
                    reason,
                    authentication != null ? authentication.getName() : "system");

            // Đánh giá lại công nợ sau khi thay đổi hạn mức (S7-04)
            if (dealerDebtService != null) {
                try {
                    dealerDebtService.evaluateDealerDebt(dealerId);
                } catch (Exception ignored) {
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dealerId", dealer.getId());
        result.put("customerName", dealer.getName());
        result.put("debtLimit", newLimit);
        result.put("maxDebtDays", newDays);
        result.put("reason", reason);
        return ApiResponse.success(
                limitChanged || daysChanged
                        ? "Đã cập nhật hạn mức công nợ"
                        : "Hạn mức công nợ không thay đổi",
                result);
    }

    // ──────────────────────── S7-04: CẤU HÌNH KIỂM SOÁT RỦI RO CÔNG NỢ ────────────────────────

    /**
     * Xem cấu hình kiểm soát rủi ro công nợ toàn hệ thống (S7-04).
     */
    @GetMapping("/debt/config")
    @PreAuthorize("hasRole('ADMIN') or (hasAnyAuthority('FINANCE_VIEW_DEBT', 'FINANCE_MANAGE'))")
    public ApiResponse getDebtRiskConfig() {
        if (dealerDebtService == null) {
            throw new IllegalStateException("DealerDebtService chưa được khởi tạo");
        }
        return ApiResponse.success("Cấu hình kiểm soát rủi ro công nợ", dealerDebtService.getDebtRiskConfig());
    }

    /**
     * Cập nhật cấu hình kiểm soát rủi ro công nợ toàn hệ thống (S7-04).
     */
    @PutMapping("/debt/config")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('FINANCE_MANAGE'))")
    public ApiResponse updateDebtRiskConfig(@Valid @RequestBody DebtRiskConfigRequest request, Authentication authentication) {
        if (dealerDebtService == null) {
            throw new IllegalStateException("DealerDebtService chưa được khởi tạo");
        }
        String username = authentication != null ? authentication.getName() : "system";
        return ApiResponse.success("Đã cập nhật cấu hình kiểm soát rủi ro công nợ",
                dealerDebtService.updateDebtRiskConfig(request, username));
    }

    /**
     * Cấu hình hành vi riêng cho một đại lý khi bị gắn cờ rủi ro (S7-04).
     */
    @PutMapping("/debt/{dealerId}/action-config")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('FINANCE_MANAGE'))")
    public ApiResponse updateDealerActionConfig(
            @PathVariable Long dealerId,
            @Valid @RequestBody DealerDebtActionConfigRequest request,
            Authentication authentication) {
        if (dealerDebtService == null) {
            throw new IllegalStateException("DealerDebtService chưa được khởi tạo");
        }
        String username = authentication != null ? authentication.getName() : "system";
        return ApiResponse.success("Đã cập nhật cấu hình hành vi đại lý",
                dealerDebtService.updateDealerActionConfig(dealerId, request.getAction(), username));
    }

    /**
     * Danh sách các đại lý đang bị gắn cờ cảnh báo rủi ro công nợ (S7-04).
     */
    @GetMapping("/debt/flagged-dealers")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('FINANCE_VIEW_DEBT'))")
    public ApiResponse getFlaggedDealers() {
        if (dealerDebtService == null) {
            throw new IllegalStateException("DealerDebtService chưa được khởi tạo");
        }
        return ApiResponse.success("Danh sách đại lý bị gắn cờ rủi ro công nợ",
                dealerDebtService.getFlaggedDealers());
    }

    /**
     * Tác vụ đánh giá lại công nợ toàn bộ đại lý (S7-04).
     */
    @PostMapping("/debt/evaluate")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('FINANCE_MANAGE'))")
    public ApiResponse evaluateAllDealersDebt() {
        if (dealerDebtService == null) {
            throw new IllegalStateException("DealerDebtService chưa được khởi tạo");
        }
        return ApiResponse.success("Đã thực hiện tác vụ đánh giá lại công nợ toàn bộ đại lý",
                dealerDebtService.evaluateAllDealersDebt());
    }

    /**
     * Đánh giá lại công nợ của một đại lý cụ thể (S7-04).
     */
    @PostMapping("/debt/{dealerId}/evaluate")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('FINANCE_MANAGE'))")
    public ApiResponse evaluateDealerDebt(@PathVariable Long dealerId) {
        if (dealerDebtService == null) {
            throw new IllegalStateException("DealerDebtService chưa được khởi tạo");
        }
        return ApiResponse.success("Đã đánh giá lại công nợ đại lý",
                dealerDebtService.evaluateDealerDebt(dealerId));
    }

    // ──────────────────────── S7-04: GHI NHẬN THU TIỀN ────────────────────────

    /**
     * Ghi nhận thu tiền / thanh toán công nợ của đại lý (S7-04).
     * Hệ thống tự động lưu phiếu thu và chạy lại tác vụ đánh giá lại công nợ đại lý ngay lập tức.
     */
    @PostMapping("/debt-payments")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('FINANCE_MANAGE'))")
    public ResponseEntity<ApiResponse> recordDebtPayment(
            @Valid @RequestBody DebtPaymentRequest request,
            Authentication authentication) {
        if (dealerDebtService == null) {
            throw new IllegalStateException("DealerDebtService chưa được khởi tạo");
        }
        String username = authentication != null ? authentication.getName() : "system";
        DebtPaymentResponse response = dealerDebtService.recordPayment(request, username);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ghi nhận thu tiền thành công và đã đánh giá lại công nợ", response));
    }

    /**
     * Danh sách lịch sử các lần thu tiền (S7-04).
     */
    @GetMapping("/debt-payments")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('FINANCE_VIEW_DEBT'))")
    public ApiResponse getDebtPayments(
            @RequestParam(required = false) Long dealerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (dealerDebtService == null) {
            throw new IllegalStateException("DealerDebtService chưa được khởi tạo");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paymentDate", "createdAt"));
        return ApiResponse.success("Danh sách phiếu thu tiền công nợ",
                dealerDebtService.getPayments(dealerId, pageable));
    }

    private BigDecimal normalizeDebtLimit(BigDecimal debtLimit) {
        return debtLimit == null ? BigDecimal.ZERO : debtLimit;
    }
}
