package com.quanlybanhang.repository;

import com.quanlybanhang.model.Dealer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DealerRepository extends JpaRepository<Dealer, Long> {
    List<Dealer> findByAssignedUserId(Long userId);
    long countByAssignedUserId(Long userId);
}
