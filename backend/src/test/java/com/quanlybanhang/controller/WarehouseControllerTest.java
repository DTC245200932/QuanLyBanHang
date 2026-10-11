package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.WarehouseVoucherRequest;
import com.quanlybanhang.dto.response.WarehouseVoucherResponse;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import com.quanlybanhang.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WarehouseControllerTest {
    @Mock WarehouseService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WarehouseController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"adjust", "import", "export"})
    void emptyOrInvalidVoucherNeverReportsSuccess(String endpoint) throws Exception {
        for (String body : new String[]{"", "null", "{}", "{\"items\":[]}", "{\"items\":[null]}",
                "{\"items\":[{\"productId\":1,\"quantity\":0}]}"}) {
            mvc.perform(post("/api/warehouse/" + endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        verifyNoInteractions(service);
    }

    @Test
    void validImportReturnsThePersistedVoucher() throws Exception {
        when(service.importGoods(any(WarehouseVoucherRequest.class), eq("system")))
                .thenReturn(WarehouseVoucherResponse.builder().id(87L).voucherCode("PNK-87").build());
        mvc.perform(post("/api/warehouse/import").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":3,\"items\":[{\"productId\":7,\"quantity\":2}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(87))
                .andExpect(jsonPath("$.data.voucherCode").value("PNK-87"));
        verify(service).importGoods(any(WarehouseVoucherRequest.class), eq("system"));
    }
}
