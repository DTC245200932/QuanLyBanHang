package com.quanlybanhang.repository;

import com.quanlybanhang.model.WarehouseStockCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WarehouseStockCountRepository extends JpaRepository<WarehouseStockCount, Long>, JpaSpecificationExecutor<WarehouseStockCount> {
    Optional<WarehouseStockCount> findByCode(String code);
    boolean existsByCode(String code);
}
