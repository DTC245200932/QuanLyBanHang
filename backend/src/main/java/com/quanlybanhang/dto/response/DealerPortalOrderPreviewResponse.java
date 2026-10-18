package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả xem trước đơn hàng trên cổng đại lý (S4-10).
 * Không trả giá sàn, mã chính sách hoặc dữ liệu nội bộ của hệ thống.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealerPortalOrderPreviewResponse {

    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;

    @Builder.Default
    private List<DealerPortalOrderPreviewItemResponse> items = new ArrayList<>();
}
