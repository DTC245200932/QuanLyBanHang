package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.UserProfileRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.UserProfileResponse;
import com.quanlybanhang.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse> getProfile(Authentication authentication) {
        String username = resolveUsername(authentication);
        UserProfileResponse profile = userService.getUserProfile(username);
        return ResponseEntity.ok(ApiResponse.success("Lấy hồ sơ thành công", profile));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse> updateProfile(@Valid @RequestBody UserProfileRequest request,
                                                      Authentication authentication) {
        String username = resolveUsername(authentication);
        ApiResponse response = userService.updateUserProfile(username, request);
        return ResponseEntity.ok(response);
    }

    private String resolveUsername(Authentication authentication) {
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.getName() != null) {
            return current.getName();
        }
        throw new org.springframework.security.authentication.BadCredentialsException("Người dùng chưa được xác thực");
    }
}
