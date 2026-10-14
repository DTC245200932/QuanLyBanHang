package com.quanlybanhang.repository;

import com.quanlybanhang.model.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByCode(String code);

    List<Warehouse> findAllByActiveTrueOrderByNameAsc();
}
