package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A configured minimum, distinct from a missing setting and from physical inventory. */
@Entity
@Table(name = "warehouse_stock_minimums", uniqueConstraints =
        @UniqueConstraint(name = "uk_stock_minimum_warehouse_product", columnNames = {"warehouse_id", "product_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockMinimum {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "warehouse_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Warehouse warehouse;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product product;
    @Column(name = "minimum_quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal minimumQuantity;
    @Column(name = "base_unit", nullable = false, length = 50)
    private String baseUnit;
    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
