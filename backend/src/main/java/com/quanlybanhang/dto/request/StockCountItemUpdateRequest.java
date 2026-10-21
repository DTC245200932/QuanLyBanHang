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
public class StockCountItemUpdateRequest {

    @NotNull(message = "ID sản phẩm không được để trống")
    private Long productId;

    @NotNull(message = "Số lượng đếm thực tế không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Số lượng đếm thực tế phải lớn hơn hoặc bằng 0")
    private BigDecimal actualQuantity;

    private String note;
}
