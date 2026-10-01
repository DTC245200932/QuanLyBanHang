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
public class OrderItemRequest {

    private Long productId;

    private String productCode;

    /**
     * Đơn vị đặt hàng (vd: "thùng", "lốc", "lon").
     */
    private String unit;

    @NotNull(message = "Số lượng đặt không được để trống")
    @DecimalMin(value = "0.0001", message = "Số lượng đặt phải lớn hơn 0")
    private BigDecimal quantity;

    private BigDecimal price;

    private String note;
}
