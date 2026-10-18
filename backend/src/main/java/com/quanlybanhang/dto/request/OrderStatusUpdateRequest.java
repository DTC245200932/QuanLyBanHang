package com.quanlybanhang.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request cập nhật trạng thái vòng đời đơn hàng (S4-06).
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

    private String note;
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
