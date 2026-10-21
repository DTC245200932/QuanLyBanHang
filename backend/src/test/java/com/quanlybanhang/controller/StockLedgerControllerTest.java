package com.quanlybanhang.controller;

import com.quanlybanhang.config.SecurityConfig;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import com.quanlybanhang.security.*;
import com.quanlybanhang.service.StockLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockLedgerController.class)
@Import({SecurityConfig.class, RestAccessDeniedHandler.class, AuthEntryPointJwt.class, GlobalExceptionHandler.class})
class StockLedgerControllerTest {
    @Autowired MockMvc mvc;
    @MockBean StockLedgerService service;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @BeforeEach
    void passThrough() throws Exception {
        doAnswer(i -> { ((jakarta.servlet.FilterChain)i.getArgument(2)).doFilter(i.getArgument(0), i.getArgument(1)); return null; })
                .when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }
    @ParameterizedTest
    @ValueSource(strings = {"", "/options"})
    void viewPermissionNotRoleOrOtherWarehousePermissionsControlsBothEndpoints(String suffix) throws Exception {
        String path = "/api/warehouse/stock-ledger" + suffix;
        mvc.perform(get(path).with(user("reader").authorities(new SimpleGrantedAuthority("ROLE_VIEWER"),
                new SimpleGrantedAuthority("WAREHOUSE_VIEW")))).andExpect(status().isOk());
        clearInvocations(service);
        mvc.perform(get(path).with(user("reader").authorities(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER"),
                new SimpleGrantedAuthority("WAREHOUSE_IMPORT"), new SimpleGrantedAuthority("WAREHOUSE_MANAGE"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
    @Test
    void unrecognizedStatusAndMalformedIdsAreRejectedBeforeService() throws Exception {
        mvc.perform(get("/api/warehouse/stock-ledger").with(user("admin").roles("ADMIN"))
                .param("stockStatus", "INVENTED")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/warehouse/stock-ledger").with(user("admin").roles("ADMIN"))
                .param("warehouseId", "invalid")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
