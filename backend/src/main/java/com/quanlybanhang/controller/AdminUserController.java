package com.quanlybanhang.controller;

import com.quanlybanhang.config.RolePermissionConfig;
import com.quanlybanhang.dto.request.AdminUserRequest;
import com.quanlybanhang.dto.request.LockUserRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.Permission;
import com.quanlybanhang.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService adminUserService;
    private final RolePermissionConfig rolePermissionConfig;

    @GetMapping("/users")
    public ApiResponse search(@RequestParam(required = false) String search,
                              @RequestParam(required = false) ERole role,
                              @RequestParam(required = false) Boolean active,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ApiResponse.success("Danh sách tài khoản",
                adminUserService.search(search, role, active,
                        PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending())));
    }

    @GetMapping("/users/{id}")
    public ApiResponse get(@PathVariable Long id) {
        return ApiResponse.success("Chi tiết tài khoản", adminUserService.get(id));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse> create(@Valid @RequestBody AdminUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Đã tạo tài khoản và gửi thông tin kích hoạt qua email", adminUserService.create(request)));
    }

    @PostMapping("/users/import/preview")
    public ApiResponse previewImport(@RequestBody List<AdminUserRequest> rows) {
        return ApiResponse.success("Kết quả kiểm tra tệp nhập người dùng",
                adminUserService.previewImport(rows));
    }

    @PostMapping("/users/import")
    public ApiResponse importUsers(@RequestBody List<AdminUserRequest> rows) {
        var result = adminUserService.importUsers(rows);
        return ApiResponse.success("Đã nhập " + result.getImportedRows() + "/" + result.getTotalRows()
                + " tài khoản hợp lệ", result);
    }

    @PutMapping("/users/{id}")
    public ApiResponse update(@PathVariable Long id,
                              @Valid @RequestBody AdminUserRequest request,
                              Authentication authentication) {
        return ApiResponse.success("Đã cập nhật tài khoản",
                adminUserService.update(id, request, authentication.getName()));
    }

    @PostMapping("/users/{id}/lock")
    public ApiResponse lock(@PathVariable Long id,
                            @Valid @RequestBody LockUserRequest request,
                            Authentication authentication) {
        var user = adminUserService.lock(id, request.getReason(), authentication.getName());
        String message = user.getAssignedDealerCount() > 0
                ? "Đã khoá tài khoản. Có " + user.getAssignedDealerCount() + " đại lý cần bàn giao."
                : "Đã khoá tài khoản và thu hồi mọi phiên đang mở";
        return ApiResponse.success(message, user);
    }

    @PostMapping("/users/{id}/unlock")
    public ApiResponse unlock(@PathVariable Long id) {
        return ApiResponse.success("Đã mở khoá tài khoản", adminUserService.unlock(id));
    }

    @GetMapping("/assignment-options")
    public ApiResponse assignmentOptions() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roles", Arrays.stream(ERole.values()).map(Enum::name).toList());
        data.put("warehouses", adminUserService.warehouses());
        data.put("territories", adminUserService.territories());
        data.put("dealers", adminUserService.dealers());
        return ApiResponse.success("Dữ liệu gán vai trò", data);
    }

    @GetMapping("/permissions")
    public ApiResponse permissions() {
        Map<String, Object> matrix = new LinkedHashMap<>();
        rolePermissionConfig.getAllPermissions().forEach((role, permissions) ->
                matrix.put(role.name(), permissions.stream().map(Permission::name).sorted().toList()));
        List<Map<String, String>> catalog = Arrays.stream(Permission.values())
                .map(permission -> Map.of(
                        "code", permission.name(),
                        "label", permission.getDisplayName()))
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matrix", matrix);
        data.put("permissions", catalog);
        return ApiResponse.success("Ma trận phân quyền", data);
    }

    @PutMapping("/permissions/{role}")
    public ApiResponse updatePermissions(@PathVariable ERole role,
                                         @RequestBody java.util.Set<Permission> permissions) {
        rolePermissionConfig.updateRolePermissions(role, permissions);
        return ApiResponse.success("Đã cập nhật quyền cho vai trò " + role.name(),
                rolePermissionConfig.getPermissions(role).stream().map(Permission::name).sorted().toList());
    }

    @PostMapping("/permissions/reset")
    public ApiResponse resetPermissions() {
        rolePermissionConfig.resetToDefault();
        return ApiResponse.success("Đã khôi phục ma trận phân quyền về mặc định", null);
    }
}
