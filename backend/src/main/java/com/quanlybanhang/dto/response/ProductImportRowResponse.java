package com.quanlybanhang.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả kiểm tra hoặc nhập từng dòng sản phẩm từ Excel (S2-08).
 * Đáp ứng yêu cầu:
 * - Xem trước và báo lỗi theo từng dòng
 * - SKU đã tồn tại thì cập nhật thay vì tạo mới, có đánh dấu rõ trong bản xem trước (action = "UPDATE", isUpdate = true).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImportRowResponse {

    private int rowNumber;

    private String sku;

    private String name;

    private String category;

    private Long resolvedCategoryId;

    private String resolvedCategoryName;

    private String unit;

    private String packingSpecification;

    private BigDecimal price;

    /**
     * Giá vốn chỉ hiển thị đối với Quản lý kinh doanh (ROLE_SALES_MANAGER).
     */
    private BigDecimal costPrice;

    private Boolean active;

    private String description;

    /**
     * Hành động xử lý: "CREATE" (Tạo mới) hoặc "UPDATE" (Cập nhật).
     * Đánh dấu rõ ràng theo yêu cầu S2-08.
     */
    private String action;

    /**
     * Cờ đánh dấu SKU đã tồn tại trong hệ thống.
     */
    private boolean isUpdate;

    /**
     * ID của sản phẩm đang tồn tại (nếu là cập nhật).
     */
    private Long existingProductId;

    private boolean valid;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * Thông tin chi tiết sản phẩm sau khi lưu thành công (nếu thực hiện import).
     */
    private ProductResponse createdProduct;
}

