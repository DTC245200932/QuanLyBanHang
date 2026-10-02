package com.quanlybanhang.repository;

import com.quanlybanhang.model.PriceListItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceListItemRepository extends JpaRepository<PriceListItem, Long> {

    List<PriceListItem> findByPriceListId(Long priceListId);

    Optional<PriceListItem> findByPriceListIdAndProductId(Long priceListId, Long productId);

    List<PriceListItem> findByProductId(Long productId);

    void deleteByPriceListId(Long priceListId);
}
