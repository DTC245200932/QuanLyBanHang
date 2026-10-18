package com.quanlybanhang.repository;

import com.quanlybanhang.model.OrderApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderApprovalHistoryRepository extends JpaRepository<OrderApprovalHistory, Long> {
    List<OrderApprovalHistory> findByOrderIdOrderByCreatedAtAsc(Long orderId);
    List<OrderApprovalHistory> findByOrderIdOrderByCreatedAtDesc(Long orderId);
    List<OrderApprovalHistory> findByOrderCodeOrderByCreatedAtAsc(String orderCode);
}
