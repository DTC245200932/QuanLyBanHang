package com.quanlybanhang.dto.request;

import jakarta.validation.Valid;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Yêu cầu tạo phiên bản mới của bảng giá (S2-10).
 * Bảng giá đã phát sinh đơn hàng thì không sửa, chỉ tạo phiên bản mới.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceListNewVersionRequest {

    /**
     * Mã phiên bản mới (nếu để trống, hệ thống tự sinh theo định dạng: {oldCode}-V{nextVersion}).
     */
    private String newCode;

    /**
     * Tên phiên bản mới (nếu để trống, tự sinh theo định dạng: {oldName} (v{nextVersion})).
     */
    private String newName;

    /**
     * Ngày bắt đầu hiệu lực mới (mặc định lấy từ bảng giá cũ hoặc hôm nay).
     */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /**
     * Ngày kết thúc hiệu lực mới (mặc định lấy từ bảng giá cũ).
     */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private String description;

    /**
     * Danh sách dòng giá mới (nếu để trống, kế thừa toàn bộ dòng giá từ phiên bản trước).
     */
    @Valid
    private List<PriceListItemRequest> items;
}
