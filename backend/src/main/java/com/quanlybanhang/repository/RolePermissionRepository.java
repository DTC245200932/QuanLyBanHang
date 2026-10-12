package com.quanlybanhang.repository;

import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRole(ERole role);
    @Transactional
    void deleteByRole(ERole role);
}
