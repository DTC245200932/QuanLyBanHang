package com.quanlybanhang.repository;

import com.quanlybanhang.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    /**
     * Lấy danh sách các nhóm hàng gốc (Cấp 1 - Ngành hàng).
     */
    List<Category> findByParentIsNullOrderByNameAsc();

    /**
     * Lấy danh sách nhóm con trực tiếp của một nhóm cha.
     */
    List<Category> findByParentIdOrderByNameAsc(Long parentId);

    /**
     * Lấy toàn bộ nhóm hàng sắp xếp theo cấp độ và tên.
     */
    List<Category> findAllByOrderByLevelAscNameAsc();

    /**
     * Đếm số lượng nhóm con trực tiếp.
     */
    long countByParentId(Long parentId);
}
