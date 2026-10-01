package com.quanlybanhang.repository;

import com.quanlybanhang.model.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long> {

    /**
     * Kiểm tra token có nằm trong blacklist không.
     */
    boolean existsByToken(String token);

    /**
     * Tìm tất cả token bị blacklist của một username.
     */
    List<TokenBlacklist> findByUsername(String username);

    /**
     * Xoá các token đã hết hạn tự nhiên (cleanup).
     */
    @Modifying
    @Query("DELETE FROM TokenBlacklist t WHERE t.expiresAt < :now")
    int deleteExpiredTokens(LocalDateTime now);
}
