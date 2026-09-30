package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.ProductRequest;
import com.quanlybanhang.dto.response.ProductResponse;
import com.quanlybanhang.model.Category;
import com.quanlybanhang.model.Product;
import com.quanlybanhang.repository.CategoryRepository;
import com.quanlybanhang.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Mã sản phẩm '" + request.getCode() + "' đã tồn tại");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng với ID: " + request.getCategoryId()));

        Product product = Product.builder()
                .code(request.getCode().trim().toUpperCase())
                .name(request.getName().trim())
                .description(request.getDescription())
                .category(category)
                .price(request.getPrice())
                .costPrice(request.getCostPrice())
                .revenue(request.getRevenue())
                .unit(request.getUnit() != null ? request.getUnit() : "cái")
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + id));

        if (productRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new IllegalArgumentException("Mã sản phẩm '" + request.getCode() + "' đã được sử dụng");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng với ID: " + request.getCategoryId()));

        product.setCode(request.getCode().trim().toUpperCase());
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setCategory(category);
        product.setPrice(request.getPrice());
        product.setCostPrice(request.getCostPrice());
        product.setRevenue(request.getRevenue());
        if (request.getUnit() != null) {
            product.setUnit(request.getUnit());
        }
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }

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
        List<Product> products;
        if (categoryId != null) {
            products = productRepository.findByCategoryId(categoryId);
        } else {
            products = productRepository.findAll();
        }
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + id));
        productRepository.delete(product);
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .code(product.getCode())
                .name(product.getName())
                .description(product.getDescription())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryCode(product.getCategory() != null ? product.getCategory().getCode() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .categoryLevel(product.getCategory() != null ? product.getCategory().getLevel() : null)
                .price(product.getPrice())
                .revenue(product.getRevenue())
                .unit(product.getUnit())
                .active(product.getActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
