package com.quanlybanhang.repository;

import com.quanlybanhang.model.UnitConversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitConversionRepository extends JpaRepository<UnitConversion, Long> {

    List<UnitConversion> findByProductIdOrderByConversionFactorAsc(Long productId);

    Optional<UnitConversion> findByProductIdAndUnitNameIgnoreCase(Long productId, String unitName);

    boolean existsByProductIdAndUnitNameIgnoreCase(Long productId, String unitName);

    boolean existsByProductIdAndUnitNameIgnoreCaseAndIdNot(Long productId, String unitName, Long id);

    void deleteByProductId(Long productId);
}
