package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.*;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.JwtResponse;
import com.quanlybanhang.security.UserDetailsImpl;
import com.quanlybanhang.service.AuthService;
import com.quanlybanhang.service.AvatarService;
import com.quanlybanhang.service.PasswordResetService;
import com.quanlybanhang.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Controller xử lý các API liên quan đến xác thực:
 * - POST /api/auth/login           → Đăng nhập
 * - POST /api/auth/refresh         → Refresh token
 * - POST /api/auth/logout          → Đăng xuất (S1-02)
 * - POST /api/auth/change-password → Đổi mật khẩu (S1-04)
 * - POST /api/auth/forgot-password → Quên mật khẩu
 * - POST /api/auth/reset-password  → Đặt lại mật khẩu
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AdminUserService adminUserService;
    private final AvatarService avatarService;

    /**
     * Đăng nhập.
     */
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest request) {
        JwtResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh access token.
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
     * Đăng xuất — vô hiệu hoá token phía server (S1-02).
     * Nhận access token qua header (nếu còn) và refresh token qua body.
     * Endpoint vẫn xử lý refresh token khi access token đã hết hạn.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletRequest request,
                                               @RequestBody(required = false) java.util.Map<String, String> body) {
        String token = parseJwt(request);
        String refreshToken = body == null ? null : body.get("refreshToken");
        authService.logout(token, refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Thông tin người dùng",
                adminUserService.getByUsername(authentication.getName())));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> updateAvatar(
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        var user = avatarService.updateAvatar(authentication.getName(), file);
        Map<String, String> data = Map.of(
                "avatarUrl", AvatarService.avatarUrl(user, false),
                "avatarThumbnailUrl", AvatarService.avatarUrl(user, true));
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật ảnh đại diện", data));
    }

    @GetMapping("/users/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable Long id,
                                         @RequestParam(defaultValue = "false") boolean thumbnail) {
        return avatarService.getAvatar(id, thumbnail)
                .map(content -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noCache())
                        .contentType(MediaType.parseMediaType(content.contentType()))
                        .body(content.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Đổi mật khẩu khi đang đăng nhập (S1-04).
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String currentToken = parseJwt(httpRequest);

        ApiResponse response = authService.changePassword(
                userDetails.getUsername(), request, currentToken);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Quên mật khẩu - gửi email reset.
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
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(
                ApiResponse.success("Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập lại."));
    }

    /**
     * Lấy JWT token từ header Authorization: Bearer <token>
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
