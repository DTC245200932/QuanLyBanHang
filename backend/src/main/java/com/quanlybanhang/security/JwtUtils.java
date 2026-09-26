package com.quanlybanhang.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Utility class xử lý JWT: tạo token, validate, extract thông tin.
 */
@Component
public class JwtUtils {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(jwtSecret.getBytes()));
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Tạo Access Token từ authentication.
     */
    public String generateAccessToken(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return generateTokenFromUsername(userDetails.getUsername(), jwtExpirationMs);
    }

    /**
     * Tạo Refresh Token từ authentication.
     */
    public String generateRefreshToken(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return generateTokenFromUsername(userDetails.getUsername(), refreshExpirationMs);
    }

    /**
     * Tạo Access Token từ username (dùng cho refresh).
     */
    public String generateAccessTokenFromUsername(String username) {
        return generateTokenFromUsername(username, jwtExpirationMs);
    }

    private String generateTokenFromUsername(String username, long expirationMs) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Lấy username từ JWT token.
     */
    public String getUsernameFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Validate JWT token.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (MalformedJwtException e) {
            logger.error("JWT token không hợp lệ: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token đã hết hạn: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token không được hỗ trợ: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string rỗng: {}", e.getMessage());
        }
        return false;
    }
}
