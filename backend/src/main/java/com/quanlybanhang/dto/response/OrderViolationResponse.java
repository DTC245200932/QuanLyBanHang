package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Thể hiện mức vi phạm và lý do cần duyệt đơn hàng (S4-05).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderViolationResponse {

    /**
     * OVER_DEBT_LIMIT, BELOW_FLOOR_PRICE, OVER_QUANTITY_LIMIT
     */
    private String type;

    private String title;
    private String description;
    private String displayValue;
    private BigDecimal value;
}
