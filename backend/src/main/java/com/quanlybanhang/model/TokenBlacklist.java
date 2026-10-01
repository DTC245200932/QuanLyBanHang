package com.quanlybanhang.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entity lưu JWT tokens đã bị thu hồi (blacklist).
 * Dùng cho: đăng xuất, đổi mật khẩu, khoá tài khoản.
 *
 * Token được lưu cho đến khi hết hạn tự nhiên, sau đó có thể xoá bằng scheduled job.
 */
@Entity
@Table(name = "token_blacklist",
       indexes = @Index(name = "idx_token", columnList = "token"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * JWT token đã bị thu hồi (lưu full token string).
     */
    @Column(nullable = false, length = 500, unique = true)
    private String token;

    /**
     * Username sở hữu token.
     */
    @Column(nullable = false, length = 50)
    private String username;

    /**
     * Lý do thu hồi: LOGOUT, PASSWORD_CHANGE, ACCOUNT_LOCK, ADMIN_REVOKE.
     */
    @Column(nullable = false, length = 30)
    private String reason;

    /**
     * Thời điểm token hết hạn tự nhiên (dùng để cleanup).
     */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Thời điểm token bị blacklist.
     */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime blacklistedAt = LocalDateTime.now();
}
