package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO xem tồn kho (S2-07).
 * Hiển thị số lượng thực tế theo đơn vị cơ sở (số lon)
 * cùng thông tin quy đổi tương đương theo các đơn vị khác (thùng, lốc...).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseInventoryResponse {

    private Long id;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;

    private Long productId;
    private String productCode;
    private String productName;

    /**
     * Số lượng tồn ghi sổ theo đơn vị cơ sở (vd: 240 lon).
     */
    private BigDecimal baseQuantity;

    /**
     * Tên đơn vị cơ sở (vd: "lon").
     */
    private String baseUnit;

    /**
     * Số lượng đang giữ chỗ cho các đơn hàng khác (S4-03).
     */
    private BigDecimal reservedQuantity;

    /**
     * Tồn khả dụng = Tồn thực tế - Tồn đang giữ chỗ (S4-03).
     */
    private BigDecimal availableQuantity;

    /**
     * Quy đổi tương đương theo các đơn vị tính khác (vd: "thùng": 10.0, "lốc": 40.0).
     */
    private Map<String, BigDecimal> convertedViews;

    /**
     * Quy đổi tương đương tồn khả dụng theo các đơn vị tính khác (vd: "thùng": 7.0, "lốc": 28.0) (S4-03).
     */
    private Map<String, BigDecimal> convertedAvailableViews;

    private LocalDateTime updatedAt;
}
