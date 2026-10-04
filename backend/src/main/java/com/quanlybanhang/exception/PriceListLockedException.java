package com.quanlybanhang.exception;

/**
 * Ngoại lệ ném ra khi cố gắng sửa đổi hoặc xóa bảng giá đã phát sinh đơn hàng (S2-10).
 * Bảng giá đã phát sinh đơn hàng thì không được sửa, chỉ tạo phiên bản mới.
 */
public class PriceListLockedException extends RuntimeException {

    public PriceListLockedException(String message) {
        super(message);
    }
}
