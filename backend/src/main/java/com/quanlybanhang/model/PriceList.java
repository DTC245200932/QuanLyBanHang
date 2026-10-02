package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity Bảng giá (S2-10).
 * Cho phép Quản lý kinh doanh khai báo bảng giá theo nhóm khách hàng và thời gian hiệu lực.
 *
 * Quy tắc nghiệp vụ:
 * 1. Nhiều bảng giá song song (Đại lý cấp 1, Đại lý cấp 2, Khách lẻ).
 * 2. Mỗi bảng giá có ngày bắt đầu và ngày kết thúc hiệu lực.
 * 3. Bảng giá đã phát sinh đơn hàng thì KHÔNG SỬA, chỉ tạo phiên bản mới.
 */
@Entity
@Table(name = "price_lists", uniqueConstraints = {
        @UniqueConstraint(columnNames = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_group", nullable = false, length = 30)
    private CustomerGroup customerGroup;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(length = 500)
    private String description;

    /**
     * Bảng giá gốc mà phiên bản này kế thừa (khi tạo phiên bản mới).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_price_list_id")
    private PriceList parentPriceList;

    /**
     * Đánh dấu bảng giá đã phát sinh đơn hàng hay chưa.
     * Bảng giá đã phát sinh đơn thì KHÔNG sửa hoặc xóa, chỉ tạo phiên bản mới.
     */
    @Column(name = "has_orders", nullable = false)
    @Builder.Default
    private Boolean hasOrders = false;

    @OneToMany(mappedBy = "priceList", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PriceListItem> items = new ArrayList<>();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addItem(PriceListItem item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.add(item);
        item.setPriceList(this);
    }

    public void removeItem(PriceListItem item) {
        if (this.items != null) {
            this.items.remove(item);
            item.setPriceList(null);
        }
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.version == null) {
            this.version = 1;
        }
        if (this.active == null) {
            this.active = true;
        }
        if (this.hasOrders == null) {
            this.hasOrders = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
