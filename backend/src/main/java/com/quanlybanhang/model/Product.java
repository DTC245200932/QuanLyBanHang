package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity Product - Quản lý sản phẩm.
 * Thuộc về một nhóm hàng (Category). Có thể chuyển đổi giữa các nhóm hàng.
 */
@Entity
@Table(name = "products", uniqueConstraints = {
        @UniqueConstraint(columnNames = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1000)
    private String description;

    /**
     * Nhóm hàng của sản phẩm.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * Giá bán của sản phẩm.
     */
    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    /**
     * Giá vốn của sản phẩm (bảo mật theo S1-05).
     */
    @Column(name = "cost_price", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO;

    /**
     * Doanh số tích lũy bán hàng của sản phẩm.
     * Phục vụ tính doanh số theo ngành hàng / nhóm hàng (S2-06).
     */
    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal revenue = BigDecimal.ZERO;

    /**
     * Đơn vị tính: chiếc, cái, hộp, kg...
     */
    @Column(length = 50)
    @Builder.Default
    private String unit = "cái";

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.active == null) {
            this.active = true;
        }
        if (this.price == null) {
            this.price = BigDecimal.ZERO;
        }
        if (this.costPrice == null) {
            this.costPrice = BigDecimal.ZERO;
        }
        if (this.revenue == null) {
            this.revenue = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
