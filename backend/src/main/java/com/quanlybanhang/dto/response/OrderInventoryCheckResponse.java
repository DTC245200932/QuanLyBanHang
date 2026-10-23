package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Response kiểm tra tồn khả dụng và gợi ý số lượng tối đa còn đặt được (S4-03).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderInventoryCheckResponse {

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;

    private Long productId;
    private String productCode;
    private String productName;

    private String unit;
    private BigDecimal quantity;
    private BigDecimal conversionFactor;
    private String baseUnit;
    private BigDecimal baseQuantity;

    /** Tồn thực tế trong kho (đơn vị cơ sở). */
    private BigDecimal physicalQuantity;

    /** Tồn đang giữ chỗ cho các đơn hàng khác (đơn vị cơ sở). */
    private BigDecimal reservedQuantity;

    /** Tồn khả dụng = Tồn thực tế - Tồn giữ chỗ (đơn vị cơ sở). */
    private BigDecimal availableQuantity;

    /** Tồn khả dụng quy đổi theo đơn vị đặt hàng (làm tròn xuống). */
    private BigDecimal availableInUnit;

    /** Số lượng tối đa còn đặt được theo đơn vị đặt hàng. */
    private BigDecimal maxAllowedInUnit;

    /** Số lượng yêu cầu có hợp lệ (<= tồn khả dụng) hay không. */
    private Boolean valid;

    /** Thông báo trạng thái hoặc gợi ý số lượng tối đa. */
    private String message;
}
