package com.quanlybanhang.repository;

import com.quanlybanhang.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    List<Order> findAllByOrderByCreatedAtDesc();

    boolean existsByPriceListId(Long priceListId);

    boolean existsByDealerId(Long dealerId);

    List<Order> findByStatus(String status);
}
