package com.quanlybanhang.dto.request;

import com.quanlybanhang.model.CustomerGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumeDiscountCalculateRequest {

    private CustomerGroup customerGroup;

    private Long dealerId;

    private LocalDate orderDate;

    @NotEmpty(message = "Danh sách sản phẩm không được để trống")
    @Valid
    private List<VolumeDiscountCalculateItemRequest> items;
}

