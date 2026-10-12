package com.quanlybanhang.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO đại diện cho một dòng dữ liệu sản phẩm khi nhập hàng loạt từ Excel hoặc JSON (S2-08).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductImportRowRequest {

    private Integer rowNumber;

    /**
     * Mã SKU / Code sản phẩm.
     */
    private String sku;

    /**
     * Alias cho SKU nếu dùng tên 'code'.
     */
    private String code;

    /**
     * Tên sản phẩm.
     */
    private String name;

    /**
     * Mã nhóm hàng hoặc tên nhóm hàng (ví dụ: "BEV-BEER" hoặc "Bia các loại").
     */
    private String category;

    /**
     * ID nhóm hàng (nếu có trực tiếp).
     */
    private Long categoryId;

    /**
     * Đơn vị tính cơ sở (cái, lon, chai, hộp...).
     */
    private String unit;

    /**
     * Quy cách đóng gói (Thùng 24 lon, Lốc 6 chai...).
     */
    private String packingSpecification;

    /**
     * Giá bán.
     */
    private BigDecimal price;

    /**
     * Giá vốn (chỉ Quản lý kinh doanh được xem và chỉnh sửa).
     */
    private BigDecimal costPrice;

    /**
     * Trạng thái kinh doanh: true/false.
     */
    private Boolean active;

    /**
     * Mô tả sản phẩm.
     */
    private String description;

    /** Lỗi đọc ô Excel; chỉ do backend điền, không nhận từ JSON của client. */
    @JsonIgnore
    private java.util.List<String> parseErrors;

    @JsonIgnore
    public String getEffectiveSku() {
        if (sku != null && !sku.isBlank()) {
            return sku.trim();
        }
        if (code != null && !code.isBlank()) {
            return code.trim();
        }
        return "";
    }
}

