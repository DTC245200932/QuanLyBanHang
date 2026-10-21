package com.quanlybanhang.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Tài khoản đại lý chỉ sử dụng dữ liệu của mình qua API cổng. */
public class DealerApiScopeFilter extends OncePerRequestFilter {
    private final RestAccessDeniedHandler accessDeniedHandler;

    public DealerApiScopeFilter(RestAccessDeniedHandler accessDeniedHandler) {
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean dealer = auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_DEALER".equals(authority.getAuthority()));
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        boolean internalRole = auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().startsWith("ROLE_")
                        && !"ROLE_DEALER".equals(authority.getAuthority()));
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean ownApi = path.startsWith("/api/portal/") || path.startsWith("/api/auth/")
                || path.startsWith("/api/users/") || path.startsWith("/api/products/images/");
        if (dealer && !admin && !internalRole && path.startsWith("/api/") && !ownApi) {
            accessDeniedHandler.handle(request, response,
                    new AccessDeniedException("Vui lòng sử dụng cổng đại lý để truy cập dữ liệu của mình"));
            return;
        }
        chain.doFilter(request, response);
    }
}
