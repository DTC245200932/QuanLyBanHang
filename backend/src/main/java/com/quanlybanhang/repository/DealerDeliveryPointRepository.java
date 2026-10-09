package com.quanlybanhang.repository;

import com.quanlybanhang.model.DealerDeliveryPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DealerDeliveryPointRepository extends JpaRepository<DealerDeliveryPoint, Long> {
    List<DealerDeliveryPoint> findByDealerId(Long dealerId);
    Optional<DealerDeliveryPoint> findByDealerIdAndIsDefaultTrue(Long dealerId);
}
