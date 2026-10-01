package com.quanlybanhang.repository;

import com.quanlybanhang.model.WarehouseVoucher;
import com.quanlybanhang.model.WarehouseVoucherType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseVoucherRepository extends JpaRepository<WarehouseVoucher, Long> {

    Optional<WarehouseVoucher> findByVoucherCode(String voucherCode);

    boolean existsByVoucherCode(String voucherCode);

    List<WarehouseVoucher> findByWarehouseIdOrderByRecordedAtDesc(Long warehouseId);

    List<WarehouseVoucher> findByTypeOrderByRecordedAtDesc(WarehouseVoucherType type);

    List<WarehouseVoucher> findAllByOrderByRecordedAtDesc();
}
