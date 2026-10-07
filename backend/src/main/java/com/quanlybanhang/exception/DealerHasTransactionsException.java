package com.quanlybanhang.exception;

/**
 * Ngoại lệ ném ra khi cố gắng xóa đại lý đã phát sinh giao dịch (S3-03).
 * Quy tắc: Đại lý đã phát sinh giao dịch thì không xoá được, chỉ ngừng giao dịch.
 */
public class DealerHasTransactionsException extends RuntimeException {

    public DealerHasTransactionsException(String message) {
        super(message);
    }

    public DealerHasTransactionsException(String code, String name) {
        super(String.format("Đại lý '%s' (%s) đã phát sinh giao dịch không thể xoá, chỉ có thể chuyển sang trạng thái ngừng giao dịch.",
                name, code));
    }
}

