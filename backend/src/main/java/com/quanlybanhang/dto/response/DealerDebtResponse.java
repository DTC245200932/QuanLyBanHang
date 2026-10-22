package com.quanlybanhang.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Thông tin công nợ và hạn mức của đại lý (S4-02).
 * Phục vụ hiển thị trên màn hình tạo đơn và kiểm soát rủi ro bán hàng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerDebtResponse {

    private Long dealerId;
    private String dealerCode;
    private String dealerName;

    /** Công nợ hiện tại (tổng tiền các đơn hàng ghi sổ chưa thanh toán). */
    private BigDecimal currentDebt;

    /** Hạn mức công nợ tối đa được cấp (S3-05). */
    private BigDecimal debtLimit;

    /** Phần hạn mức còn lại (debtLimit - currentDebt). */
    private BigDecimal remainingLimit;

    /** Số ngày nợ tối đa cho phép (S3-05). */
    private Integer maxDebtDays;

    /** Có khoản nợ nào quá số ngày cho phép hay không. */
    private Boolean hasOverdueDebt;

    /** Tổng số tiền nợ bị quá hạn. */
    private BigDecimal overdueAmount;

    /** Số ngày quá hạn lớn nhất (nếu có đơn nợ quá hạn). */
    private Integer maxOverdueDays;

    /** Mã đơn hàng nợ quá hạn lâu nhất. */
    private String oldestOverdueOrderCode;

    /** Ngày phát sinh đơn hàng nợ quá hạn lâu nhất. */
    private LocalDate oldestOverdueOrderDate;

    /** Trạng thái bị chặn tạo đơn hoàn toàn (do nợ quá hạn hoặc bị khoá giao dịch). */
    private Boolean isBlocked;

    /** Lý do chặn tạo đơn (nếu isBlocked = true). */
    private String blockReason;

    private BigDecimal totalPaid;
    private Boolean isDebtFlagged;
    private com.quanlybanhang.model.DebtFlagType debtFlagType;
    private String debtFlagReason;
    private java.time.LocalDateTime debtFlaggedAt;
    private java.time.LocalDateTime lastDebtEvaluatedAt;
    private com.quanlybanhang.model.DebtFlagAction effectiveAction;
}

