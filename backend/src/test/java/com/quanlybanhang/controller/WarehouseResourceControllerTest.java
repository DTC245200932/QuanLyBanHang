package com.quanlybanhang.controller;

import com.quanlybanhang.config.SecurityConfig;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import com.quanlybanhang.security.*;
import com.quanlybanhang.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WarehouseResourceController.class)
@Import({SecurityConfig.class, RestAccessDeniedHandler.class, AuthEntryPointJwt.class, GlobalExceptionHandler.class})
class WarehouseResourceControllerTest {
    @Autowired MockMvc mvc;
    @MockBean WarehouseService service;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void passThrough() throws Exception {
        doAnswer(i -> { ((jakarta.servlet.FilterChain)i.getArgument(2)).doFilter(i.getArgument(0), i.getArgument(1)); return null; })
                .when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
        "GET,/api/warehouses,WAREHOUSE_VIEW",
        "GET,/api/warehouses/1,WAREHOUSE_VIEW",
        "GET,/api/warehouses/1/locations,WAREHOUSE_VIEW",
        "GET,/api/warehouse-locations,WAREHOUSE_MANAGE",
        "POST,/api/warehouses,WAREHOUSE_MANAGE",
        "PUT,/api/warehouses/1,WAREHOUSE_MANAGE",
        "POST,/api/warehouses/1/locations,WAREHOUSE_MANAGE",
        "PUT,/api/warehouse-locations/1,WAREHOUSE_MANAGE",
        "GET,/api/warehouses/dealer-assignments,WAREHOUSE_MANAGE",
        "PUT,/api/warehouses/dealer-assignments/1,WAREHOUSE_MANAGE"
    })
    void matrixGrantEnablesAndRevocationBlocksEvenWarehouseManager(String method, String path, String permission) throws Exception {
        String body = "{\"code\":\"KHO-1\",\"name\":\"Kho 1\",\"warehouseId\":1,\"locationType\":\"SHELF\"}";
        mvc.perform(request(HttpMethod.valueOf(method), path)
                .with(user("matrix-user").authorities(new SimpleGrantedAuthority("ROLE_VIEWER"), new SimpleGrantedAuthority(permission)))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        clearInvocations(service);
        mvc.perform(request(HttpMethod.valueOf(method), path).with(user("matrix-user").roles("WAREHOUSE_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mvc.perform(request(HttpMethod.valueOf(method), path).with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    @Test
    void optionalLocationCodeReachesServiceButMissingNameIsStillInvalid() throws Exception {
        mvc.perform(post("/api/warehouses/1/locations").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Kệ mới\",\"locationType\":\"SHELF\"}"))
                .andExpect(status().isOk());
        verify(service).createLocation(eq(1L), argThat(r -> r.getCode() == null), eq("admin"));
        clearInvocations(service);
        mvc.perform(post("/api/warehouses/1/locations").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"\",\"locationType\":\"SHELF\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void viewOnlyCannotChangeAssignmentsAndInvalidAssignmentCannotReachService() throws Exception {
        mvc.perform(put("/api/warehouses/dealer-assignments/1").with(user("reader").authorities(new SimpleGrantedAuthority("WAREHOUSE_VIEW")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"warehouseId\":1}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/warehouses/dealer-assignments/1").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/warehouses/dealer-assignments").param("size", "201").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/warehouses")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
