package com.quanlybanhang.dto.request;

import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.model.DiscountTargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumeDiscountPolicyRequest {

    @NotBlank(message = "Mã chính sách chiết khấu không được để trống")
    @Size(max = 50, message = "Mã chính sách không được vượt quá 50 ký tự")
    private String code;

    @NotBlank(message = "Tên chính sách chiết khấu không được để trống")
    @Size(max = 255, message = "Tên chính sách không được vượt quá 255 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;

    @NotNull(message = "Đối tượng áp dụng không được để trống (PRODUCT hoặc CATEGORY)")
    private DiscountTargetType targetType;

    /**
     * Bắt buộc nếu targetType = PRODUCT.
     */
    private Long productId;

    /**
     * Bắt buộc nếu targetType = CATEGORY.
     */
    private Long categoryId;

    /**
     * Nhóm khách hàng áp dụng (tùy chọn: DAI_LY_CAP_1, DAI_LY_CAP_2, KHACH_LE).
     * Để trống nếu áp dụng cho toàn bộ khách hàng.
     */
    private CustomerGroup customerGroup;

    private LocalDate startDate;

    private LocalDate endDate;

    @Builder.Default
    private Boolean active = true;

    @NotEmpty(message = "Chính sách phải có ít nhất 1 bậc chiết khấu")
    @Valid
    private List<VolumeDiscountTierRequest> tiers;
}

