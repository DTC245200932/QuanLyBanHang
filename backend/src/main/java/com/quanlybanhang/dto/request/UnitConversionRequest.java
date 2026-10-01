package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

/**
 * Request khai báo / cập nhật đơn vị tính quy đổi của SKU (S2-07).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitConversionRequest {

    @NotBlank(message = "Tên đơn vị quy đổi không được để trống")
    @Size(max = 50, message = "Tên đơn vị quy đổi tối đa 50 ký tự")
    private String unitName;

    @NotNull(message = "Hệ số quy đổi không được để trống")
    @DecimalMin(value = "0.0001", message = "Hệ số quy đổi phải lớn hơn 0")
    private BigDecimal conversionFactor;

    private BigDecimal price;

    private BigDecimal costPrice;

    @Size(max = 50, message = "Mã vạch tối đa 50 ký tự")
    private String barcode;

    @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
    private String description;

    @Builder.Default
    private Boolean isBaseUnit = false;
}
