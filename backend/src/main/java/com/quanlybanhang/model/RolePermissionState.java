package com.quanlybanhang.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Đánh dấu một vai trò đã được quản trị viên cấu hình trong ma trận.
 *
 * Bảng role_permissions chỉ lưu các quyền được tick, vì vậy một vai trò
 * không còn quyền nào sẽ không có dòng nào để phân biệt với vai trò chưa
 * từng được lưu. Bảng này giữ lại trạng thái đó qua các lần restart backend.
 */
@Entity
@Table(name = "role_permission_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermissionState {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false, length = 30)
    private ERole role;

    @Column(nullable = false)
    private boolean configured;
}
