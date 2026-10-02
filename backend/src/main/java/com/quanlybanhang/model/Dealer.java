package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;

/** Đại lý được giao cho nhân viên kinh doanh; dùng để theo dõi nhu cầu bàn giao. */
@Entity
@Table(name = "dealers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dealer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @Column(nullable = false)
    @Builder.Default
    private Boolean handoverRequired = false;

    /**
     * Nhóm khách hàng của đại lý (mặc định là Đại lý cấp 1 - DAI_LY_CAP_1).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_group", length = 30)
    @Builder.Default
    private CustomerGroup customerGroup = CustomerGroup.DAI_LY_CAP_1;
}
