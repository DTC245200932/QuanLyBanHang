package com.quanlybanhang.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity Chính sách chiết khấu theo sản lượng (S3-01).
 * Cho phép Quản lý kinh doanh khai báo chính sách chiết khấu theo sản lượng
 * theo một SKU hoặc một nhóm hàng, với các bậc sản lượng chiết khấu theo % hoặc số tiền/đơn vị.
 *
 * Quy tắc nghiệp vụ cốt lõi:
 * 1. Bậc chiết khấu theo số lượng mua của một SKU hoặc một nhóm hàng.
 * 2. Chiết khấu tính theo phần trăm hoặc theo số tiền trên đơn vị cơ sở.
 * 3. Khi nhiều chính sách cùng áp dụng, hệ thống tự động chọn chính sách có lợi nhất cho khách
 *    (mức chiết khấu bằng tiền là cao nhất).
 */
@Entity
@Table(name = "volume_discount_policies", uniqueConstraints = {
        @UniqueConstraint(columnNames = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumeDiscountPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private DiscountTargetType targetType;

    /**
     * Sản phẩm cụ thể khi targetType = PRODUCT.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /**
     * Nhóm hàng khi targetType = CATEGORY.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * Nhóm khách hàng áp dụng (DAI_LY_CAP_1, DAI_LY_CAP_2, KHACH_LE).
     * Null = Áp dụng cho tất cả nhóm khách hàng.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_group", length = 30)
    private CustomerGroup customerGroup;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("minQuantity ASC")
    @Builder.Default
    private List<VolumeDiscountTier> tiers = new ArrayList<>();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addTier(VolumeDiscountTier tier) {
        if (this.tiers == null) {
            this.tiers = new ArrayList<>();
        }
        this.tiers.add(tier);
        tier.setPolicy(this);
    }

    public void removeTier(VolumeDiscountTier tier) {
        if (this.tiers != null) {
            this.tiers.remove(tier);
            tier.setPolicy(null);
        }
    }

    public void clearTiers() {
        if (this.tiers != null) {
            for (VolumeDiscountTier tier : this.tiers) {
                tier.setPolicy(null);
            }
            this.tiers.clear();
        }
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.active == null) {
            this.active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

