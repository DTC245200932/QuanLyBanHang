package com.quanlybanhang.repository;

import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.model.DiscountTargetType;
import com.quanlybanhang.model.VolumeDiscountPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VolumeDiscountPolicyRepository extends JpaRepository<VolumeDiscountPolicy, Long> {

    Optional<VolumeDiscountPolicy> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<VolumeDiscountPolicy> findByActiveTrue();

    List<VolumeDiscountPolicy> findByProductId(Long productId);

    List<VolumeDiscountPolicy> findByCategoryId(Long categoryId);

    /**
     * Tìm tất cả chính sách đang hoạt động và có hiệu lực tại ngày chỉ định.
     */
    @Query("SELECT p FROM VolumeDiscountPolicy p WHERE p.active = true " +
           "AND (p.startDate IS NULL OR p.startDate <= :date) " +
           "AND (p.endDate IS NULL OR p.endDate >= :date)")
    List<VolumeDiscountPolicy> findActivePoliciesByDate(@Param("date") LocalDate date);
}

