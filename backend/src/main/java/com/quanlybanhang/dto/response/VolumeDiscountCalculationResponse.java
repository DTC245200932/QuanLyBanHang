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
public class VolumeDiscountCalculationResponse {

    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;

    @Builder.Default
    private List<VolumeDiscountItemCalculationResponse> items = new ArrayList<>();
}

