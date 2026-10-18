package com.quanlybanhang.repository;

import com.quanlybanhang.model.WarehouseLocationProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WarehouseLocationProductRepository extends JpaRepository<WarehouseLocationProduct, Long> {

    List<WarehouseLocationProduct> findByLocationId(Long locationId);

    List<WarehouseLocationProduct> findByProductId(Long productId);

    @Query("SELECT wlp FROM WarehouseLocationProduct wlp WHERE wlp.location.warehouse.id = :warehouseId AND wlp.product.id = :productId ORDER BY wlp.isDefault DESC, wlp.id ASC")
    List<WarehouseLocationProduct> findByWarehouseIdAndProductId(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);

    Optional<WarehouseLocationProduct> findByLocationIdAndProductId(Long locationId, Long productId);

    boolean existsByLocationIdAndProductId(Long locationId, Long productId);

    void deleteByLocationIdAndProductId(Long locationId, Long productId);

    long countByLocationId(Long locationId);
}

