package com.quanlybanhang.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Kết quả thẻ kho đầy đủ cho một sản phẩm (S6-02).
 *
 * <p>Chứa thông tin định danh sản phẩm và danh sách phát sinh theo thứ tự thời gian
 * kèm tồn tích lũy sau mỗi sự kiện, với phân trang phía server.
 */
public record StockCardResponse(
        /** ID sản phẩm. */
        Long productId,

        /** Mã SKU sản phẩm. */
        String productCode,

        /** Tên sản phẩm. */
        String productName,

        /** Đơn vị cơ sở hiện tại của sản phẩm. */
        String baseUnit,

        /** Danh sách phát sinh trong trang hiện tại theo thứ tự thời gian. */
        List<StockCardEntryResponse> entries,

        /** Tổng số dòng phát sinh (toàn bộ, không phân trang). */
        long totalElements,

        /** Tổng số trang. */
        int totalPages,

        /** Trang hiện tại (0-based). */
        int currentPage,

        /** Số dòng mỗi trang. */
        int pageSize,

        /**
         * Tồn kho (đơn vị cơ sở) trước dòng đầu tiên của trang này
         * — dùng để frontend hiển thị số dư liên tục khi phân trang.
         * Null nếu đây là trang đầu (balance trước trang = 0 hoặc chưa có phát sinh).
         */
        BigDecimal openingBalance,

        /** Thời điểm của bộ lọc từ (bao gồm). */
        LocalDateTime fromDate,

        /** Thời điểm của bộ lọc đến (bao gồm). */
        LocalDateTime toDate
) {
}
