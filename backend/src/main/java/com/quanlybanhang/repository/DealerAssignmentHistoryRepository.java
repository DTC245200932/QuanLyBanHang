package com.quanlybanhang.repository;

import com.quanlybanhang.model.DealerAssignmentHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DealerAssignmentHistoryRepository extends JpaRepository<DealerAssignmentHistory, Long> {

    List<DealerAssignmentHistory> findByDealerIdOrderByTransferredAtDesc(Long dealerId);

    Page<DealerAssignmentHistory> findByDealerIdOrderByTransferredAtDesc(Long dealerId, Pageable pageable);

    @Query("SELECT h FROM DealerAssignmentHistory h WHERE " +
           "(:fromUserId IS NULL OR h.fromUser.id = :fromUserId) AND " +
           "(:toUserId IS NULL OR h.toUser.id = :toUserId) AND " +
           "(:dealerId IS NULL OR h.dealer.id = :dealerId) AND " +
           "(:territoryId IS NULL OR h.territory.id = :territoryId) AND " +
           "(:startDate IS NULL OR h.transferredAt >= :startDate) AND " +
           "(:endDate IS NULL OR h.transferredAt <= :endDate) " +
           "ORDER BY h.transferredAt DESC")
    Page<DealerAssignmentHistory> searchHistory(
            @Param("fromUserId") Long fromUserId,
            @Param("toUserId") Long toUserId,
            @Param("dealerId") Long dealerId,
            @Param("territoryId") Long territoryId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}

