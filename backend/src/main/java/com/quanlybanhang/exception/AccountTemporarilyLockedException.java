package com.quanlybanhang.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Exception khi tài khoản bị khoá tạm do đăng nhập sai quá nhiều lần.
 * Khác với LockedException (bị admin khoá vĩnh viễn).
 */
public class AccountTemporarilyLockedException extends AuthenticationException {

    private final long remainingMinutes;

    public AccountTemporarilyLockedException(long remainingMinutes) {
        super("Tài khoản bị khoá tạm thời do đăng nhập sai quá nhiều lần. "
                + "Vui lòng thử lại sau " + remainingMinutes + " phút.");
        this.remainingMinutes = remainingMinutes;
    }

    public long getRemainingMinutes() {
        return remainingMinutes;
    }
}
