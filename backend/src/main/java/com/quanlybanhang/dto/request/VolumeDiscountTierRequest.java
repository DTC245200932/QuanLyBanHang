package com.quanlybanhang.dto.request;

import com.quanlybanhang.model.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumeDiscountTierRequest {

    private Long id;

    @NotNull(message = "Số lượng mua tối thiểu không được để trống")
    @DecimalMin(value = "0.0001", message = "Số lượng mua tối thiểu phải lớn hơn 0")
    private BigDecimal minQuantity;

    private BigDecimal maxQuantity;

    @NotNull(message = "Loại chiết khấu không được để trống (PERCENTAGE hoặc FIXED_AMOUNT)")
    private DiscountType discountType;

    @NotNull(message = "Giá trị chiết khấu không được để trống")
    @DecimalMin(value = "0.01", message = "Giá trị chiết khấu phải lớn hơn 0")
    private BigDecimal discountValue;

    private Integer tierOrder;
}

