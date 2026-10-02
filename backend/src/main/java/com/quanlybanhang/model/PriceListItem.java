package com.quanlybanhang.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Chi tiết dòng bảng giá (S2-10).
 * Mỗi dòng có giá bán và giá sàn — bán dưới giá sàn sẽ phải qua Quản lý kinh doanh duyệt.
 */
@Entity
@Table(name = "price_list_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"price_list_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_list_id", nullable = false)
    @JsonIgnore
    private PriceList priceList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Giá bán theo bảng giá.
     */
    @Column(name = "selling_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal sellingPrice;

    /**
     * Giá sàn theo bảng giá.
     * Bán dưới giá sàn sẽ phải qua Quản lý kinh doanh phê duyệt.
     */
    @Column(name = "floor_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal floorPrice;

    @Column(length = 255)
    private String note;
}
