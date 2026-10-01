package com.quanlybanhang.controller;

import com.quanlybanhang.config.RolePermissionConfig;
import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.Permission;
import com.quanlybanhang.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller test phân quyền — dùng để kiểm tra roles và permissions.
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final RolePermissionConfig rolePermissionConfig;

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
     * Trả về thông tin user + permissions thực tế.
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> userAccess(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // Lấy danh sách role của user
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // Gom permissions từ tất cả roles
        List<ERole> eRoles = roles.stream()
                .map(ERole::valueOf)
                .collect(Collectors.toList());
        Set<Permission> permissions = rolePermissionConfig.getPermissions(eRoles);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Xin chào " + userDetails.getFullName());
        response.put("username", userDetails.getUsername());
        response.put("email", userDetails.getEmail());
        response.put("roles", roles);
        response.put("permissions", permissions.stream()
                .map(Permission::name)
                .sorted()
                .collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }

    /**
     * API chỉ dành cho quản lý (SALES_MANAGER, WAREHOUSE_MANAGER, ADMIN).
     */
    @GetMapping("/manager")
    @PreAuthorize("hasAnyRole('SALES_MANAGER', 'WAREHOUSE_MANAGER', 'ADMIN')")
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

    /**
     * API xem ma trận phân quyền — chỉ ADMIN.
     */
    @GetMapping("/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> viewPermissionMatrix() {
        Map<String, Object> matrix = new HashMap<>();
        rolePermissionConfig.getAllPermissions().forEach((role, perms) -> {
            matrix.put(role.name(), perms.stream()
                    .map(Permission::name)
                    .sorted()
                    .collect(Collectors.toList()));
        });

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Ma trận phân quyền hệ thống");
        response.put("permissionMatrix", matrix);
        return ResponseEntity.ok(response);
    }
}
