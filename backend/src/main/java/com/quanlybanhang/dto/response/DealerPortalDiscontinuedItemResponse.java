package com.quanlybanhang.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Chi tiết mặt hàng ngừng kinh doanh hoặc không còn trong bảng giá hiệu lực bị loại bỏ khi đặt lại (S5-02).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerPortalDiscontinuedItemResponse {
    private Long sourceItemId;
    private Long productId;
    private String productCode;
    private String productName;
    private String unit;
    private BigDecimal quantity;
    private String reason;
}
