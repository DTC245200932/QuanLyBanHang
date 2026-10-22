package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity Phiếu kiểm kê kho (S5-08).
 * Thực hiện kiểm kê kho, chốt tồn sổ sách tại thời điểm tạo,
 * nhập số đếm thực tế, tính chênh lệch và chốt điều chỉnh tồn về số thực tế.
 */
@Entity
@Table(name = "warehouse_stock_counts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_count_code", columnNames = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseStockCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /**
     * Nhóm hàng kiểm kê. Nếu null là kiểm kê toàn kho.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private StockCountStatus status = StockCountStatus.DRAFT;

    @Column(name = "note", length = 500)
    private String note;

    /**
     * Lý do điều chỉnh tồn kho khi chốt kiểm kê (bắt buộc khi chốt).
     */
    @Column(name = "adjustment_reason", length = 500)
    private String adjustmentReason;

    /**
     * Lý do hủy phiếu kiểm kê.
     */
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_by", length = 100)
    private String completedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_by", length = 100)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "stockCount", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WarehouseStockCountItem> items = new ArrayList<>();

    public void addItem(WarehouseStockCountItem item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.add(item);
        item.setStockCount(this);
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = StockCountStatus.DRAFT;
        }
    }
}
