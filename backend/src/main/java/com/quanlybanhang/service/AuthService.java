package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.*;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.JwtResponse;
import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.Role;
import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.RoleRepository;
import com.quanlybanhang.repository.UserRepository;
import com.quanlybanhang.security.JwtUtils;
import com.quanlybanhang.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service xử lý logic đăng nhập, đăng ký, refresh token.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    /**
     * Đăng nhập - trả về JWT token.
     */
    public JwtResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()));

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
            // Mặc định là EMPLOYEE
            Role employeeRole = roleRepository.findByName(ERole.ROLE_EMPLOYEE)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy role EMPLOYEE"));
            roles.add(employeeRole);
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
}
