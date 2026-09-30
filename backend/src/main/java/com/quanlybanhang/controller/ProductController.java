package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.ProductRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.ProductResponse;
import com.quanlybanhang.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller Quản lý sản phẩm.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse getAllProducts(@RequestParam(required = false) Long categoryId) {
        List<ProductResponse> products = productService.getAll(categoryId);
        return ApiResponse.success("Danh sách sản phẩm", products);
    }

    @GetMapping("/{id}")
    public ApiResponse getProductById(@PathVariable Long id) {
        return ApiResponse.success("Chi tiết sản phẩm", productService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo sản phẩm thành công", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.update(id, request);
        return ApiResponse.success("Cập nhật sản phẩm thành công", response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse deleteProduct(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.success("Xóa sản phẩm thành công", null);
    }
}
