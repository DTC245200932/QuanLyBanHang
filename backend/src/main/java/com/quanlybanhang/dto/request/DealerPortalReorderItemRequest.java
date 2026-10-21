package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Dòng hàng được chọn đặt lại (S5-02).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerPortalReorderItemRequest {
    private Long productId;
    private String productCode;
    private String unit;

    @DecimalMin(value = "0.0001", message = "Số lượng đặt lại phải lớn hơn 0")
    private BigDecimal quantity;

    private String note;
}
