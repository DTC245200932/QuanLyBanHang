package com.quanlybanhang.repository;

import com.quanlybanhang.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Product> findByCategoryId(Long categoryId);

    List<Product> findByCategoryIdIn(Collection<Long> categoryIds);

    long countByCategoryId(Long categoryId);

    long countByCategoryIdIn(Collection<Long> categoryIds);

    /**
     * Tính tổng doanh số của các sản phẩm thuộc danh sách nhóm hàng (ngành hàng / cây nhóm con).
     */
    @Query("SELECT SUM(p.revenue) FROM Product p WHERE p.category.id IN :categoryIds")
    BigDecimal sumRevenueByCategoryIds(@Param("categoryIds") Collection<Long> categoryIds);
}
