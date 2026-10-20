package com.quanlybanhang.repository;

import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.RolePermissionState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionStateRepository extends JpaRepository<RolePermissionState, ERole> {
}
