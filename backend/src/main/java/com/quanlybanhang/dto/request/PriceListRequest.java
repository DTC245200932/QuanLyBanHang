package com.quanlybanhang.dto.request;

import com.quanlybanhang.model.CustomerGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceListRequest {

    @NotBlank(message = "Mã bảng giá không được để trống")
    private String code;

    @NotBlank(message = "Tên bảng giá không được để trống")
    private String name;

    @NotNull(message = "Nhóm khách hàng không được để trống")
    private CustomerGroup customerGroup;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "Ngày bắt đầu hiệu lực không được để trống")
    private LocalDate startDate;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "Ngày kết thúc hiệu lực không được để trống")
    private LocalDate endDate;

    @Builder.Default
    private Boolean active = true;

    private String description;

    @Valid
    private List<PriceListItemRequest> items;
}
