package com.quanlybanhang.repository;

import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.model.PriceList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceListRepository extends JpaRepository<PriceList, Long> {

    Optional<PriceList> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<PriceList> findByCustomerGroup(CustomerGroup customerGroup);

    List<PriceList> findByCustomerGroupAndActiveTrue(CustomerGroup customerGroup);

    List<PriceList> findAllByOrderByCreatedAtDesc();

    /**
     * Tìm các bảng giá đang có hiệu lực theo nhóm khách hàng tại một thời điểm.
     * Sắp xếp theo phiên bản mới nhất (version giảm dần) và id giảm dần.
     */
    @Query("SELECT p FROM PriceList p WHERE p.customerGroup = :customerGroup " +
            "AND p.active = true " +
            "AND p.startDate <= :date AND p.endDate >= :date " +
            "ORDER BY p.version DESC, p.id DESC")
    List<PriceList> findEffectivePriceLists(@Param("customerGroup") CustomerGroup customerGroup,
                                           @Param("date") LocalDate date);
}
