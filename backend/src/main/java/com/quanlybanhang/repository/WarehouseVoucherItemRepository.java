package com.quanlybanhang.repository;

import com.quanlybanhang.model.WarehouseVoucherItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WarehouseVoucherItemRepository extends JpaRepository<WarehouseVoucherItem, Long> {

    List<WarehouseVoucherItem> findByVoucherId(Long voucherId);

    List<WarehouseVoucherItem> findByProductId(Long productId);
}
