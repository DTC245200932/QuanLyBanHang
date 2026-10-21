package com.quanlybanhang.security;

import com.quanlybanhang.config.RolePermissionConfig;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.UserRepository;
import com.quanlybanhang.service.TokenBlacklistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PermissionRevocationTest {
    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void sameAccessTokenUsesNewMatrixOnTheNextRequest() throws Exception {
        RolePermissionConfig matrix = new RolePermissionConfig();
        UserRepository users = mock(UserRepository.class);
        User user = User.builder().id(1L).username("staff").password("unused").active(true)
                .roles(Set.of(Role.builder().name(ERole.ROLE_WAREHOUSE_STAFF).build())).build();
        when(users.findByUsername("staff")).thenReturn(Optional.of(user));
        JwtUtils jwt = mock(JwtUtils.class);
        when(jwt.validateToken("same-token")).thenReturn(true);
        when(jwt.getTokenType("same-token")).thenReturn("access");
        when(jwt.getUsernameFromToken("same-token")).thenReturn("staff");
        when(jwt.getTokenVersion("same-token")).thenReturn(0L);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt,
                new UserDetailsServiceImpl(users, matrix), mock(TokenBlacklistService.class));
        List<Boolean> results = new ArrayList<>();
        for (boolean grant : List.of(true, false, true)) {
            matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_STAFF,
                    grant ? Set.of(Permission.WAREHOUSE_EXPORT) : Set.of());
            SecurityContextHolder.clearContext();
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/warehouse/export");
            request.addHeader("Authorization", "Bearer same-token");
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                    results.add(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                            .stream().anyMatch(authority -> "WAREHOUSE_EXPORT".equals(authority.getAuthority()))));
        }
        assertThat(results).containsExactly(true, false, true);
    }
}
