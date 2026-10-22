package com.quanlybanhang.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.quanlybanhang.config.RolePermissionConfig;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration Test cho tính năng Gợi ý lô xuất FEFO và Ma trận phân quyền (S6-04).
 * Chạy trên cơ sở dữ liệu in-memory H2, kiểm thử qua HTTP, Spring Security và JPA thực tế.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:export_lot_fefo;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.open-in-view=false", "spring.jpa.show-sql=false",
    "app.jwt.secret=export-lot-fefo-test-secret-at-least-32-chars",
    "app.bootstrap.admin-password=ExportLotTest2026", "app.bootstrap.admin-email=exportlot@example.invalid",
    "app.seed-demo-users=false", "app.seed-demo-data=false", "app.order.reservation-check-interval-ms=3600000"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WarehouseExportLotIntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired WarehouseRepository warehouses;
    @Autowired WarehouseInventoryRepository inventories;
    @Autowired WarehouseLotInventoryRepository lots;
    @Autowired WarehouseVoucherRepository vouchers;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired UnitConversionRepository conversions;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired RolePermissionConfig matrix;

    Warehouse warehouse, outsideWarehouse;
    Product product;
    String staffToken, adminToken;
    WarehouseLotInventory lotA, lotB, lotC;

    @BeforeEach
    void setUp() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        warehouse = warehouses.save(Warehouse.builder().code("WH-EX-" + key).name("Kho Xuất " + key).active(true).build());
        outsideWarehouse = warehouses.save(Warehouse.builder().code("WH-OUT-" + key).name("Kho Ngoài " + key).active(true).build());

        Category category = categories.save(Category.builder().code("CAT-" + key).name("Category " + key).build());
        product = products.save(Product.builder()
                .code("P-LOT-" + key)
                .name("Sữa tươi có lô " + key)
                .category(category)
                .unit("lon")
                .manageLot(true)
                .price(new BigDecimal("20000"))
                .active(true)
                .build());

        // Tổng tồn: 75 lon
        inventories.save(WarehouseInventory.builder()
                .warehouse(warehouse)
                .product(product)
                .baseUnit("lon")
                .baseQuantity(new BigDecimal("75.0000"))
                .reservedQuantity(BigDecimal.ZERO)
                .build());

        // Lot A: 5 lon, HSD còn 10 ngày
        lotA = lots.save(WarehouseLotInventory.builder()
                .warehouse(warehouse)
                .product(product)
                .lotNumber("LOT-A-" + key)
                .expiryDate(LocalDate.now().plusDays(10))
                .manufacturingDate(LocalDate.now().minusDays(20))
                .baseQuantity(new BigDecimal("5.0000"))
                .baseUnit("lon")
                .build());

        // Lot B: 20 lon, HSD còn 30 ngày
        lotB = lots.save(WarehouseLotInventory.builder()
                .warehouse(warehouse)
                .product(product)
                .lotNumber("LOT-B-" + key)
                .expiryDate(LocalDate.now().plusDays(30))
                .manufacturingDate(LocalDate.now().minusDays(10))
                .baseQuantity(new BigDecimal("20.0000"))
                .baseUnit("lon")
                .build());

        // Lot C: 50 lon, HSD còn 90 ngày
        lotC = lots.save(WarehouseLotInventory.builder()
                .warehouse(warehouse)
                .product(product)
                .lotNumber("LOT-C-" + key)
                .expiryDate(LocalDate.now().plusDays(90))
                .manufacturingDate(LocalDate.now().minusDays(5))
                .baseQuantity(new BigDecimal("50.0000"))
                .baseUnit("lon")
                .build());

        // Gán quyền WAREHOUSE_EXPORT cho ROLE_WAREHOUSE_STAFF trong ma trận
        matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_STAFF, Set.of(Permission.WAREHOUSE_EXPORT, Permission.WAREHOUSE_VIEW));

        User staff = users.save(User.builder()
                .username("staff-" + key)
                .email(key + "@staff.invalid")
                .password(passwords.encode("ExportLotTest2026"))
                .roles(Set.of(roles.findByName(ERole.ROLE_WAREHOUSE_STAFF).orElseThrow()))
                .warehouses(Set.of(warehouse)) // Phân công ở warehouse, không có outsideWarehouse
                .build());

        staffToken = login(staff.getUsername(), "ExportLotTest2026");
        adminToken = login("admin", "ExportLotTest2026");
    }

    @Test
    @DisplayName("S6-04 AC1: API GET /api/warehouse/export/suggest-lot gợi ý lô có HSD gần nhất còn đủ số lượng")
    void suggestLot_FEFO_ReturnsLotWithNearestExpiryAndSufficientStock() {
        // Cần 10 lon -> Lot A (5 lon) không đủ -> gợi ý Lot B (20 lon, HSD gần nhất trong các lô đủ hàng)
        String url = "/api/warehouse/export/suggest-lot?warehouseId=" + warehouse.getId() +
                "&productId=" + product.getId() + "&quantity=10&unit=lon";

        JsonNode response = call(HttpMethod.GET, url, staffToken, null, 200);
        JsonNode data = response.get("data");

        assertThat(data.get("hasSufficientStock").asBoolean()).isTrue();
        assertThat(data.at("/suggestedLot/lotNumber").asText()).isEqualTo(lotB.getLotNumber());
        assertThat(data.at("/suggestedLot/availableQuantity").decimalValue()).isEqualByComparingTo("20");
        assertThat(data.get("availableLots").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("S6-04 AC1: API POST /api/warehouse/export/suggest-lots gợi ý hàng loạt")
    void suggestLots_Batch_ReturnsSuggestions() {
        Map<String, Object> body = Map.of(
                "warehouseId", warehouse.getId(),
                "items", List.of(Map.of("productId", product.getId(), "quantity", 3, "unit", "lon"))
        );

        JsonNode response = call(HttpMethod.POST, "/api/warehouse/export/suggest-lots", staffToken, body, 200);
        JsonNode list = response.get("data");
        assertThat(list.isArray()).isTrue();
        assertThat(list.size()).isEqualTo(1);
        // Cần 3 lon -> Lot A (5 lon) đủ số lượng và có HSD gần nhất
        assertThat(list.get(0).at("/suggestedLot/lotNumber").asText()).isEqualTo(lotA.getLotNumber());
    }

    @Test
    @DisplayName("S6-04 AC2: Xuất kho chọn lô khác (Lot C) nhưng KHÔNG nhập lý do -> trả 400 Bad Request")
    void exportGoods_SelectDifferentLotWithoutReason_Returns400() {
        Map<String, Object> body = Map.of(
                "warehouseId", warehouse.getId(),
                "type", "EXPORT",
                "items", List.of(Map.of(
                        "productId", product.getId(),
                        "quantity", 10,
                        "unit", "lon",
                        "lotNumber", lotC.getLotNumber() // Lô gợi ý cho 10 lon là Lot B, đây chọn Lot C
                        // Không có overrideReason
                ))
        );

        JsonNode response = call(HttpMethod.POST, "/api/warehouse/export", staffToken, body, 400);
        assertThat(response.get("message").asText()).contains("bắt buộc phải nhập lý do");
    }

    @Test
    @DisplayName("S6-04 AC2 & AC3: Xuất kho chọn lô khác (Lot C) và CÓ nhập lý do -> 200 OK và trừ đúng tồn lô C")
    void exportGoods_SelectDifferentLotWithReason_SuccessAndDeductsSelectedLot() {
        Map<String, Object> body = Map.of(
                "warehouseId", warehouse.getId(),
                "type", "EXPORT",
                "voucherCode", "PXK-" + UUID.randomUUID(),
                "items", List.of(Map.of(
                        "productId", product.getId(),
                        "quantity", 10,
                        "unit", "lon",
                        "lotNumber", lotC.getLotNumber(),
                        "overrideReason", "Khách hàng yêu cầu date xa để vận chuyển đường dài"
                ))
        );

        JsonNode response = call(HttpMethod.POST, "/api/warehouse/export", staffToken, body, 200);
        JsonNode item = response.at("/data/items/0");

        assertThat(item.get("lotNumber").asText()).isEqualTo(lotC.getLotNumber());
        assertThat(item.get("suggestedLotNumber").asText()).isEqualTo(lotB.getLotNumber());
        assertThat(item.get("overrideReason").asText()).isEqualTo("Khách hàng yêu cầu date xa để vận chuyển đường dài");

        // Kiểm tra trừ đúng tồn cơ sở của Lot C: 50 - 10 = 40
        var updatedLotC = lots.findByWarehouseIdAndProductIdAndLotNumber(warehouse.getId(), product.getId(), lotC.getLotNumber()).orElseThrow();
        assertThat(updatedLotC.getBaseQuantity()).isEqualByComparingTo("40");

        // Lot B không bị trừ: 20
        var unchangedLotB = lots.findByWarehouseIdAndProductIdAndLotNumber(warehouse.getId(), product.getId(), lotB.getLotNumber()).orElseThrow();
        assertThat(unchangedLotB.getBaseQuantity()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("Ma trận phân quyền: Thu hồi WAREHOUSE_EXPORT -> API trả ngay 403 Forbidden; cấp lại -> trả 200 OK")
    void permissionMatrix_RevokeAndGrant_ImmediateEnforcement() {
        String suggestUrl = "/api/warehouse/export/suggest-lot?warehouseId=" + warehouse.getId() +
                "&productId=" + product.getId() + "&quantity=5&unit=lon";

        // Trước khi thu hồi: 200 OK
        call(HttpMethod.GET, suggestUrl, staffToken, null, 200);

        // Thu hồi toàn bộ quyền kho khỏi ROLE_WAREHOUSE_STAFF trong DB
        matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_STAFF, Collections.emptySet());

        // Request tiếp theo trên token hiện tại phải bị 403 ngay lập tức!
        call(HttpMethod.GET, suggestUrl, staffToken, null, 403);

        Map<String, Object> exportBody = Map.of(
                "warehouseId", warehouse.getId(),
                "type", "EXPORT",
                "items", List.of(Map.of("productId", product.getId(), "quantity", 5, "unit", "lon"))
        );
        call(HttpMethod.POST, "/api/warehouse/export", staffToken, exportBody, 403);

        // Cấp lại quyền WAREHOUSE_EXPORT
        matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_STAFF, Set.of(Permission.WAREHOUSE_EXPORT));

        // Request tiếp theo thành công 200 OK
        call(HttpMethod.GET, suggestUrl, staffToken, null, 200);
    }

    @Test
    @DisplayName("Phạm vi dữ liệu kho: Truy cập kho ngoài phạm vi phân công -> 403 Forbidden")
    void warehouseScope_OutsideWarehouse_Forbidden() {
        String suggestUrl = "/api/warehouse/export/suggest-lot?warehouseId=" + outsideWarehouse.getId() +
                "&productId=" + product.getId() + "&quantity=5&unit=lon";

        // Nhân viên kho chỉ được phân công ở warehouse, gọi outsideWarehouse -> 403
        call(HttpMethod.GET, suggestUrl, staffToken, null, 403);

        // Admin luôn được phép truy cập mọi kho
        call(HttpMethod.GET, suggestUrl, adminToken, null, 200);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────────

    private String login(String username, String password) {
        ResponseEntity<JsonNode> resp = exchange(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", username, "password", password));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        return resp.getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> exchange(HttpMethod method, String path, String jwt, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (jwt != null) {
            headers.setBearerAuth(jwt);
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private JsonNode call(HttpMethod method, String path, String jwt, Object body, int expectedStatus) {
        ResponseEntity<JsonNode> resp = exchange(method, path, jwt, body);
        assertThat(resp.getStatusCode().value())
                .as("%s %s response: %s", method, path, resp.getBody())
                .isEqualTo(expectedStatus);
        return resp.getBody();
    }
}

