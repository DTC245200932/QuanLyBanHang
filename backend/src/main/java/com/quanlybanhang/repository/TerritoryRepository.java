package com.quanlybanhang.repository;

import com.quanlybanhang.model.Territory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TerritoryRepository extends JpaRepository<Territory, Long> {
    Optional<Territory> findByCode(String code);

    List<Territory> findAllByActiveTrueOrderByNameAsc();
}
