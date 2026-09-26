package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.*;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.JwtResponse;
import com.quanlybanhang.service.AuthService;
import com.quanlybanhang.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller xử lý các API liên quan đến xác thực:
 * - POST /api/auth/login       → Đăng nhập
 * - POST /api/auth/register    → Đăng ký
 * - POST /api/auth/refresh     → Refresh token
 * - POST /api/auth/forgot-password → Quên mật khẩu
 * - POST /api/auth/reset-password  → Đặt lại mật khẩu
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    /**
     * Đăng nhập.
     *
     * Request body:
     * {
     *   "username": "admin",
     *   "password": "123456"
     * }
     *
     * Response: JwtResponse (accessToken, refreshToken, user info, roles)
     */
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest request) {
        JwtResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Đăng ký tài khoản mới.
     *
     * Request body:
     * {
     *   "username": "nhanvien1",
     *   "email": "nv1@email.com",
     *   "password": "123456",
     *   "fullName": "Nguyen Van A",
     *   "phone": "0901234567",
     *   "roles": ["ROLE_EMPLOYEE"]   // optional, mặc định ROLE_EMPLOYEE
     * }
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody RegisterRequest request) {
        ApiResponse response = authService.register(request);
        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Refresh access token.
     *
     * Request body:
     * {
     *   "refreshToken": "eyJhbGciOi..."
     * }
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refreshToken(@RequestBody java.util.Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("Refresh token không được để trống");
        }
        JwtResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    /**
     * Quên mật khẩu - gửi email reset.
     *
     * Request body:
     * {
     *   "email": "user@email.com"
     * }
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.sendResetPasswordEmail(request.getEmail());
        return ResponseEntity.ok(
                ApiResponse.success("Email đặt lại mật khẩu đã được gửi. Vui lòng kiểm tra hộp thư."));
    }

    /**
     * Đặt lại mật khẩu bằng token từ email.
     *
     * Request body:
     * {
     *   "token": "uuid-token-from-email",
     *   "newPassword": "matkhaumoi123"
     * }
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(
                ApiResponse.success("Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập lại."));
    }
}
