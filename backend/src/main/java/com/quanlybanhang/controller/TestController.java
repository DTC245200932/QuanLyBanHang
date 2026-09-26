package com.quanlybanhang.controller;

import com.quanlybanhang.security.UserDetailsImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller test phân quyền - dùng để kiểm tra các role.
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    /**
     * API public - ai cũng truy cập được.
     */
    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> publicAccess() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "API công khai - ai cũng truy cập được");
        return ResponseEntity.ok(response);
    }

    /**
     * API yêu cầu đăng nhập - tất cả user đã đăng nhập.
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> userAccess(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Xin chào " + userDetails.getFullName());
        response.put("username", userDetails.getUsername());
        response.put("email", userDetails.getEmail());
        return ResponseEntity.ok(response);
    }

    /**
     * API chỉ dành cho MANAGER trở lên.
     */
    @GetMapping("/manager")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> managerAccess() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Trang quản lý - chỉ Manager và Admin truy cập được");
        return ResponseEntity.ok(response);
    }

    /**
     * API chỉ dành cho ADMIN.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> adminAccess() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Trang quản trị - chỉ Admin truy cập được");
        return ResponseEntity.ok(response);
    }
}
