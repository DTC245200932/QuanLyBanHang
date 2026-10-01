package com.quanlybanhang.exception;

/**
 * Exception ném ra khi cố gắng xoá nhóm hàng nhưng nhóm vẫn còn chứa sản phẩm
 * hoặc các nhóm con vẫn còn chứa sản phẩm (S2-06).
 */
public class CategoryNotEmptyException extends RuntimeException {

    public CategoryNotEmptyException(String message) {
        super(message);
    }
}
