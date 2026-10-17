package com.quanlybanhang.repository;

import com.quanlybanhang.model.WarehouseInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseInventoryRepository extends JpaRepository<WarehouseInventory, Long> {

    Optional<WarehouseInventory> findByWarehouseIdAndProductId(Long warehouseId, Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT wi FROM WarehouseInventory wi WHERE wi.warehouse.id = :warehouseId AND wi.product.id = :productId")
    Optional<WarehouseInventory> findByWarehouseIdAndProductIdForUpdate(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT wi FROM WarehouseInventory wi WHERE wi.warehouse.id = :warehouseId AND wi.product.id IN :productIds")
    List<WarehouseInventory> findByWarehouseIdAndProductIdInForUpdate(@Param("warehouseId") Long warehouseId, @Param("productIds") Collection<Long> productIds);

    List<WarehouseInventory> findByWarehouseIdAndProductIdIn(Long warehouseId, Collection<Long> productIds);

    List<WarehouseInventory> findByWarehouseId(Long warehouseId);

    List<WarehouseInventory> findByProductId(Long productId);

    List<WarehouseInventory> findAllByOrderByProduct_CodeAsc();
}
