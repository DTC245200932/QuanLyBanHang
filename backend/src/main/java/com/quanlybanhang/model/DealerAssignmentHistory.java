package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity lưu lịch sử phân công và chuyển giao địa bàn/đại lý (S3-06).
 * Ghi nhận mỗi lần thay đổi nhân viên phụ trách chính của đại lý
 * (cả phân công riêng lẻ và chuyển giao hàng loạt khi nhân viên nghỉ việc).
 */
@Entity
@Table(name = "dealer_assignment_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealerAssignmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dealer_id", nullable = false)
    private Dealer dealer;

    @Column(name = "dealer_code", nullable = false, length = 50)
    private String dealerCode;

    @Column(name = "dealer_name", nullable = false, length = 150)
    private String dealerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id")
    private User fromUser;

    @Column(name = "from_user_name", length = 100)
    private String fromUserName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @Column(name = "to_user_name", nullable = false, length = 100)
    private String toUserName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "territory_id")
    private Territory territory;

    @Column(name = "territory_name", length = 120)
    private String territoryName;

    /**
     * Loại chuyển giao: BULK_HANDOVER (chuyển giao hàng loạt) hoặc INDIVIDUAL_ASSIGNMENT (phân công đơn lẻ).
     */
    @Column(name = "transfer_type", nullable = false, length = 30)
    private String transferType;

    /**
     * Lý do chuyển giao (VD: Nhân viên nghỉ việc, Điều chuyển địa bàn, v.v.).
     */
    @Column(length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id")
    private User performedBy;

    @Column(name = "performed_by_name", length = 100)
    private String performedByName;

    @Column(name = "transferred_at", nullable = false)
    private LocalDateTime transferredAt;

    @PrePersist
    protected void onCreate() {
        if (transferredAt == null) {
            transferredAt = LocalDateTime.now();
        }
    }
}

