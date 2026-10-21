package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * Request kiểm tra nhanh tồn khả dụng trước khi đặt hàng (S4-03).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderInventoryCheckRequest {

    private Long warehouseId;

    private Long dealerId;

    @NotNull(message = "ID sản phẩm không được để trống")
    private Long productId;

    private String unit;

    @NotNull(message = "Số lượng đặt không được để trống")
    @DecimalMin(value = "0.0001", message = "Số lượng đặt phải lớn hơn 0")
    private BigDecimal quantity;
}
