package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.ProductRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.ProductResponse;
import com.quanlybanhang.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Controller Quản lý danh mục sản phẩm (S2-05).
 *
 * Yêu cầu nghiệp vụ:
 * - Khai báo mã SKU, tên, nhóm hàng, đơn vị tính cơ sở, quy cách đóng gói, giá vốn, ảnh, trạng thái
 * - Mã SKU là duy nhất
 * - Giá vốn chỉ Quản lý kinh doanh xem và sửa được
 * - Sản phẩm đã phát sinh giao dịch thì không xoá được, chỉ ngừng kinh doanh
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final Path imageStoragePath = Paths.get("./data/products").toAbsolutePath().normalize();

    @GetMapping
    public ApiResponse getAllProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active) {
        List<ProductResponse> products = (search != null || active != null)
                ? productService.getAll(categoryId, search, active)
                : productService.getAll(categoryId);
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

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse updateProductStatus(@PathVariable Long id, @RequestParam boolean active) {
        ProductResponse response = productService.updateStatus(id, active);
        String msg = active ? "Đã kích hoạt kinh doanh sản phẩm" : "Đã chuyển sản phẩm sang ngừng kinh doanh";
        return ApiResponse.success(msg, response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ApiResponse deleteProduct(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.success("Xóa sản phẩm thành công", null);
    }

    /**
     * Upload ảnh sản phẩm (S2-05).
     */
    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse> uploadProductImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Tập tin ảnh không được để trống"));
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        Set<String> allowedExtensions = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");
        if (!allowedExtensions.contains(extension)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Định dạng ảnh không hợp lệ (hỗ trợ JPG, PNG, WEBP, GIF)"));
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Kích thước ảnh tối đa là 5MB"));
        }

        try {
            if (!Files.exists(imageStoragePath)) {
                Files.createDirectories(imageStoragePath);
            }

            String newFileName = UUID.randomUUID() + extension;
            Path targetLocation = imageStoragePath.resolve(newFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "/products/images/" + newFileName;
            return ResponseEntity.ok(ApiResponse.success("Tải ảnh lên thành công", Map.of(
                    "imageUrl", fileUrl,
                    "fileName", newFileName
            )));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Không thể lưu tập tin ảnh: " + e.getMessage()));
        }
    }

    /**
     * Xem / Tải ảnh sản phẩm đã lưu.
     */
    @GetMapping("/images/{fileName:.+}")
    public ResponseEntity<Resource> getProductImage(@PathVariable String fileName) {
        try {
            Path filePath = imageStoragePath.resolve(fileName).normalize();
            if (!filePath.startsWith(imageStoragePath)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}

