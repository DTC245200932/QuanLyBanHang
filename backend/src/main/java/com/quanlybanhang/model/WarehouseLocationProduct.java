package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity Gán sản phẩm vào vị trí lưu trong kho (S5-03).
 * Xác định chính xác hàng nằm ở kệ hoặc khu nào khi soạn đơn.
 */
@Entity
@Table(name = "warehouse_location_products", uniqueConstraints = {
        @UniqueConstraint(name = "uk_location_product", columnNames = {"location_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseLocationProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private WarehouseLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Đánh dấu là vị trí lưu mặc định của sản phẩm trong kho này */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = true;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isDefault == null) {
            isDefault = true;
        }
    }
}
