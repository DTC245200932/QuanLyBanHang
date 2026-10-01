package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.ProductRequest;
import com.quanlybanhang.dto.response.ProductResponse;
import com.quanlybanhang.exception.ProductHasTransactionsException;
import com.quanlybanhang.model.Category;
import com.quanlybanhang.model.Permission;
import com.quanlybanhang.model.Product;
import com.quanlybanhang.repository.CategoryRepository;
import com.quanlybanhang.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service quản lý danh mục sản phẩm (S2-05).
 * Tuân thủ các quy tắc:
 * 1. Khai báo mã SKU, tên, nhóm hàng, đơn vị tính cơ sở, quy cách đóng gói, giá vốn, ảnh, trạng thái
 * 2. Mã SKU là duy nhất
 * 3. Giá vốn CHỈ Quản lý kinh doanh (ROLE_SALES_MANAGER) xem và sửa được
 * 4. Sản phẩm đã phát sinh giao dịch thì không xoá được, chỉ ngừng kinh doanh
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = request.getEffectiveSku();
        if (sku.isBlank()) {
            throw new IllegalArgumentException("Mã SKU không được để trống");
        }
        sku = sku.toUpperCase();

        if (productRepository.existsByCodeIgnoreCase(sku)) {
            throw new IllegalArgumentException("Mã SKU '" + sku + "' đã tồn tại");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng với ID: " + request.getCategoryId()));

        boolean canManageCostPrice = isSalesManager();
        BigDecimal costPrice = (canManageCostPrice && request.getCostPrice() != null)
                ? request.getCostPrice()
                : BigDecimal.ZERO;

        BigDecimal price = request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO;
        BigDecimal revenue = request.getRevenue() != null ? request.getRevenue() : BigDecimal.ZERO;
        boolean hasTrans = Boolean.TRUE.equals(request.getHasTransactions()) || revenue.compareTo(BigDecimal.ZERO) > 0;

        Product product = Product.builder()
                .code(sku)
                .name(request.getName().trim())
                .description(request.getDescription())
                .category(category)
                .price(price)
                .costPrice(costPrice)
                .revenue(revenue)
                .unit(request.getUnit() != null && !request.getUnit().isBlank() ? request.getUnit().trim() : "cái")
                .packingSpecification(request.getPackingSpecification() != null ? request.getPackingSpecification().trim() : null)
                .imageUrl(request.getImageUrl() != null ? request.getImageUrl().trim() : null)
                .active(request.getActive() != null ? request.getActive() : true)
                .hasTransactions(hasTrans)
                .build();

        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + id));

        String sku = request.getEffectiveSku();
        if (sku.isBlank()) {
            throw new IllegalArgumentException("Mã SKU không được để trống");
        }
        sku = sku.toUpperCase();

        if (productRepository.existsByCodeIgnoreCaseAndIdNot(sku, id)) {
            throw new IllegalArgumentException("Mã SKU '" + sku + "' đã được sử dụng");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng với ID: " + request.getCategoryId()));

        product.setCode(sku);
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setCategory(category);

        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }

        // Quy tắc: Giá vốn chỉ Quản lý kinh doanh được phép sửa
        if (isSalesManager()) {
            if (request.getCostPrice() != null) {
                product.setCostPrice(request.getCostPrice());
            }
        }
        // Nếu không phải Quản lý kinh doanh thì giữ nguyên giá vốn hiện tại

        if (request.getRevenue() != null) {
            product.setRevenue(request.getRevenue());
        }
        if (request.getUnit() != null && !request.getUnit().isBlank()) {
            product.setUnit(request.getUnit().trim());
        }
        if (request.getPackingSpecification() != null) {
            product.setPackingSpecification(request.getPackingSpecification().trim());
        }
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl().trim());
        }
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        if (request.getHasTransactions() != null) {
            product.setHasTransactions(request.getHasTransactions());
        }

        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse updateStatus(Long id, boolean active) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + id));
        product.setActive(active);
        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + id));
        return mapToResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAll(Long categoryId) {
        return getAll(categoryId, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAll(Long categoryId, String search, Boolean active) {
        List<Product> products;
        if (categoryId != null) {
            products = productRepository.findByCategoryId(categoryId);
        } else {
            products = productRepository.findAll();
        }

        return products.stream()
                .filter(p -> {
                    if (active != null && !active.equals(p.getActive())) {
                        return false;
                    }
                    if (search != null && !search.isBlank()) {
                        String q = search.trim().toLowerCase();
                        boolean matchSku = p.getCode() != null && p.getCode().toLowerCase().contains(q);
                        boolean matchName = p.getName() != null && p.getName().toLowerCase().contains(q);
                        return matchSku || matchName;
                    }
                    return true;
                })
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + id));

        // Quy tắc S2-05: Sản phẩm đã phát sinh giao dịch thì không xoá được, chỉ ngừng kinh doanh
        if (product.hasTransactions()) {
            throw new ProductHasTransactionsException(product.getCode(), product.getName());
        }

        productRepository.delete(product);
    }

    /**
     * Kiểm tra xem người dùng hiện tại có vai trò Quản lý kinh doanh (ROLE_SALES_MANAGER)
     * hoặc quyền FINANCE_VIEW_COST_PRICE hay không.
     */
    private boolean isSalesManager() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a ->
                "ROLE_SALES_MANAGER".equals(a.getAuthority()) ||
                Permission.FINANCE_VIEW_COST_PRICE.name().equals(a.getAuthority())
        );
    }

    private ProductResponse mapToResponse(Product product) {
        boolean canViewCostPrice = isSalesManager();
        boolean hasTrans = product.hasTransactions();

        return ProductResponse.builder()
                .id(product.getId())
                .code(product.getCode())
                .sku(product.getCode())
                .name(product.getName())
                .description(product.getDescription())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryCode(product.getCategory() != null ? product.getCategory().getCode() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .categoryLevel(product.getCategory() != null ? product.getCategory().getLevel() : null)
                .price(product.getPrice())
                .costPrice(canViewCostPrice ? product.getCostPrice() : null)
                .revenue(product.getRevenue())
                .unit(product.getUnit())
                .packingSpecification(product.getPackingSpecification())
                .imageUrl(product.getImageUrl())
                .active(product.getActive())
                .status(Boolean.TRUE.equals(product.getActive()) ? "Đang kinh doanh" : "Ngừng kinh doanh")
                .hasTransactions(hasTrans)
                .canDelete(!hasTrans)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}

