package com.quanlybanhang.security;

import com.quanlybanhang.model.User;
import com.quanlybanhang.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock JwtUtils jwtUtils;
    @Mock UserDetailsServiceImpl userDetailsService;
    @Mock TokenBlacklistService tokenBlacklistService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain filterChain;
    @InjectMocks JwtAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void openAccessTokenIsRejectedImmediatelyAfterAccountIsLocked() throws Exception {
        User lockedUser = User.builder()
                .id(15L)
                .username("sales01")
                .email("sales01@example.com")
                .password("encoded")
                .active(false)
                .tokenVersion(5L)
                .build();
        when(request.getHeader("Authorization")).thenReturn("Bearer open-access-token");
        when(jwtUtils.validateToken("open-access-token")).thenReturn(true);
        when(jwtUtils.getTokenType("open-access-token")).thenReturn("access");
        when(tokenBlacklistService.isBlacklisted("open-access-token")).thenReturn(false);
        when(jwtUtils.getUsernameFromToken("open-access-token")).thenReturn("sales01");
        when(userDetailsService.loadUserByUsername("sales01"))
                .thenReturn(UserDetailsImpl.build(lockedUser));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
