package com.quanlybanhang.exception;

/**
 * Ngoại lệ ném ra khi cố gắng tạo đơn hàng mới cho đại lý đã bị khoá giao dịch (S3-07).
 * Quy tắc: Đại lý bị khoá không tạo được đơn mới, kể cả trên cổng đặt hàng.
 */
public class DealerLockedException extends RuntimeException {

    public DealerLockedException(String message) {
        super(message);
    }

    public DealerLockedException(String code, String name, String reason) {
        super(String.format("Đại lý '%s' (%s) đã bị khoá giao dịch, không thể tạo đơn hàng mới. Lý do khoá: %s",
                name, code, reason != null && !reason.isBlank() ? reason : "Không nêu rõ"));
    }
}

