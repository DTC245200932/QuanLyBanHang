package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO cho Product.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private String code;
    private String name;
    private String description;

    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private Integer categoryLevel;

    private BigDecimal price;
    private BigDecimal revenue;
    private String unit;
    private Boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
