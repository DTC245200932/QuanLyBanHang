package com.quanlybanhang.controller;

import com.quanlybanhang.config.SecurityConfig;
import com.quanlybanhang.dto.response.SupplierResponse;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import com.quanlybanhang.security.AuthEntryPointJwt;
import com.quanlybanhang.security.JwtAuthenticationFilter;
import com.quanlybanhang.security.RestAccessDeniedHandler;
import com.quanlybanhang.security.UserDetailsServiceImpl;
import com.quanlybanhang.service.SupplierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SupplierController.class)
@Import({SecurityConfig.class, RestAccessDeniedHandler.class, AuthEntryPointJwt.class, GlobalExceptionHandler.class})
class SupplierControllerTest {
    @Autowired MockMvc mvc;
    @MockBean SupplierService supplierService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "WAREHOUSE_MANAGER", "WAREHOUSE_STAFF", "VIEWER"})
    void allowedRolesReadPaginatedSuppliers(String role) throws Exception {
        when(supplierService.getAllSuppliers(eq(""), any())).thenReturn(new PageImpl<>(List.of(
                SupplierResponse.builder().id(1L).code("NCC001").name("Supplier")
                        .contactPerson("Contact").active(true).build())));
        mvc.perform(get("/api/suppliers").with(user("reader").roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].contactPerson").value("Contact"))
                .andExpect(jsonPath("$.data.content[0].active").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "WAREHOUSE_MANAGER", "WAREHOUSE_STAFF"})
    void warehouseRolesCanChangeStatus(String role) throws Exception {
        when(supplierService.updateStatus(1L, false)).thenReturn(
                SupplierResponse.builder().id(1L).active(false).build());
        mvc.perform(patch("/api/suppliers/1/status").param("active", "false")
                        .with(user("warehouse").roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIEWER", "SALES", "SALES_MANAGER", "ACCOUNTANT"})
    void otherRolesCannotChangeStatus(String role) throws Exception {
        mvc.perform(patch("/api/suppliers/1/status").param("active", "false")
                        .with(user("reader").roles(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(supplierService);
    }

    @Test
    void anonymousCannotReadSuppliers() throws Exception {
        mvc.perform(get("/api/suppliers")).andExpect(status().isUnauthorized());
    }

    @Test
    void contactDetailsReachServiceAndReturnToFrontend() throws Exception {
        when(supplierService.createSupplier(any())).thenAnswer(invocation -> {
            com.quanlybanhang.dto.request.SupplierRequest request = invocation.getArgument(0);
            return SupplierResponse.builder().id(1L).code(request.getCode()).name(request.getName())
                    .contactPerson(request.getContactPerson()).contactPhone(request.getContactPhone())
                    .email(request.getEmail()).address(request.getAddress()).notes(request.getNotes())
                    .active(true).build();
        });
        mvc.perform(post("/api/suppliers").with(user("warehouse").roles("WAREHOUSE_STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"NCC001","name":"Supplier","contactPerson":"Contact",
                         "contactPhone":"0901234567","email":"contact@example.test",
                         "address":"Test address","notes":"Test notes"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactPerson").value("Contact"))
                .andExpect(jsonPath("$.data.contactPhone").value("0901234567"))
                .andExpect(jsonPath("$.data.email").value("contact@example.test"))
                .andExpect(jsonPath("$.data.address").value("Test address"))
                .andExpect(jsonPath("$.data.notes").value("Test notes"));
    }

    @Test
    void invalidContactDetailsAreRejected() throws Exception {
        mvc.perform(post("/api/suppliers").with(user("warehouse").roles("WAREHOUSE_STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"NCC001","name":"Supplier","contactPhone":"123","email":"invalid"}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(supplierService);
    }

    @Test
    void excessivePageSizeIsRejected() throws Exception {
        mvc.perform(get("/api/suppliers?size=201").with(user("warehouse").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(supplierService);
    }
}
