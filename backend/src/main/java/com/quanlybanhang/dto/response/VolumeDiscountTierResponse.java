package com.quanlybanhang.dto.response;

import com.quanlybanhang.model.DiscountType;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumeDiscountTierResponse {

    private Long id;
    private BigDecimal minQuantity;
    private BigDecimal maxQuantity;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private Integer tierOrder;
    private String description;
}

