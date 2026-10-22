package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response kết quả ghi nhận thu tiền (S7-04).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtPaymentResponse {
    private Long id;
    private String paymentCode;
    private Long dealerId;
    private String dealerCode;
    private String dealerName;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String paymentMethod;
    private String referenceCode;
    private String note;
    private String recordedBy;
    private LocalDateTime createdAt;

    /** Công nợ còn lại của đại lý sau khi trừ khoản thu */
    private BigDecimal remainingDebt;

    /** Trạng thái cờ rủi ro của đại lý sau khi đánh giá lại */
    private Boolean dealerStillFlagged;

    /** Lý do cờ rủi ro sau khi đánh giá lại (nếu còn) */
    private String debtFlagReason;
}

