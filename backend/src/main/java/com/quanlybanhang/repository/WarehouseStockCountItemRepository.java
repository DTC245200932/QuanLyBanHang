package com.quanlybanhang.repository;

import com.quanlybanhang.model.WarehouseStockCountItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WarehouseStockCountItemRepository extends JpaRepository<WarehouseStockCountItem, Long> {
    List<WarehouseStockCountItem> findByStockCountId(Long stockCountId);
}
