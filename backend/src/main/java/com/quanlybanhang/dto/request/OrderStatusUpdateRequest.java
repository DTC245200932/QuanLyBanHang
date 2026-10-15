package com.quanlybanhang.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request DTO cập nhật trạng thái duyệt đơn hàng (S4-05).
 * status: APPROVED, REJECTED, RETURNED
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderStatusUpdateRequest {

    @NotBlank(message = "Trạng thái không được để trống")
    private String status;

    private String comment;
    private String reason;

    @JsonIgnore
    public String getEffectiveComment() {
        if (comment != null && !comment.isBlank()) {
            return comment.trim();
        }
        if (reason != null && !reason.isBlank()) {
            return reason.trim();
        }
        return null;
    }
}
