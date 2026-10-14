package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumeDiscountItemCalculationResponse {

    private Long productId;
    private String productCode;
    private String productName;
    private Long categoryId;
    private String categoryName;

    private String unit;
    private BigDecimal quantity;
    private BigDecimal conversionFactor;
    private String baseUnit;
    private BigDecimal baseQuantity;

    private BigDecimal price;
    private BigDecimal priceListSellingPrice;
    private BigDecimal floorPrice;
    private Boolean belowFloorPrice;
    private BigDecimal subtotalAmount;

    // Chiết khấu được áp dụng
    private BigDecimal discountAmount;
    private BigDecimal discountPercent;
    private BigDecimal totalAmount;

    private Long appliedPolicyId;
    private String appliedPolicyCode;
    private String appliedPolicyName;
    private String selectionReason;

    @Builder.Default
    private List<CandidateDiscountPolicyInfo> candidatePolicies = new ArrayList<>();
}

