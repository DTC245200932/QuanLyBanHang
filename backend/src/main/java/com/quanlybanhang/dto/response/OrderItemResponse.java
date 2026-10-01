package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {

    private Long id;
    private Long productId;
    private String productCode;
    private String productName;

    /**
     * Đơn vị đặt hàng (vd: "thùng").
     */
    private String unit;

    /**
     * Số lượng theo đơn vị đặt hàng (vd: 2).
     */
    private BigDecimal quantity;

    /**
     * Hệ số quy đổi đóng băng tại thời điểm ghi sổ (vd: 24.0).
     */
    private BigDecimal conversionFactor;

    /**
     * Đơn vị cơ sở đóng băng tại thời điểm ghi sổ (vd: "lon").
     */
    private String baseUnit;

    /**
     * Số lượng đã quy về đơn vị cơ sở khi ghi sổ (vd: 48 lon = 2 * 24.0).
     */
    private BigDecimal baseQuantity;

    private BigDecimal price;
    private BigDecimal totalAmount;
    private String note;
}
