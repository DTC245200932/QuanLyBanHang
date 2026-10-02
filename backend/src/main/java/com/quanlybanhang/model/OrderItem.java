package com.quanlybanhang.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Chi tiết dòng đơn hàng (S2-07).
 *
 * Lưu giữ giá trị đóng băng của đơn vị tính, hệ số quy đổi và số lượng cơ sở
 * tại thời điểm ghi sổ đơn hàng.
 * Đổi hệ số quy đổi sau này KHÔNG làm thay đổi số liệu trong bản ghi này!
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Đơn vị đặt hàng khách chọn hoặc nhân viên nhập (vd: "thùng", "lốc", "lon").
     */
    @Column(nullable = false, length = 50)
    private String unit;

    /**
     * Số lượng theo đơn vị đã chọn (vd: 5 thùng).
     */
    @Column(precision = 15, scale = 4, nullable = false)
    private BigDecimal quantity;

    /**
     * Hệ số quy đổi về đơn vị cơ sở ĐÓNG BĂNG tại thời điểm ghi sổ (vd: 24.0).
     */
    @Column(name = "conversion_factor", precision = 12, scale = 4, nullable = false)
    private BigDecimal conversionFactor;

    /**
     * Đơn vị cơ sở ĐÓNG BĂNG tại thời điểm ghi sổ (vd: "lon").
     */
    @Column(name = "base_unit", nullable = false, length = 50)
    private String baseUnit;

    /**
     * Số lượng đã quy đổi về đơn vị cơ sở khi ghi sổ (vd: 120 lon = 5 * 24.0).
     */
    @Column(name = "base_quantity", precision = 15, scale = 4, nullable = false)
    private BigDecimal baseQuantity;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    /**
     * Giá sàn theo đơn vị đặt hàng tại thời điểm tạo đơn (S2-10).
     */
    @Column(name = "floor_price", precision = 15, scale = 2)
    private BigDecimal floorPrice;

    /**
     * Đánh dấu dòng sản phẩm này bán dưới giá sàn.
     */
    @Column(name = "below_floor_price", nullable = false)
    @Builder.Default
    private Boolean belowFloorPrice = false;

    @Column(name = "total_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(length = 255)
    private String note;
}
