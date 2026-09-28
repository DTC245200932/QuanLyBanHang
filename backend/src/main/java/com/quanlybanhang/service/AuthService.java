package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.*;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.JwtResponse;
import com.quanlybanhang.exception.AccountTemporarilyLockedException;
import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.Role;
import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.RoleRepository;
import com.quanlybanhang.repository.UserRepository;
import com.quanlybanhang.security.JwtUtils;
import com.quanlybanhang.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service xử lý logic đăng nhập, đăng ký, refresh token, đổi mật khẩu, đăng xuất.
 *
 * Bao gồm:
 * - Login với lockout (5 lần sai → khoá 15 phút)
 * - Đăng ký với 7 vai trò nghiệp vụ
 * - Refresh token
 * - Đổi mật khẩu (S1-04)
 * - Đăng xuất với invalidation phía server (S1-02)
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    /** Số lần đăng nhập sai tối đa trước khi khoá tạm. */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /** Thời gian khoá tạm (phút). */
    private static final int LOCK_DURATION_MINUTES = 15;

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Đăng nhập — kiểm tra lockout trước, rồi xác thực.
     *
     * Flow:
     * 1. Kiểm tra user tồn tại (trả message chung nếu không)
     * 2. Kiểm tra khoá tạm (15 phút sau 5 lần sai)
     * 3. Kiểm tra khoá bởi admin (active=false)
     * 4. Xác thực password
     * 5. Đúng → reset failed count, trả JWT
     * 6. Sai → tăng failed count, khoá nếu đủ 5 lần
     */
    @Transactional
    public JwtResponse login(LoginRequest request) {
        // Tìm user — nếu không có, trả message chung (không tiết lộ user có tồn tại)
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException(
                        "Tên đăng nhập hoặc mật khẩu không đúng"));

        // Kiểm tra khoá tạm do sai password
        if (user.isTemporarilyLocked()) {
            long remaining = Duration.between(LocalDateTime.now(), user.getLockTime()).toMinutes() + 1;
            throw new AccountTemporarilyLockedException(remaining);
        }

        // Kiểm tra bị admin khoá
        if (!user.getActive()) {
            throw new LockedException("Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        // Xác thực password
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()));

            // ✅ Đăng nhập thành công → reset failed count
            resetFailedAttempts(user);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String accessToken = jwtUtils.generateAccessToken(authentication);
            String refreshToken = jwtUtils.generateRefreshToken(authentication);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            return JwtResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .id(userDetails.getId())
                    .username(userDetails.getUsername())
                    .email(userDetails.getEmail())
                    .fullName(userDetails.getFullName())
                    .roles(roles)
                    .build();

        } catch (BadCredentialsException e) {
            // ❌ Sai password → tăng failed count
            handleFailedLogin(user);
            // Luôn trả message chung — không tiết lộ username có tồn tại
            throw new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không đúng");
        }
    }

    /**
     * Xử lý khi đăng nhập sai: tăng failedLoginAttempts, khoá nếu đủ 5 lần.
     */
    private void handleFailedLogin(User user) {
        int newFailCount = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(newFailCount);

        if (newFailCount >= MAX_FAILED_ATTEMPTS) {
            user.setLockTime(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            user.setFailedLoginAttempts(0); // Reset count — bắt đầu đếm lại sau khi hết khoá
            logger.warn("Tài khoản '{}' bị khoá {} phút do {} lần đăng nhập sai",
                    user.getUsername(), LOCK_DURATION_MINUTES, MAX_FAILED_ATTEMPTS);
        }

        userRepository.save(user);
    }

    /**
     * Reset số lần sai về 0 và xoá thời gian khoá khi đăng nhập đúng.
     */
    private void resetFailedAttempts(User user) {
        if (user.getFailedLoginAttempts() > 0 || user.getLockTime() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockTime(null);
            userRepository.save(user);
        }
    }

    /**
     * Đăng ký tài khoản mới.
     */
    @Transactional
    public ApiResponse register(RegisterRequest request) {
        // Kiểm tra username đã tồn tại
        if (userRepository.existsByUsername(request.getUsername())) {
            return ApiResponse.error("Tên đăng nhập đã được sử dụng");
        }

        // Kiểm tra email đã tồn tại
        if (userRepository.existsByEmail(request.getEmail())) {
            return ApiResponse.error("Email đã được sử dụng");
        }

        // Xử lý roles
        Set<Role> roles = new HashSet<>();
        if (request.getRoles() == null || request.getRoles().isEmpty()) {
            // Mặc định là SALES (nhân viên kinh doanh)
            Role defaultRole = roleRepository.findByName(ERole.ROLE_SALES)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy role SALES"));
            roles.add(defaultRole);
        } else {
            for (String roleName : request.getRoles()) {
                ERole eRole;
                try {
                    eRole = ERole.valueOf(roleName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ApiResponse.error("Role không hợp lệ: " + roleName);
                }
                Role role = roleRepository.findByName(eRole)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy role: " + roleName));
                roles.add(role);
            }
        }

        // Tạo user mới
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .roles(roles)
                .build();

        userRepository.save(user);

        return ApiResponse.success("Đăng ký tài khoản thành công");
    }

    /**
     * Refresh access token bằng refresh token.
     */
    public JwtResponse refreshToken(String refreshToken) {
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new RuntimeException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        // Kiểm tra refresh token có bị blacklist không
        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            throw new RuntimeException("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        }

        String username = jwtUtils.getUsernameFromToken(refreshToken);
        String newAccessToken = jwtUtils.generateAccessTokenFromUsername(username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        List<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toList());

        return JwtResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken) // giữ nguyên refresh token
                .tokenType("Bearer")
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .build();
    }

    /**
     * Đăng xuất — blacklist access token để vô hiệu hoá ngay phía server (S1-02).
     */
    @Transactional
    public void logout(String accessToken) {
        tokenBlacklistService.blacklistToken(accessToken, "LOGOUT");
    }

    /**
     * Đổi mật khẩu khi đang đăng nhập (S1-04).
     *
     * - Bắt buộc nhập mật khẩu hiện tại
     * - Mật khẩu mới tối thiểu 8 ký tự, có chữ và số
     * - Đổi xong blacklist token hiện tại (thu hồi phiên này)
     */
    @Transactional
    public ApiResponse changePassword(String username, ChangePasswordRequest request, String currentToken) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        // Kiểm tra mật khẩu hiện tại
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            return ApiResponse.error("Mật khẩu hiện tại không đúng");
        }

        // Kiểm tra mật khẩu mới không trùng mật khẩu cũ
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            return ApiResponse.error("Mật khẩu mới không được trùng mật khẩu hiện tại");
        }

        // Cập nhật mật khẩu
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Blacklist token hiện tại — yêu cầu đăng nhập lại
        tokenBlacklistService.blacklistToken(currentToken, "PASSWORD_CHANGE");

        logger.info("User '{}' đã đổi mật khẩu thành công", username);

        return ApiResponse.success("Đổi mật khẩu thành công. Vui lòng đăng nhập lại.");
    }
}

