package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Đại lý / Khách hàng (S3-03).
 * Quản lý hồ sơ đại lý chuẩn gồm mã đại lý (duy nhất), tên, mã số thuế,
 * nhóm khách hàng, khu vực, người phụ trách, trạng thái hoạt động.
 */
@Entity
@Table(name = "dealers", uniqueConstraints = {
        @UniqueConstraint(columnNames = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dealer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã đại lý - bắt buộc, duy nhất (S3-03). */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    /** Tên đại lý - bắt buộc (S3-03). */
    @Column(nullable = false, length = 150)
    private String name;

    /** Mã số thuế của đại lý (S3-03). */
    @Column(name = "tax_code", length = 50)
    private String taxCode;

    /** Người phụ trách đại lý (nhân viên kinh doanh) (S3-03). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    /** Khu vực / địa bàn của đại lý (S3-03). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "territory_id")
    private Territory territory;

    /** Trạng thái giao dịch: true = Đang giao dịch, false = Ngừng giao dịch (S3-03). */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean handoverRequired = false;

    /** Hạn mức công nợ tối đa được cấp cho đại lý. */
    @Column(name = "debt_limit", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal debtLimit = BigDecimal.ZERO;

    /**
     * Nhóm khách hàng của đại lý (mặc định là Đại lý cấp 1 - DAI_LY_CAP_1).
     * Quyết định bảng giá được áp dụng (S3-03, S2-10).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_group", length = 30)
    @Builder.Default
    private CustomerGroup customerGroup = CustomerGroup.DAI_LY_CAP_1;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(length = 255)
    private String address;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
        if (debtLimit == null) {
            debtLimit = BigDecimal.ZERO;
        }
        if (handoverRequired == null) {
            handoverRequired = false;
        }
        if (customerGroup == null) {
            customerGroup = CustomerGroup.DAI_LY_CAP_1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
