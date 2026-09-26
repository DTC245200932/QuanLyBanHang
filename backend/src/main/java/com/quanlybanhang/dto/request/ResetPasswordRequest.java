package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO cho request đặt lại mật khẩu.
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Token không được để trống")
    private String token;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, max = 100, message = "Mật khẩu phải từ 6-100 ký tự")
    private String newPassword;
}
