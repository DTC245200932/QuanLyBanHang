package com.quanlybanhang.exception;

import com.quanlybanhang.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Xử lý exception toàn cục - trả về JSON response nhất quán cho frontend.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Xử lý lỗi đăng nhập sai mật khẩu/username.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Tên đăng nhập hoặc mật khẩu không đúng"));
    }

    /**
     * Xử lý tài khoản bị khóa.
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse> handleLocked(LockedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên."));
    }

    /**
     * Xử lý tài khoản bị vô hiệu hóa.
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse> handleDisabled(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Tài khoản đã bị vô hiệu hóa."));
    }

    /**
     * Xử lý lỗi validation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity.badRequest()
                .body(ApiResponse.builder()
                        .success(false)
                        .message("Dữ liệu không hợp lệ")
                        .data(errors)
                        .build());
    }

    /**
     * Xử lý các RuntimeException chung.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Xử lý tất cả exception không bắt được.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau."));
    }
}
