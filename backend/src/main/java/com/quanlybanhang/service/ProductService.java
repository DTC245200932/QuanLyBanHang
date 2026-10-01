package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.ProductRequest;
import com.quanlybanhang.dto.response.ProductResponse;

import java.util.List;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductRequest request);

    ProductResponse updateStatus(Long id, boolean active);

    ProductResponse getById(Long id);

    List<ProductResponse> getAll(Long categoryId);

    List<ProductResponse> getAll(Long categoryId, String search, Boolean active);

    void delete(Long id);
}

