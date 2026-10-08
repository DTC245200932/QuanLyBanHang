package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request chuyển giao địa bàn và đại lý hàng loạt khi nhân viên nghỉ việc (S3-06).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkHandoverRequest {

    @NotNull(message = "Nhân viên bàn giao không được để trống")
    private Long fromUserId;

    @NotNull(message = "Nhân viên tiếp nhận không được để trống")
    private Long toUserId;

    /** Tùy chọn lọc theo địa bàn/khu vực cần chuyển giao */
    private Long territoryId;

    /** Tùy chọn danh sách đại lý cụ thể cần chuyển giao. Nếu để trống -> chuyển toàn bộ */
    private List<Long> dealerIds;

    @NotBlank(message = "Lý do chuyển giao không được để trống")
    @Size(max = 500, message = "Lý do chuyển giao tối đa 500 ký tự")
    private String reason;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String notes;
}

