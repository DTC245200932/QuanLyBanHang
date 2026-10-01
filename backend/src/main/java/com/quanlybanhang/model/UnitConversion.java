package com.quanlybanhang.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity UnitConversion - Đơn vị tính quy đổi của SKU (S2-07).
 *
 * Mỗi SKU có một đơn vị cơ sở (Product.unit, vd: "lon").
 * SKU có thể khai báo nhiều đơn vị quy đổi khác (vd: "lốc", "thùng")
 * kèm hệ số quy đổi về đơn vị cơ sở (vd: 1 lốc = 6 lon, 1 thùng = 24 lon).
 */
@Entity
@Table(name = "product_unit_conversions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_unit_name", columnNames = {"product_id", "unit_name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitConversion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    /**
     * Tên đơn vị quy đổi: lon, lốc, thùng, két, hộp...
     */
    @Column(name = "unit_name", nullable = false, length = 50)
    private String unitName;

    /**
     * Hệ số quy đổi về đơn vị cơ sở (luôn > 0).
     * Ví dụ: đơn vị cơ sở là "lon", nếu đơn vị quy đổi là "thùng" (24 lon) thì conversionFactor = 24.0.
     * Số lượng cơ sở = số lượng theo đơn vị này * conversionFactor.
     */
    @Column(name = "conversion_factor", precision = 12, scale = 4, nullable = false)
    private BigDecimal conversionFactor;

    /**
     * Đánh dấu có phải là đơn vị cơ sở hay không (hệ số = 1.0).
     */
    @Column(name = "is_base_unit")
    @Builder.Default
    private Boolean isBaseUnit = false;

    /**
     * Giá bán riêng cho đơn vị này (nếu có).
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal price;

    /**
     * Giá vốn riêng cho đơn vị này (nếu có).
     */
    @Column(name = "cost_price", precision = 15, scale = 2)
    private BigDecimal costPrice;

    /**
     * Mã vạch cho đơn vị quy đổi (vd mã vạch thùng khác mã vạch lon).
     */
    @Column(length = 50)
    private String barcode;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.isBaseUnit == null) {
            this.isBaseUnit = false;
        }
        if (this.conversionFactor == null) {
            this.conversionFactor = BigDecimal.ONE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
