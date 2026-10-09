package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumeDiscountCalculateItemRequest {

    private Long productId;

    private String productCode;

    private String unit;

    @NotNull(message = "Số lượng không được để trống")
    @DecimalMin(value = "0.0001", message = "Số lượng phải lớn hơn 0")
    private BigDecimal quantity;

    private BigDecimal price;
}

