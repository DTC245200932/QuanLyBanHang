package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bản nháp tạo đơn của từng người dùng (S3-09).
 * Lưu riêng để không làm thay đổi trạng thái hoặc số liệu của đơn hàng thật.
 */
@Entity
@Table(name = "order_drafts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_draft_username", columnNames = "username")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String username;

    @Lob
    @Column(name = "draft_data", nullable = false, columnDefinition = "LONGTEXT")
    private String draftData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
