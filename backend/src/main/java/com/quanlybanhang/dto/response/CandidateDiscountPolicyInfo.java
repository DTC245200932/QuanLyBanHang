package com.quanlybanhang.dto.response;

import com.quanlybanhang.model.DiscountTargetType;
import com.quanlybanhang.model.DiscountType;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateDiscountPolicyInfo {

    private Long policyId;
    private String policyCode;
    private String policyName;
    private DiscountTargetType targetType;
    private Long matchedTierId;
    private BigDecimal minQuantity;
    private BigDecimal maxQuantity;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal calculatedDiscountAmount;
    private BigDecimal calculatedDiscountPercent;
    private Boolean isSelected;
}

