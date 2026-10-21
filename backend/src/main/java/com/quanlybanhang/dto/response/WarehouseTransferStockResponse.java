package com.quanlybanhang.dto.response;

import java.math.BigDecimal;

/** Narrow transfer lookup: no prices, margins, orders or financial data. */
public record WarehouseTransferStockResponse(Long productId, String productCode, String productName,
        String unit, BigDecimal physicalQuantity, BigDecimal reservedQuantity, BigDecimal availableQuantity) {}
