package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.ProductRequest;
import com.quanlybanhang.dto.response.ProductResponse;

import java.util.List;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductRequest request);

    ProductResponse getById(Long id);

    List<ProductResponse> getAll(Long categoryId);

    void delete(Long id);
}
