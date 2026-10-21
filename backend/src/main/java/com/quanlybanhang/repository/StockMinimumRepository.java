package com.quanlybanhang.repository;

import com.quanlybanhang.model.StockMinimum;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StockMinimumRepository extends JpaRepository<StockMinimum, Long> {
    Optional<StockMinimum> findByWarehouseIdAndProductId(Long warehouseId, Long productId);
}
