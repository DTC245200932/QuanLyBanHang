package com.quanlybanhang.repository;

import com.quanlybanhang.model.LocationType;
import com.quanlybanhang.model.WarehouseLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface WarehouseLocationRepository extends JpaRepository<WarehouseLocation, Long>, JpaSpecificationExecutor<WarehouseLocation> {

    List<WarehouseLocation> findByWarehouseIdOrderByCodeAsc(Long warehouseId);

    List<WarehouseLocation> findByWarehouseIdAndActiveTrueOrderByCodeAsc(Long warehouseId);

    List<WarehouseLocation> findByWarehouseIdAndLocationTypeOrderByCodeAsc(Long warehouseId, LocationType locationType);

    Optional<WarehouseLocation> findByWarehouseIdAndCode(Long warehouseId, String code);

    boolean existsByWarehouseIdAndCode(Long warehouseId, String code);

    boolean existsByWarehouseIdAndCodeAndIdNot(Long warehouseId, String code, Long id);

    List<WarehouseLocation> findByParentIdOrderByCodeAsc(Long parentId);

    long countByParentId(Long parentId);

    long countByWarehouseId(Long warehouseId);
}

