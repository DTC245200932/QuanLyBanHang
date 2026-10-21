package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.StockCountCompleteRequest;
import com.quanlybanhang.dto.request.StockCountCreateRequest;
import com.quanlybanhang.dto.request.StockCountItemUpdateRequest;
import com.quanlybanhang.dto.request.StockCountUpdateItemsRequest;
import com.quanlybanhang.dto.response.StockCountItemResponse;
import com.quanlybanhang.dto.response.StockCountOptionsResponse;
import com.quanlybanhang.dto.response.StockCountResponse;
import com.quanlybanhang.exception.GlobalExceptionHandler;
import com.quanlybanhang.model.StockCountStatus;
import com.quanlybanhang.service.StockCountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockCountController - Kiểm thử các endpoint kiểm kê kho (S5-08)")
class StockCountControllerTest {

    @Mock
    private StockCountService stockCountService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StockCountController(stockCountService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /options: Trả về danh sách kho, nhóm hàng và trạng thái kiểm kê")
    void getOptions_Returns200() throws Exception {
        StockCountOptionsResponse options = StockCountOptionsResponse.builder()
                .warehouses(List.of(new StockCountOptionsResponse.NamedOption(1L, "KHO-1", "Kho 1")))
                .categories(List.of(new StockCountOptionsResponse.NamedOption(10L, "BIA", "Bia")))
                .statuses(List.of(new StockCountOptionsResponse.StatusOption("DRAFT", "Mới tạo")))
                .canAdjust(true)
                .build();

        when(stockCountService.getOptions()).thenReturn(options);

        mockMvc.perform(get("/api/warehouse/stock-counts/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.warehouses[0].code").value("KHO-1"))
                .andExpect(jsonPath("$.data.canAdjust").value(true));
    }

    @Test
    @DisplayName("GET : Tìm kiếm / phân trang danh sách phiếu kiểm kê")
    void search_Returns200() throws Exception {
        StockCountResponse sc = StockCountResponse.builder()
                .id(1L)
                .code("PKK-20261021-001")
                .warehouseCode("KHO-1")
                .status(StockCountStatus.DRAFT)
                .build();

        when(stockCountService.search(any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(sc), org.springframework.data.domain.PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/warehouse/stock-counts")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].code").value("PKK-20261021-001"));
    }

    @Test
    @DisplayName("GET /{id}: Chi tiết phiếu kiểm kê bao gồm các dòng sản phẩm")
    void getById_Returns200() throws Exception {
        StockCountResponse sc = StockCountResponse.builder()
                .id(1L)
                .code("PKK-20261021-001")
                .warehouseCode("KHO-1")
                .status(StockCountStatus.DRAFT)
                .items(List.of(
                        StockCountItemResponse.builder()
                                .productId(101L)
                                .productCode("SP-TIGER")
                                .bookQuantity(new BigDecimal("100"))
                                .build()
                ))
                .build();

        when(stockCountService.getById(1L)).thenReturn(sc);

        mockMvc.perform(get("/api/warehouse/stock-counts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].productCode").value("SP-TIGER"));
    }

    @Test
    @DisplayName("POST : Tạo phiếu kiểm kê thành công")
    void create_ValidRequest_Returns200() throws Exception {
        StockCountResponse sc = StockCountResponse.builder()
                .id(1L)
                .code("PKK-20261021-001")
                .status(StockCountStatus.DRAFT)
                .build();

        when(stockCountService.create(any(StockCountCreateRequest.class), any()))
                .thenReturn(sc);

        String json = """
                {
                  "warehouseId": 1,
                  "categoryId": 10,
                  "note": "Kiểm kê định kỳ"
                }
                """;

        mockMvc.perform(post("/api/warehouse/stock-counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("PKK-20261021-001"));
    }

    @Test
    @DisplayName("POST : Tạo phiếu kiểm kê thiếu warehouseId -> 400 Bad Request")
    void create_MissingWarehouseId_Returns400() throws Exception {
        String json = """
                {
                  "warehouseId": null,
                  "note": "Kiểm kê thiếu kho"
                }
                """;

        mockMvc.perform(post("/api/warehouse/stock-counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /{id}/items: Cập nhật số đếm thực tế thành công")
    void updateItems_ValidRequest_Returns200() throws Exception {
        StockCountResponse sc = StockCountResponse.builder()
                .id(1L)
                .status(StockCountStatus.DRAFT)
                .build();

        when(stockCountService.updateItems(eq(1L), any(StockCountUpdateItemsRequest.class), any()))
                .thenReturn(sc);

        String json = """
                {
                  "items": [
                    {
                      "productId": 101,
                      "actualQuantity": 95,
                      "note": "Hao hụt 5"
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/warehouse/stock-counts/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /{id}/complete: Chốt kiểm kê thành công khi có lý do")
    void complete_ValidReason_Returns200() throws Exception {
        StockCountResponse sc = StockCountResponse.builder()
                .id(1L)
                .status(StockCountStatus.COMPLETED)
                .adjustmentReason("Cân đối kho")
                .build();

        when(stockCountService.complete(eq(1L), any(StockCountCompleteRequest.class), any()))
                .thenReturn(sc);

        String json = """
                {
                  "reason": "Cân đối kho sau kiểm kê"
                }
                """;

        mockMvc.perform(post("/api/warehouse/stock-counts/1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /{id}/complete: Chốt kiểm kê thiếu lý do -> 400 Bad Request")
    void complete_MissingReason_Returns400() throws Exception {
        String json = """
                {
                  "reason": ""
                }
                """;

        mockMvc.perform(post("/api/warehouse/stock-counts/1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /{id}/cancel: Hủy phiếu kiểm kê thành công")
    void cancel_Returns200() throws Exception {
        StockCountResponse sc = StockCountResponse.builder()
                .id(1L)
                .status(StockCountStatus.CANCELLED)
                .build();

        when(stockCountService.cancel(eq(1L), any(), any()))
                .thenReturn(sc);

        mockMvc.perform(post("/api/warehouse/stock-counts/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Hủy nhầm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
