package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO cho đơn vị quy đổi của SKU (S2-07).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitConversionResponse {

    private Long id;
    private Long productId;
    private String productCode;
    private String productName;
    private String unitName;
    private BigDecimal conversionFactor;
    private String baseUnit;
    private Boolean isBaseUnit;
    private BigDecimal price;
    private BigDecimal costPrice;
    private String barcode;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
