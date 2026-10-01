package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.CategoryRequest;
import com.quanlybanhang.dto.request.MoveProductsRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.CategoryResponse;
import com.quanlybanhang.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller Quản lý nhóm hàng nhiều cấp (S2-06).
 *
 * Yêu cầu nghiệp vụ:
 * - Nhóm hàng có cấu trúc cây, tối thiểu ba cấp
 * - Chuyển sản phẩm giữa các nhóm được
 * - Nhóm còn sản phẩm thì không xoá được
 * - Xem doanh số theo ngành hàng chứ không chỉ theo từng mã
 * - Vai trò: Quản lý kinh doanh (ROLE_SALES_MANAGER) và Quản trị viên (ROLE_ADMIN).
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Lấy cây nhóm hàng nhiều cấp (hỗ trợ tối thiểu 3 cấp và hiển thị doanh số ngành hàng).
     */
    @GetMapping("/tree")
    public ApiResponse getCategoryTree() {
        List<CategoryResponse> tree = categoryService.getCategoryTree();
        return ApiResponse.success("Cây nhóm hàng nhiều cấp", tree);
    }

    /**
     * Lấy danh sách nhóm hàng dạng phẳng.
     */
    @GetMapping
    public ApiResponse getAllCategories() {
        return ApiResponse.success("Danh sách nhóm hàng", categoryService.getAllFlat());
    }

    /**
     * Lấy chi tiết nhóm hàng theo ID.
     */
    @GetMapping("/{id}")
    public ApiResponse getCategoryById(@PathVariable Long id) {
        return ApiResponse.success("Chi tiết nhóm hàng", categoryService.getById(id));
    }

    /**
     * Xem doanh số theo ngành hàng / nhóm hàng (tổng hợp doanh số của toàn bộ cây con).
     */
    @GetMapping("/{id}/revenue")
    public ApiResponse getCategoryRevenue(@PathVariable Long id) {
        return ApiResponse.success("Doanh số theo ngành hàng", categoryService.getCategoryRevenue(id));
    }

    /**
     * Tạo nhóm hàng mới.
     * Chỉ Quản lý kinh doanh (ROLE_SALES_MANAGER) và Quản trị viên (ROLE_ADMIN).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo nhóm hàng thành công", response));
    }

    /**
     * Cập nhật thông tin nhóm hàng.
     * Chỉ Quản lý kinh doanh (ROLE_SALES_MANAGER) và Quản trị viên (ROLE_ADMIN).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.update(id, request);
        return ApiResponse.success("Cập nhật nhóm hàng thành công", response);
    }

    /**
     * Xóa nhóm hàng.
     * Quy tắc: Nhóm còn sản phẩm thì KHÔNG xoá được!
     * Chỉ Quản lý kinh doanh (ROLE_SALES_MANAGER) và Quản trị viên (ROLE_ADMIN).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.success("Xóa nhóm hàng thành công", null);
    }

    /**
     * Chuyển danh sách sản phẩm sang nhóm hàng đích.
     * Quy tắc: Chuyển sản phẩm giữa các nhóm được (S2-06).
     * Chỉ Quản lý kinh doanh (ROLE_SALES_MANAGER) và Quản trị viên (ROLE_ADMIN).
     */
    @PostMapping("/move-products")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse moveProducts(@Valid @RequestBody MoveProductsRequest request) {
        categoryService.moveProducts(request);
        return ApiResponse.success("Chuyển sản phẩm giữa các nhóm thành công", null);
    }

    /**
     * Chuyển 1 sản phẩm sang nhóm hàng đích.
     */
    @PutMapping("/products/{productId}/move")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse moveSingleProduct(@PathVariable Long productId, @RequestParam Long targetCategoryId) {
        categoryService.moveSingleProduct(productId, targetCategoryId);
        return ApiResponse.success("Chuyển sản phẩm sang nhóm mới thành công", null);
    }
}
