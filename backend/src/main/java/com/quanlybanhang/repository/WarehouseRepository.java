package com.quanlybanhang.repository;

import com.quanlybanhang.model.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    List<Warehouse> findAllByActiveTrueOrderByNameAsc();
}
