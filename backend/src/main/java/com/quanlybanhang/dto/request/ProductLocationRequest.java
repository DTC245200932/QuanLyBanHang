package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request gán sản phẩm vào vị trí kệ / khu trong kho (S5-03).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductLocationRequest {

    @NotNull(message = "ID sản phẩm không được để trống")
    private Long productId;

    /** Đánh dấu là vị trí mặc định của sản phẩm trong kho */
    @Builder.Default
    private Boolean isDefault = true;

    @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự")
    private String note;
}

