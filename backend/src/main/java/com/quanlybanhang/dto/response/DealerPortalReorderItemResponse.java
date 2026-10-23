package com.quanlybanhang.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Chi tiết dòng hàng có thể đặt lại (S5-02).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerPortalReorderItemResponse {
    private Long sourceItemId;
    private Long productId;
    private String productCode;
    private String productName;
    private String unit;
    private String baseUnit;
    private BigDecimal conversionFactor;
    private BigDecimal quantity;
    private BigDecimal oldPrice;
    private BigDecimal currentPrice;
    private BigDecimal currentFloorPrice;
    private BigDecimal subtotalAmount;

    @Builder.Default
    private List<DealerPortalCatalogItemResponse.PackagingUnitInfo> units = new ArrayList<>();
}
