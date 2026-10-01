package com.quanlybanhang.exception;

/**
 * Ngoại lệ ném ra khi cố gắng xóa sản phẩm đã phát sinh giao dịch (S2-05).
 * Quy tắc: Sản phẩm đã phát sinh giao dịch thì không xoá được, chỉ ngừng kinh doanh.
 */
public class ProductHasTransactionsException extends RuntimeException {

    public ProductHasTransactionsException(String message) {
        super(message);
    }

    public ProductHasTransactionsException(String sku, String productName) {
        super(String.format("Sản phẩm '%s' (%s) đã phát sinh giao dịch không thể xoá, chỉ có thể chuyển sang trạng thái ngừng kinh doanh.",
                productName, sku));
    }
}

