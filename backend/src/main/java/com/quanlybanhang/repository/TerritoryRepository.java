package com.quanlybanhang.repository;

import com.quanlybanhang.model.Territory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TerritoryRepository extends JpaRepository<Territory, Long> {
    List<Territory> findAllByActiveTrueOrderByNameAsc();
}
