package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Phiếu ghi nhận thu tiền / thanh toán công nợ của đại lý (S7-04).
 * Sau mỗi lần ghi nhận thu tiền, hệ thống tự động chạy lại tác vụ đánh giá công nợ.
 */
@Entity
@Table(name = "debt_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã phiếu thu tiền - duy nhất (VD: PT-20261022-0001) */
    @Column(name = "payment_code", nullable = false, unique = true, length = 50)
    private String paymentCode;

    /** Đại lý thanh toán nợ */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dealer_id", nullable = false)
    private Dealer dealer;

    /** Số tiền thu nợ (bắt buộc > 0) */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Ngày thu tiền */
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /** Phương thức thanh toán (TIEN_MAT, CHUYEN_KHOAN, KHAC) */
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    /** Mã tham chiếu / số uỷ nhiệm chi / sao kê ngân hàng */
    @Column(name = "reference_code", length = 100)
    private String referenceCode;

    /** Ghi chú nội dung thu tiền */
    @Column(length = 500)
    private String note;

    /** Người thực hiện ghi nhận */
    @Column(name = "recorded_by", length = 100)
    private String recordedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (paymentDate == null) {
            paymentDate = LocalDate.now();
        }
    }
}

