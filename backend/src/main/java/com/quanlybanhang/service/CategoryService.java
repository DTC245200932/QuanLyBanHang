package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.CategoryRequest;
import com.quanlybanhang.dto.request.MoveProductsRequest;
import com.quanlybanhang.dto.response.CategoryResponse;

import java.util.List;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Long id, CategoryRequest request);

    void delete(Long id);

    CategoryResponse getById(Long id);

    List<CategoryResponse> getCategoryTree();

    List<CategoryResponse> getAllFlat();

    CategoryResponse getCategoryRevenue(Long id);

    void moveProducts(MoveProductsRequest request);

    void moveSingleProduct(Long productId, Long targetCategoryId);
}
