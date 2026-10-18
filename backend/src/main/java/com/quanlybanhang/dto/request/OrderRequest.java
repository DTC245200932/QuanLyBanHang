package com.quanlybanhang.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {

    private String orderCode;

    private String customerName;

    /**
     * Nhóm khách hàng (DAI_LY_CAP_1, DAI_LY_CAP_2, KHACH_LE).
     * Dùng để tự động áp bảng giá tương ứng (S2-10).
     */
    private com.quanlybanhang.model.CustomerGroup customerGroup;

    /**
     * ID đại lý (nếu đặt cho đại lý cụ thể, hệ thống tự lấy customerGroup của đại lý đó).
     */
    private Long dealerId;

    /**
     * ID điểm giao hàng của đại lý (S3-04).
     */
    private Long deliveryPointId;

    /**
     * ID kho phục vụ đại lý / xuất hàng (S4-03).
     */
    private Long warehouseId;

    /** Ngày giao mong muốn do nhân viên kinh doanh chọn khi tạo đơn (S3-09). */
    private LocalDate expectedDeliveryDate;

    /**
     * Chỉ định rõ ID bảng giá (nếu không truyền, hệ thống tự động tìm bảng giá hiệu lực).
     */
    private Long priceListId;


    /**
     * Số giờ giữ chỗ tồn kho mong muốn (S5-06, mặc định 48h nếu không chỉ định).
     */
    private Integer reservationHours;

    /**
     * Thời điểm hết hạn giữ chỗ tuỳ chỉnh (S5-06).
     */
    private LocalDateTime reservationExpiresAt;

    private String note;

    @NotEmpty(message = "Đơn hàng phải có ít nhất 1 sản phẩm")
    @Valid
    private List<OrderItemRequest> items;
}
