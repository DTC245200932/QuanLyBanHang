package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Một dòng hàng đã được server áp giá và tính chiết khấu cho cổng đại lý.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealerPortalOrderPreviewItemResponse {

    private Long productId;
    private String productCode;
    private String productName;
    private String categoryName;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountPercent;
    private BigDecimal totalAmount;
}
