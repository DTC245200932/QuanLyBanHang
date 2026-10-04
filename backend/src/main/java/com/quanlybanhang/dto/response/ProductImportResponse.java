package com.quanlybanhang.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả tổng quan của quá trình xem trước (preview) hoặc nhập danh mục sản phẩm từ Excel (S2-08).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImportResponse {

    /**
     * Tổng số dòng dữ liệu đọc được từ tệp.
     */
    private int totalRows;

    /**
     * Số dòng hợp lệ.
     */
    private int validRows;

    /**
     * Số dòng có lỗi nghiệp vụ / định dạng.
     */
    private int invalidRows;

    /**
     * Số sản phẩm mới sẽ được tạo (SKU chưa có trong hệ thống).
     */
    private int createCount;

    /**
     * Số sản phẩm sẽ được cập nhật (SKU đã tồn tại trong hệ thống).
     */
    private int updateCount;

    /**
     * Số sản phẩm đã nhập thành công vào cơ sở dữ liệu (khi thực thi import).
     */
    private int importedRows;

    /**
     * Chi tiết kiểm tra và trạng thái từng dòng.
     */
    @Builder.Default
    private List<ProductImportRowResponse> rows = new ArrayList<>();
}

