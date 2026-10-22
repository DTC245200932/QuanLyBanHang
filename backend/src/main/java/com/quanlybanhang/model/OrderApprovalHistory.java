package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity lưu lịch sử duyệt đơn hàng (S4-05).
 * Bắt buộc bất biến (immutable): lịch sử duyệt không thể sửa hoặc xoá.
 */
@Entity
@Table(name = "order_approval_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderApprovalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "order_code", length = 50, nullable = false)
    private String orderCode;

    /**
     * Hành động duyệt: PENDING_APPROVAL, APPROVED, REJECTED, RETURNED
     */
    @Column(length = 30, nullable = false)
    private String action;

    /**
     * Ý kiến / lý do của người duyệt (bắt buộc khi REJECTED hoặc RETURNED).
     */
    @Column(length = 1000)
    private String comment;

    @Column(name = "performed_by", length = 100, nullable = false)
    private String performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void onUpdate() {
        throw new UnsupportedOperationException("Lịch sử duyệt đơn hàng không được phép chỉnh sửa");
    }

    @PreRemove
    public void onDelete() {
        throw new UnsupportedOperationException("Lịch sử duyệt đơn hàng không được phép xoá");
    }
}
