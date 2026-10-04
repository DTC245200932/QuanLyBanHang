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
public class PriceListItemRequest {

    private Long productId;

    private String productCode;

    @NotNull(message = "Giá bán không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá bán phải lớn hơn hoặc bằng 0")
    private BigDecimal sellingPrice;

    @NotNull(message = "Giá sàn không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá sàn phải lớn hơn hoặc bằng 0")
    private BigDecimal floorPrice;

    private String note;
}
