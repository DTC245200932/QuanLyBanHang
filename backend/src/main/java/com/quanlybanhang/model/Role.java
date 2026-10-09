package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity Role - lưu trữ vai trò người dùng trong hệ thống.
 * Hỗ trợ 7 vai trò nghiệp vụ (xem ERole).
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, unique = true, nullable = false)
    private ERole name;

    public Role(ERole name) {
        this.name = name;
    }
}
