package com.quanlybanhang.repository;

import com.quanlybanhang.model.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long>, JpaSpecificationExecutor<Warehouse> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select w from Warehouse w where w.id = :id")
    Optional<Warehouse> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Warehouse> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Warehouse> findAllByActiveTrueOrderByNameAsc();

    List<Warehouse> findAllByOrderByCodeAsc();
}
