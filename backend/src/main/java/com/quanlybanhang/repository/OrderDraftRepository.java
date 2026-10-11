package com.quanlybanhang.repository;

import com.quanlybanhang.model.OrderDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderDraftRepository extends JpaRepository<OrderDraft, Long> {

    Optional<OrderDraft> findByUsername(String username);
}
