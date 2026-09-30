package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.CategoryRequest;
import com.quanlybanhang.dto.request.MoveProductsRequest;
import com.quanlybanhang.dto.response.CategoryResponse;
import com.quanlybanhang.exception.CategoryNotEmptyException;
import com.quanlybanhang.model.Category;
import com.quanlybanhang.model.Product;
import com.quanlybanhang.repository.CategoryRepository;
import com.quanlybanhang.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service quản lý nhóm hàng nhiều cấp (S2-06).
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Mã nhóm hàng '" + request.getCode() + "' đã tồn tại");
        }

        Category parent = null;
        int level = 1;

        if (request.getParentId() != null) {
            parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng cha với ID: " + request.getParentId()));
            level = parent.getLevel() + 1;
        }

        Category category = Category.builder()
                .code(request.getCode().trim().toUpperCase())
                .name(request.getName().trim())
                .description(request.getDescription())
                .parent(parent)
                .level(level)
                .active(true)
                .build();

        Category saved = categoryRepository.save(category);
        return mapToResponse(saved, false);
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng với ID: " + id));

        if (categoryRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new IllegalArgumentException("Mã nhóm hàng '" + request.getCode() + "' đã được sử dụng");
        }

        category.setCode(request.getCode().trim().toUpperCase());
        category.setName(request.getName().trim());
        category.setDescription(request.getDescription());

        Long currentParentId = category.getParent() != null ? category.getParent().getId() : null;
        Long newParentId = request.getParentId();

        if (!Objects.equals(currentParentId, newParentId)) {
            if (newParentId == null) {
                category.setParent(null);
                category.setLevel(1);
            } else {
                if (newParentId.equals(id)) {
                    throw new IllegalArgumentException("Không thể chọn chính nhóm hàng này làm nhóm cha");
                }
                Category newParent = categoryRepository.findById(newParentId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng cha với ID: " + newParentId));

                // Kiểm tra newParent không được là con cháu của category (tránh vòng lặp chu trình)
                Set<Long> descendantIds = collectDescendantIds(category);
                if (descendantIds.contains(newParentId)) {
                    throw new IllegalArgumentException("Không thể chọn nhóm hàng con cháu làm nhóm cha (gây vòng lặp chu trình cây)");
                }

                category.setParent(newParent);
                category.setLevel(newParent.getLevel() + 1);
            }
            updateDescendantsLevels(category);
        }

        Category saved = categoryRepository.save(category);
        return mapToResponse(saved, false);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng với ID: " + id));

        // Quy tắc cốt lõi: Nhóm còn sản phẩm thì không xoá được (S2-06)
        long directProductsCount = productRepository.countByCategoryId(id);
        if (directProductsCount > 0) {
            throw new CategoryNotEmptyException("Nhóm còn " + directProductsCount + " sản phẩm thì không xoá được. Vui lòng chuyển hoặc xoá sản phẩm trước.");
        }

        // Kiểm tra các nhóm con
        long subCategoryCount = categoryRepository.countByParentId(id);
        if (subCategoryCount > 0) {
            Set<Long> descendantIds = collectDescendantIds(category);
            long subProductsCount = productRepository.countByCategoryIdIn(descendantIds);
            if (subProductsCount > 0) {
                throw new CategoryNotEmptyException("Không thể xoá nhóm hàng vì các nhóm con vẫn còn " + subProductsCount + " sản phẩm.");
            }
            throw new CategoryNotEmptyException("Không thể xoá nhóm hàng vì vẫn còn " + subCategoryCount + " nhóm con. Vui lòng xoá hoặc chuyển các nhóm con trước.");
        }

        categoryRepository.delete(category);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng với ID: " + id));
        return buildCategoryNode(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoryTree() {
        // Lấy tất cả các nhóm hàng gốc (Cấp 1 - Ngành hàng)
        List<Category> roots = categoryRepository.findByParentIsNullOrderByNameAsc();
        return roots.stream()
                .map(this::buildCategoryNode)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllFlat() {
        List<Category> all = categoryRepository.findAllByOrderByLevelAscNameAsc();
        return all.stream()
                .map(c -> mapToResponse(c, false))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryRevenue(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng với ID: " + id));
        return buildCategoryNode(category);
    }

    @Override
    @Transactional
    public void moveProducts(MoveProductsRequest request) {
        Category targetCategory = categoryRepository.findById(request.getTargetCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng đích với ID: " + request.getTargetCategoryId()));

        List<Product> products = productRepository.findAllById(request.getProductIds());
        if (products.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy sản phẩm nào trong danh sách được cung cấp");
        }

        for (Product product : products) {
            product.setCategory(targetCategory);
        }
        productRepository.saveAll(products);
    }

    @Override
    @Transactional
    public void moveSingleProduct(Long productId, Long targetCategoryId) {
        Category targetCategory = categoryRepository.findById(targetCategoryId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng đích với ID: " + targetCategoryId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + productId));

        product.setCategory(targetCategory);
        productRepository.save(product);
    }

    // ── Helper methods ──

    private CategoryResponse buildCategoryNode(Category category) {
        CategoryResponse response = mapToResponse(category, true);

        // Lấy các nhóm con trực tiếp
        List<Category> children = categoryRepository.findByParentIdOrderByNameAsc(category.getId());
        List<CategoryResponse> childResponses = children.stream()
                .map(this::buildCategoryNode)
                .collect(Collectors.toList());
        response.setChildren(childResponses);

        // Tính tổng sản phẩm và tổng doanh số của toàn bộ cây nhánh này
        Set<Long> subtreeCategoryIds = collectSubtreeCategoryIds(category);
        long totalProducts = productRepository.countByCategoryIdIn(subtreeCategoryIds);
        BigDecimal totalRevenue = productRepository.sumRevenueByCategoryIds(subtreeCategoryIds);

        response.setTotalProductCount(totalProducts);
        response.setTotalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO);

        return response;
    }

    private CategoryResponse mapToResponse(Category category, boolean calculateDirectStats) {
        Long parentId = category.getParent() != null ? category.getParent().getId() : null;
        String parentName = category.getParent() != null ? category.getParent().getName() : null;
        String parentCode = category.getParent() != null ? category.getParent().getCode() : null;

        long directCount = 0L;
        BigDecimal directRevenue = BigDecimal.ZERO;

        if (calculateDirectStats && category.getId() != null) {
            directCount = productRepository.countByCategoryId(category.getId());
            BigDecimal rev = productRepository.sumRevenueByCategoryIds(Collections.singleton(category.getId()));
            directRevenue = rev != null ? rev : BigDecimal.ZERO;
        }

        return CategoryResponse.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .description(category.getDescription())
                .level(category.getLevel())
                .parentId(parentId)
                .parentName(parentName)
                .parentCode(parentCode)
                .directProductCount(directCount)
                .totalProductCount(directCount)
                .directRevenue(directRevenue)
                .totalRevenue(directRevenue)
                .children(new ArrayList<>())
                .build();
    }

    private Set<Long> collectSubtreeCategoryIds(Category category) {
        Set<Long> ids = new HashSet<>();
        if (category.getId() != null) {
            ids.add(category.getId());
            List<Category> children = categoryRepository.findByParentIdOrderByNameAsc(category.getId());
            for (Category child : children) {
                ids.addAll(collectSubtreeCategoryIds(child));
            }
        }
        return ids;
    }

    private Set<Long> collectDescendantIds(Category category) {
        Set<Long> ids = new HashSet<>();
        if (category.getId() != null) {
            List<Category> children = categoryRepository.findByParentIdOrderByNameAsc(category.getId());
            for (Category child : children) {
                ids.add(child.getId());
                ids.addAll(collectDescendantIds(child));
            }
        }
        return ids;
    }

    private void updateDescendantsLevels(Category parent) {
        List<Category> children = categoryRepository.findByParentIdOrderByNameAsc(parent.getId());
        for (Category child : children) {
            child.setLevel(parent.getLevel() + 1);
            categoryRepository.save(child);
            updateDescendantsLevels(child);
        }
    }
}
