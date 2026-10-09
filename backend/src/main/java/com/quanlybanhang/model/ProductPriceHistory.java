package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_price_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "old_price", precision = 15, scale = 2, nullable = false, updatable = false)
    private BigDecimal oldPrice;

    @Column(name = "new_price", precision = 15, scale = 2, nullable = false, updatable = false)
    private BigDecimal newPrice;

    @Column(name = "modified_by", length = 100, nullable = false, updatable = false)
    private String modifiedBy;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @PrePersist
    protected void onCreate() {
        if (this.appliedAt == null) {
            this.appliedAt = LocalDateTime.now();
        }
    }
}
