package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountItemResponse {
    private Long id;
    private Long productId;
    private String productCode;
    private String productName;
    private String categoryName;
    private String baseUnit;
    private BigDecimal bookQuantity;
    private BigDecimal actualQuantity;
    private BigDecimal differenceQuantity;
    private String note;
}
