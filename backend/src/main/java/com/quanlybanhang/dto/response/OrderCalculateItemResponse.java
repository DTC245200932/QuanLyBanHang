package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Thông tin giá và chiết khấu đã tính cho một dòng trong đơn hàng (S4-01).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCalculateItemResponse {

    private Long productId;
    private String productCode;
    private String productName;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal conversionFactor;
    private String baseUnit;
    private BigDecimal baseQuantity;

    /** Giá niêm yết theo bảng giá hiệu lực. */
    private BigDecimal priceListSellingPrice;

    /** Giá sàn theo bảng giá hiệu lực. */
    private BigDecimal floorPrice;

    /** Đơn giá được áp dụng (giá niêm yết hoặc giá sửa thủ công). */
    private BigDecimal price;

    /** Có phải giá do người dùng sửa thủ công hay không. */
    private Boolean isManualPrice;

    /** Đơn giá có dưới giá sàn hay không. */
    private Boolean belowFloorPrice;

    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountPercent;
    private Long appliedPolicyId;
    private String appliedPolicyCode;
    private String appliedPolicyName;
    private String selectionReason;
    private BigDecimal totalAmount;
}

