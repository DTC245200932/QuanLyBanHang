package com.quanlybanhang.repository;

import com.quanlybanhang.model.WarehouseVoucherItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.Collection;

@Repository
public interface WarehouseVoucherItemRepository extends JpaRepository<WarehouseVoucherItem, Long> {

    List<WarehouseVoucherItem> findByVoucherId(Long voucherId);

    List<WarehouseVoucherItem> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    @Query("SELECT DISTINCT i.product.id FROM WarehouseVoucherItem i WHERE i.product.id IN :productIds")
    Set<Long> findProductIdsWithTransactions(@Param("productIds") Collection<Long> productIds);
}
