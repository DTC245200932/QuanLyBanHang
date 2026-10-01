package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

/**
 * Request tạo / sửa sản phẩm.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank(message = "Mã sản phẩm không được để trống")
    @Size(max = 50, message = "Mã sản phẩm tối đa 50 ký tự")
    private String code;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 255, message = "Tên sản phẩm tối đa 255 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
    private String description;

    @NotNull(message = "Nhóm hàng không được để trống")
    private Long categoryId;

    @PositiveOrZero(message = "Giá bán không được âm")
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @PositiveOrZero(message = "Giá vốn không được âm")
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO;

    @PositiveOrZero(message = "Doanh số không được âm")
    @Builder.Default
    private BigDecimal revenue = BigDecimal.ZERO;

    @Builder.Default
    private String unit = "cái";

    @Builder.Default
    private Boolean active = true;

    /**
     * Danh sách đơn vị quy đổi khai báo kèm sản phẩm (S2-07).
     * Ví dụ: lon (cơ sở), lốc (hệ số 6), thùng (hệ số 24).
     */
    private java.util.List<UnitConversionRequest> unitConversions;
}
