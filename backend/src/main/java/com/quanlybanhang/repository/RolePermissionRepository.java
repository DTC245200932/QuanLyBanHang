package com.quanlybanhang.repository;

import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.Permission;
import com.quanlybanhang.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRole(ERole role);
    @Transactional
    void deleteByRole(ERole role);

    /** Delete and insert in one transaction so a failed insert preserves the old grants. */
    @Transactional
    default void replaceByRole(ERole role, Set<Permission> permissions) {
        deleteByRole(role);
        flush();
        saveAllAndFlush(permissions.stream()
                .map(permission -> RolePermission.builder().role(role).permission(permission).build())
                .toList());
    }

    @Transactional
    default void replaceMatrix(Map<ERole, Set<Permission>> matrix) {
        deleteAllInBatch();
        saveAllAndFlush(matrix.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(permission -> RolePermission.builder()
                                .role(entry.getKey()).permission(permission).build()))
                .toList());
    }
}
