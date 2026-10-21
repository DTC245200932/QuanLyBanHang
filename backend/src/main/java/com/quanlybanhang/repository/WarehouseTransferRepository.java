package com.quanlybanhang.repository;

import com.quanlybanhang.model.WarehouseTransfer;
import com.quanlybanhang.model.WarehouseTransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseTransferRepository extends JpaRepository<WarehouseTransfer, Long>, JpaSpecificationExecutor<WarehouseTransfer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from WarehouseTransfer t where t.id = :id")
    Optional<WarehouseTransfer> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<WarehouseTransfer> findByTransferCode(String transferCode);
    boolean existsByTransferCode(String transferCode);
    List<WarehouseTransfer> findAllByOrderByCreatedAtDesc();
    List<WarehouseTransfer> findByFromWarehouseIdOrderByCreatedAtDesc(Long fromWarehouseId);
    List<WarehouseTransfer> findByToWarehouseIdOrderByCreatedAtDesc(Long toWarehouseId);
    List<WarehouseTransfer> findByStatusOrderByCreatedAtDesc(WarehouseTransferStatus status);
}
