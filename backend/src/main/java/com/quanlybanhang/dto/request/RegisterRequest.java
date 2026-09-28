package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Set;

/**
 * DTO cho request đăng ký tài khoản.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 3, max = 50, message = "Tên đăng nhập phải từ 3-50 ký tự")
    private String username;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, max = 100, message = "Mật khẩu phải từ 8-100 ký tự")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "Mật khẩu phải có cả chữ và số")
    private String password;

    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    private String fullName;

    @Size(max = 15, message = "Số điện thoại tối đa 15 ký tự")
    private String phone;

    // Danh sách role: ["ROLE_ADMIN", "ROLE_MANAGER", "ROLE_EMPLOYEE"]
    // Nếu không truyền, mặc định là ROLE_EMPLOYEE
    private Set<String> roles;
}
