package com.quanlybanhang.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SQL projection: deliberately excludes prices and financial data. */
public interface StockLedgerRow {
    Long getWarehouseId();
    String getWarehouseCode();
    String getWarehouseName();
    Long getProductId();
    String getProductCode();
    String getProductName();
    Long getCategoryId();
    String getCategoryName();
    String getBaseUnit();
    BigDecimal getPhysicalQuantity();
    BigDecimal getReservedQuantity();
    BigDecimal getAvailableQuantity();
    String getStockStatus();
    LocalDateTime getUpdatedAt();
    BigDecimal getMinimumQuantity();
    String getMinimumUnit();
    Integer getMinimumUnitMatchesFlag();
    Integer getBelowMinimumFlag();
    String getMinimumUpdatedBy();
    LocalDateTime getMinimumUpdatedAt();
    Boolean getWarehouseActive();
    Boolean getProductActive();
}
