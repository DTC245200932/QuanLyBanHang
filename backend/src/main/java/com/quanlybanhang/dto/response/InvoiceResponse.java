package com.quanlybanhang.dto.response;

import com.quanlybanhang.model.InvoiceStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponse {
    private Long id;
    private String invoiceNumber;
    private String invoiceCode;
    private LocalDateTime invoiceDate;
    private LocalDate dueDate;
    private Boolean isPaid;
    private BigDecimal paidAmount;
    private InvoiceStatus status;
    private String statusLabel;

    private Long dealerId;
    private String dealerCode;
    private String dealerName;
    private String dealerTaxCode;
    private String dealerAddress;
    private String dealerPhone;

    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BigDecimal adjustedTotalAmount;

    private String note;
    private String createdBy;
    private LocalDateTime createdAt;

    private List<InvoiceItemResponse> items;
    private List<InvoiceOrderSummaryResponse> orders;
    private List<InvoiceAdjustmentResponse> adjustments;
}

