package com.quanlybanhang.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Configuration does not disclose stock quantities to a MANAGE-only account. */
public record StockMinimumSettingResponse(Long warehouseId, String warehouseCode, String warehouseName,
        Long productId, String productCode, String productName, String categoryName, String baseUnit,
        BigDecimal minimumQuantity, String minimumUnit, boolean minimumUnitMatches,
        boolean warehouseActive, boolean productActive, String updatedBy, LocalDateTime updatedAt) {
    public static StockMinimumSettingResponse from(StockLedgerRow row) {
        return new StockMinimumSettingResponse(row.getWarehouseId(), row.getWarehouseCode(), row.getWarehouseName(),
                row.getProductId(), row.getProductCode(), row.getProductName(), row.getCategoryName(), row.getBaseUnit(),
                row.getMinimumQuantity(), row.getMinimumUnit(), Integer.valueOf(1).equals(row.getMinimumUnitMatchesFlag()),
                Boolean.TRUE.equals(row.getWarehouseActive()), Boolean.TRUE.equals(row.getProductActive()),
                row.getMinimumUpdatedBy(), row.getMinimumUpdatedAt());
    }
}
