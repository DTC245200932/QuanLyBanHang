package com.quanlybanhang.repository;

import com.quanlybanhang.dto.response.StockLedgerRow;
import com.quanlybanhang.model.WarehouseInventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;

/** Read-only, paginated warehouse/SKU view, including SKUs with no stock row yet. */
public interface StockLedgerRepository extends Repository<WarehouseInventory, Long> {
    String FROM_FILTERS = """
        FROM warehouses w CROSS JOIN products p
        LEFT JOIN warehouse_inventories i ON i.warehouse_id = w.id AND i.product_id = p.id
        LEFT JOIN categories c ON c.id = p.category_id
        WHERE w.id IN (:warehouseIds)
          AND (:warehouseId IS NULL OR w.id = :warehouseId)
          AND (:categoryId IS NULL OR p.category_id = :categoryId)
          AND (:keyword IS NULL OR LOWER(p.code) LIKE :keyword ESCAPE '!' OR LOWER(p.name) LIKE :keyword ESCAPE '!')
          AND (:stockStatus IS NULL
            OR (:stockStatus = 'IN_STOCK' AND COALESCE(i.base_quantity,0) > 0
                AND COALESCE(i.base_quantity,0) > COALESCE(i.reserved_quantity,0))
            OR (:stockStatus = 'OUT_OF_STOCK' AND COALESCE(i.base_quantity,0) <= 0)
            OR (:stockStatus = 'FULLY_RESERVED' AND COALESCE(i.base_quantity,0) > 0
                AND COALESCE(i.base_quantity,0) <= COALESCE(i.reserved_quantity,0)))
        """;
    @Query(value = """
        SELECT w.id AS warehouseId, w.code AS warehouseCode, w.name AS warehouseName,
               p.id AS productId, p.code AS productCode, p.name AS productName,
               c.id AS categoryId, c.name AS categoryName, COALESCE(i.base_unit,p.unit) AS baseUnit,
               COALESCE(i.base_quantity,0) AS physicalQuantity,
               COALESCE(i.reserved_quantity,0) AS reservedQuantity,
               CASE WHEN COALESCE(i.base_quantity,0) > COALESCE(i.reserved_quantity,0)
                    THEN COALESCE(i.base_quantity,0) - COALESCE(i.reserved_quantity,0) ELSE 0 END AS availableQuantity,
               CASE WHEN COALESCE(i.base_quantity,0) <= 0 THEN 'OUT_OF_STOCK'
                    WHEN COALESCE(i.base_quantity,0) <= COALESCE(i.reserved_quantity,0) THEN 'FULLY_RESERVED'
                    ELSE 'IN_STOCK' END AS stockStatus, i.updated_at AS updatedAt
        """ + FROM_FILTERS + " ORDER BY w.code, p.code, p.id",
        countQuery = "SELECT COUNT(*) " + FROM_FILTERS, nativeQuery = true)
    Page<StockLedgerRow> findLedger(@Param("warehouseIds") List<Long> warehouseIds,
            @Param("warehouseId") Long warehouseId, @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword, @Param("stockStatus") String stockStatus, Pageable pageable);
}
