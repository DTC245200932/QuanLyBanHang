package com.quanlybanhang.dto.response;

import com.quanlybanhang.model.StockStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockLedgerEntryResponse(Long warehouseId, String warehouseCode, String warehouseName,
        Long productId, String productCode, String productName, Long categoryId, String categoryName,
        String baseUnit, BigDecimal physicalQuantity, BigDecimal reservedQuantity, BigDecimal availableQuantity,
        StockStatus stockStatus, String stockStatusLabel, LocalDateTime updatedAt) {
    public static StockLedgerEntryResponse from(StockLedgerRow row) {
        StockStatus status = StockStatus.valueOf(row.getStockStatus());
        return new StockLedgerEntryResponse(row.getWarehouseId(), row.getWarehouseCode(), row.getWarehouseName(),
                row.getProductId(), row.getProductCode(), row.getProductName(), row.getCategoryId(), row.getCategoryName(),
                row.getBaseUnit(), row.getPhysicalQuantity(), row.getReservedQuantity(), row.getAvailableQuantity(),
                status, status.getLabel(), row.getUpdatedAt());
    }
}
