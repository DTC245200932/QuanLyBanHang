package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request tạo / sửa nhóm hàng.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequest {

    @NotBlank(message = "Mã nhóm hàng không được để trống")
    @Size(max = 50, message = "Mã nhóm hàng tối đa 50 ký tự")
    private String code;

    @NotBlank(message = "Tên nhóm hàng không được để trống")
    @Size(max = 255, message = "Tên nhóm hàng tối đa 255 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
    private String description;

    /**
     * ID của nhóm hàng cha.
     * Để trống hoặc null nếu là ngành hàng gốc (Cấp 1).
     */
    private Long parentId;
}
