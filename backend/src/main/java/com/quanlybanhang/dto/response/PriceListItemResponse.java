package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceListItemResponse {

    private Long id;
    private Long productId;
    private String productCode;
    private String productName;
    private String unit;
    private BigDecimal sellingPrice;
    private BigDecimal floorPrice;
    private String note;
}
