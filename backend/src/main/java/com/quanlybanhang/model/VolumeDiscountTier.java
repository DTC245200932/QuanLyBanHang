package com.quanlybanhang.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Entity Bậc chiết khấu theo sản lượng (S3-01).
 *
 * Mỗi bậc xác định:
 * - minQuantity: Số lượng mua tối thiểu (tính theo đơn vị cơ sở của SKU hoặc nhóm hàng).
 * - maxQuantity: Số lượng mua tối đa (null nếu không giới hạn trên).
 * - discountType: PERCENTAGE (%) hoặc FIXED_AMOUNT (số tiền trên một đơn vị cơ sở).
 * - discountValue: Giá trị chiết khấu tương ứng.
 */
@Entity
@Table(name = "volume_discount_tiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumeDiscountTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    @JsonIgnore
    private VolumeDiscountPolicy policy;

    /**
     * Số lượng mua tối thiểu để đạt bậc chiết khấu (tính theo đơn vị cơ sở).
     */
    @Column(name = "min_quantity", precision = 15, scale = 4, nullable = false)
    private BigDecimal minQuantity;

    /**
     * Số lượng mua tối đa của bậc (null nếu không giới hạn trên).
     */
    @Column(name = "max_quantity", precision = 15, scale = 4)
    private BigDecimal maxQuantity;

    /**
     * Loại chiết khấu: PERCENTAGE (%) hoặc FIXED_AMOUNT (tiền trên đơn vị).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private DiscountType discountType;

    /**
     * Giá trị chiết khấu:
     * - Nếu PERCENTAGE: % chiết khấu (vd: 5.0 = 5%, 10.0 = 10%).
     * - Nếu FIXED_AMOUNT: số tiền chiết khấu trên một đơn vị cơ sở (vd: 500 đ/lon).
     */
    @Column(name = "discount_value", precision = 15, scale = 2, nullable = false)
    private BigDecimal discountValue;

    @Column(name = "tier_order")
    @Builder.Default
    private Integer tierOrder = 1;
}

