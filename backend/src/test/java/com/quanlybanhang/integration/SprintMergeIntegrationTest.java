package com.quanlybanhang.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.config.RolePermissionConfig;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** HTTP, security, JPA and transactions use a disposable database, without mocks. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sprintmerge;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false", "app.jwt.secret=sprint-audit-test-secret-only-at-least-32-characters",
        "app.bootstrap.admin-password=SprintTest2026", "app.bootstrap.admin-email=audit@example.invalid",
        "app.seed-demo-users=false", "app.seed-demo-data=false",
        "app.order.reservation-check-interval-ms=3600000"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SprintMergeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired WarehouseRepository warehouses;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired DealerRepository dealers;
    @Autowired PriceListRepository prices;
    @Autowired UnitConversionRepository conversions;
    @Autowired WarehouseInventoryRepository inventories;
    @Autowired SupplierRepository suppliers;
    @Autowired PasswordEncoder passwords;
    @Autowired RolePermissionConfig matrix;
    @Autowired OrderRepository orders;

    Warehouse warehouse, destination;
    Product product;
    Dealer dealer;
    String managerToken, dealerToken, salesToken, strangerToken;
    Long dealerUserId;

    @BeforeEach
    void fixtures() throws Exception {
        String key = UUID.randomUUID().toString().substring(0, 8);
        warehouse = warehouses.save(Warehouse.builder().code("WH-" + key).name("Kho kiểm thử " + key).build());
        destination = warehouses.save(Warehouse.builder().code("TO-" + key).name("Kho nhận " + key).build());
        var category = categories.save(Category.builder().code("CAT-" + key).name("Nhóm kiểm thử").build());
        product = products.save(Product.builder().code("SKU-" + key).name("Sản phẩm kiểm thử")
                .category(category).unit("lon").price(new BigDecimal("25000"))
                .costPrice(new BigDecimal("10000")).active(true).build());
        conversions.save(UnitConversion.builder().product(product).unitName("thùng")
                .conversionFactor(new BigDecimal("24")).isBaseUnit(false).price(BigDecimal.ONE).build());
        inventories.save(WarehouseInventory.builder().warehouse(warehouse).product(product)
                .baseQuantity(new BigDecimal("240")).baseUnit("lon").build());
        User sales = createUser("sale-" + key, ERole.ROLE_SALES, null);
        dealer = dealers.save(Dealer.builder().code("DL-" + key).name("Đại lý kiểm thử")
                .assignedUser(sales).warehouse(warehouse).customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .debtLimit(new BigDecimal("100000000")).build());
        User representative = createUser("dealer-" + key, ERole.ROLE_DEALER, dealer);
        dealerUserId = representative.getId();
        createUser("stranger-" + key, ERole.ROLE_SALES, null);
        User manager = createUser("manager-" + key, ERole.ROLE_SALES_MANAGER, null);
        var priceList = PriceList.builder().code("BG-" + key).name("Bảng giá kiểm thử")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1).startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(30)).build();
        priceList.addItem(PriceListItem.builder().product(product).sellingPrice(new BigDecimal("20000"))
                .floorPrice(new BigDecimal("18000")).build());
        prices.save(priceList);
        managerToken = login(manager.getUsername());
        dealerToken = login(representative.getUsername());
        salesToken = login(sales.getUsername());
        strangerToken = login("stranger-" + key);
    }

    @Test
    void stockLedgerShowsPhysicalReservedAvailableAndZeroStockWithoutWriting() throws Exception {
        WarehouseInventory stock = stock();
        stock.setBaseQuantity(new BigDecimal("10.5000")); stock.setReservedQuantity(new BigDecimal("3.2500"));
        stock.setBaseUnit("hộp");
        stock.setInTransitQuantity(new BigDecimal("40")); inventories.save(stock);
        long before = inventories.count();
        JsonNode row = call(get("/api/warehouse/stock-ledger").param("warehouseId", warehouse.getId().toString())
                .param("categoryId", product.getCategory().getId().toString()).param("keyword", product.getCode()), managerToken, null, 200)
                .at("/data/content/0");
        assertThat(row.get("physicalQuantity").decimalValue()).isEqualByComparingTo("10.5");
        assertThat(row.get("reservedQuantity").decimalValue()).isEqualByComparingTo("3.25");
        assertThat(row.get("availableQuantity").decimalValue()).isEqualByComparingTo("7.25");
        assertThat(row.get("baseUnit").asText()).isEqualTo("hộp");
        assertThat(row.get("stockStatus").asText()).isEqualTo("IN_STOCK");
        assertThat(row.has("price") || row.has("costPrice")).isFalse();
        JsonNode zero = call(get("/api/warehouse/stock-ledger").param("warehouseId", destination.getId().toString())
                .param("keyword", product.getCode()), managerToken, null, 200).at("/data/content/0");
        assertThat(zero.get("physicalQuantity").decimalValue()).isZero();
        assertThat(zero.get("reservedQuantity").decimalValue()).isZero();
        assertThat(zero.get("availableQuantity").decimalValue()).isZero();
        assertThat(zero.get("stockStatus").asText()).isEqualTo("OUT_OF_STOCK");
        assertThat(inventories.count()).isEqualTo(before);
    }

    @Test
    void stockLedgerFiltersStatusSearchAndPaginationOnServer() throws Exception {
        WarehouseInventory stock = stock(); stock.setReservedQuantity(stock.getBaseQuantity()); inventories.save(stock);
        for (String status : List.of("IN_STOCK", "OUT_OF_STOCK", "FULLY_RESERVED")) {
            JsonNode data = call(get("/api/warehouse/stock-ledger").param("warehouseId", warehouse.getId().toString())
                    .param("keyword", product.getCode()).param("stockStatus", status), managerToken, null, 200).get("data");
            assertThat(data.get("totalElements").asLong()).isEqualTo(status.equals("FULLY_RESERVED") ? 1 : 0);
        }
        String term = "Search%_!" + UUID.randomUUID();
        product.setName(term); products.save(product);
        products.save(Product.builder().code("NEXT-" + UUID.randomUUID()).name(term).category(product.getCategory()).unit("kg").build());
        var first = call(get("/api/warehouse/stock-ledger").param("warehouseId", warehouse.getId().toString())
                .param("keyword", term.toLowerCase()).param("size", "1"), managerToken, null, 200).get("data");
        var second = call(get("/api/warehouse/stock-ledger").param("warehouseId", warehouse.getId().toString())
                .param("keyword", term).param("size", "1").param("page", "1"), managerToken, null, 200).get("data");
        assertThat(first.get("totalElements").asLong()).isEqualTo(2);
        assertThat(first.at("/content/0/productId")).isNotEqualTo(second.at("/content/0/productId"));
        assertThat(first.get("last").asBoolean()).isFalse();
        assertThat(second.get("last").asBoolean()).isTrue();
        call(get("/api/warehouse/stock-ledger").param("size", "201"), managerToken, null, 400);
        call(get("/api/warehouse/stock-ledger").param("page", "-1"), managerToken, null, 400);
    }

    @Test
    void stockLedgerHonorsLivePermissionsAndWarehouseAssignments() throws Exception {
        User staff = createUser("stock-reader-" + UUID.randomUUID(), ERole.ROLE_WAREHOUSE_STAFF, null);
        staff.setWarehouses(Set.of(warehouse)); users.save(staff);
        String token = login(staff.getUsername());
        JsonNode options = call(get("/api/warehouse/stock-ledger/options"), token, null, 200).get("data");
        assertThat(options.get("warehouses").size()).isEqualTo(1);
        assertThat(options.at("/warehouses/0/id").asLong()).isEqualTo(warehouse.getId());
        assertThat(call(get("/api/warehouse/stock-ledger").param("keyword", product.getCode()), token, null, 200)
                .at("/data/totalElements").asLong()).isEqualTo(1);
        call(get("/api/warehouse/stock-ledger").param("warehouseId", destination.getId().toString()), token, null, 403);
        Set<Permission> original = matrix.getPermissions(ERole.ROLE_WAREHOUSE_STAFF);
        try {
            matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_STAFF, Set.of(Permission.WAREHOUSE_VIEW));
            call(get("/api/warehouse/stock-ledger/options"), token, null, 200);
            call(get("/api/warehouse/stock-ledger"), token, null, 200);
            matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_STAFF, Set.of(Permission.WAREHOUSE_IMPORT));
            call(get("/api/warehouse/stock-ledger"), token, null, 403);
            call(get("/api/warehouse/stock-ledger/options"), token, null, 403);
            matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_STAFF, Set.of(Permission.WAREHOUSE_VIEW));
            staff.setWarehouses(Set.of()); users.save(staff);
            assertThat(call(get("/api/warehouse/stock-ledger/options"), token, null, 200).at("/data/warehouses").isEmpty()).isTrue();
            assertThat(call(get("/api/warehouse/stock-ledger"), token, null, 200).at("/data/totalElements").asLong()).isZero();
            call(get("/api/warehouse/stock-ledger").param("warehouseId", warehouse.getId().toString()), token, null, 403);
        } finally { matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_STAFF, original); }
        String admin = login("admin");
        call(get("/api/warehouse/stock-ledger").param("warehouseId", destination.getId().toString()), admin, null, 200);
    }

    @Test
    void automaticLocationCodesArePerWarehouseAndSafeForConcurrentCreates() throws Exception {
        String admin = login("admin");
        String url = "/api/warehouses/" + warehouse.getId() + "/locations";
        call(post(url), admin, Map.of("name", "Kệ có nhãn", "code", "KE-009", "locationType", "SHELF"), 200);
        var jobs = new ArrayList<CompletableFuture<JsonNode>>();
        for (int i = 0; i < 4; i++) {
            jobs.add(CompletableFuture.supplyAsync(() -> {
                try { return call(post(url), admin, Map.of("name", "Kệ tự sinh", "code", " ", "locationType", "SHELF"), 200).get("data"); }
                catch (Exception e) { throw new RuntimeException(e); }
            }));
        }
        var created = jobs.stream().map(CompletableFuture::join).toList();
        assertThat(created.stream().map(n -> n.get("code").asText()).toList())
                .containsExactlyInAnyOrder("KE-010", "KE-011", "KE-012", "KE-013");
        long id = created.get(0).get("id").asLong();
        assertThat(call(put("/api/warehouse-locations/" + id), admin,
                Map.of("code", "", "name", "Tên đã sửa", "locationType", "SHELF"), 200).at("/data/code").asText())
                .isEqualTo(created.get(0).get("code").asText());
        assertThat(call(post("/api/warehouses/" + destination.getId() + "/locations"), admin,
                Map.of("name", "Kệ kho khác", "locationType", "SHELF"), 200).at("/data/code").asText()).isEqualTo("KE-001");
        assertThat(call(post(url), admin, Map.of("name", "Khu tự sinh", "locationType", "ZONE"), 200)
                .at("/data/code").asText()).isEqualTo("KHU-001");
        assertThat(call(post(url), admin, Map.of("name", "Khu có nhãn", "code", "NHAN-KHU-X", "locationType", "ZONE"), 200)
                .at("/data/code").asText()).isEqualTo("NHAN-KHU-X");
        call(post(url), admin, Map.of("name", "Mã trùng", "code", "KE-009", "locationType", "SHELF"), 400);
        Set<Permission> original = matrix.getPermissions(ERole.ROLE_SALES);
        try {
            matrix.updateRolePermissions(ERole.ROLE_SALES, Set.of(Permission.WAREHOUSE_MANAGE));
            call(post(url), salesToken, Map.of("name", "Được cấp quyền", "locationType", "SHELF"), 200);
            matrix.updateRolePermissions(ERole.ROLE_SALES, Set.of());
            call(post(url), salesToken, Map.of("name", "Đã thu hồi", "locationType", "SHELF"), 403);
        } finally { matrix.updateRolePermissions(ERole.ROLE_SALES, original); }
    }

    @Test
    void warehouseManagementUsesLiveMatrixAndPersistsLocationsAndDealerWarehouse() throws Exception {
        User viewer = createUser("warehouse-ui-" + UUID.randomUUID(), ERole.ROLE_VIEWER, null);
        String token = login(viewer.getUsername());
        Set<Permission> original = matrix.getPermissions(ERole.ROLE_VIEWER);
        try {
            matrix.updateRolePermissions(ERole.ROLE_VIEWER, EnumSet.of(Permission.WAREHOUSE_MANAGE));
            long zoneId = call(post("/api/warehouses/" + warehouse.getId() + "/locations"), token,
                    Map.of("code", "ZONE-A", "name", "Khu A", "locationType", "ZONE", "parentLocationId", 0), 200)
                    .at("/data/id").asLong();
            long shelfId = call(post("/api/warehouses/" + warehouse.getId() + "/locations"), token,
                    Map.of("code", "SHELF-A", "name", "Kệ A", "locationType", "SHELF", "parentLocationId", zoneId), 200)
                    .at("/data/id").asLong();
            call(put("/api/warehouse-locations/" + zoneId), token,
                    Map.of("code", "ZONE-A", "name", "Khu A", "locationType", "SHELF"), 400);
            JsonNode detached = call(put("/api/warehouse-locations/" + shelfId), token,
                    Map.of("code", "SHELF-A", "name", "Kệ A", "locationType", "SHELF", "parentLocationId", 0), 200);
            assertThat(detached.at("/data/parentLocationId").isNull()).isTrue();
            JsonNode assigned = call(put("/api/warehouses/dealer-assignments/" + dealer.getId()), token,
                    Map.of("warehouseId", destination.getId()), 200).get("data");
            assertThat(assigned.get("warehouseId").asLong()).isEqualTo(destination.getId());
            assertThat(assigned.has("debtLimit")).isFalse();
            assertThat(dealers.findById(dealer.getId()).orElseThrow().getWarehouse().getId()).isEqualTo(destination.getId());
            call(get("/api/warehouses/dealer-assignments"), token, null, 200);
            matrix.updateRolePermissions(ERole.ROLE_VIEWER, EnumSet.of(Permission.WAREHOUSE_VIEW));
            call(get("/api/warehouses"), token, null, 200);
            call(get("/api/warehouses/" + warehouse.getId()), token, null, 200);
            call(get("/api/warehouses/dealer-assignments"), token, null, 403);
            call(put("/api/warehouses/dealer-assignments/" + dealer.getId()), token,
                    Map.of("warehouseId", warehouse.getId()), 403);
            call(put("/api/warehouse-locations/" + shelfId), token,
                    Map.of("code", "SHELF-A", "name", "Kệ A", "locationType", "SHELF"), 403);
            matrix.updateRolePermissions(ERole.ROLE_VIEWER, Set.of());
            call(get("/api/warehouses"), token, null, 403);
            call(get("/api/warehouse-locations"), token, null, 403);
        } finally { matrix.updateRolePermissions(ERole.ROLE_VIEWER, original); }
    }

    @Test
    void debtAndOverdueDoNotDisappearWhenFulfillmentStatusChanges() throws Exception {
        String admin = login("admin");
        long id = call(post("/api/orders"), managerToken, orderBody(1, false), 201).at("/data/id").asLong();
        for (String status : List.of("PICKING", "EXPORTED", "DELIVERED", "CLOSED")) {
            call(put("/api/orders/" + id + "/status"), admin, Map.of("status", status), 200);
            assertThat(call(get("/api/dealers/" + dealer.getId() + "/debt"), managerToken, null, 200)
                    .at("/data/currentDebt").decimalValue()).isEqualByComparingTo("480000");
        }
        Order closed = orders.findById(id).orElseThrow();
        closed.setRecordedAt(java.time.LocalDateTime.now().minusDays(5));
        orders.save(closed);
        dealer.setMaxDebtDays(1);
        dealers.save(dealer);
        assertThat(call(get("/api/dealers/" + dealer.getId() + "/debt"), managerToken, null, 200)
                .at("/data/hasOverdueDebt").asBoolean()).isTrue();
        call(post("/api/orders"), managerToken, orderBody(1, false), 400);
    }

    @Test
    void dealerWarehouseCannotBeOverriddenOrReplacedWithAnArbitraryDefault() throws Exception {
        var body = new HashMap<>(orderBody(1, false));
        body.put("warehouseId", destination.getId());
        call(post("/api/orders"), managerToken, body, 403);
        call(post("/api/orders/calculate"), managerToken, body, 403);
        call(post("/api/orders/check-inventory"), managerToken, Map.of("dealerId", dealer.getId(),
                "warehouseId", destination.getId(), "productId", product.getId(), "quantity", 1, "unit", "lon"), 403);
        dealer.setWarehouse(null);
        dealers.save(dealer);
        call(post("/api/orders"), managerToken, orderBody(1, false), 400);
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void portalPreviewMatchesCreationAndIgnoresForgedPrices() throws Exception {
        String key = UUID.randomUUID().toString().substring(0, 8);
        call(post("/api/volume-discounts"), managerToken, Map.of("code", "DISC-" + key, "name", "Chiết khấu kiểm thử",
                "targetType", "PRODUCT", "productId", product.getId(), "customerGroup", "DAI_LY_CAP_1",
                "tiers", List.of(Map.of("minQuantity", 1, "discountType", "PERCENTAGE", "discountValue", 10))), 201);
        JsonNode preview = call(post("/api/portal/orders/preview"), dealerToken, orderBody(2, true), 200).get("data");
        assertThat(preview.at("/pricing/subtotalAmount").decimalValue()).isEqualByComparingTo("960000");
        assertThat(preview.at("/pricing/discountAmount").decimalValue()).isEqualByComparingTo("96000");
        JsonNode created = call(post("/api/portal/orders"), dealerToken, orderBody(2, true), 201).get("data");
        assertThat(created.get("totalAmount").decimalValue()).isEqualByComparingTo(preview.at("/pricing/totalAmount").decimalValue());
        assertThat(created.get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void confirmationUsesItsOwnMatrixPermissionAndAssignedDealerScope() throws Exception {
        long id = call(post("/api/portal/orders"), dealerToken, orderBody(1, false), 201).at("/data/id").asLong();
        Set<Permission> original = matrix.getPermissions(ERole.ROLE_SALES);
        try {
            call(put("/api/orders/" + id + "/approve"), salesToken, null, 403);
            call(put("/api/orders/" + id + "/confirm"), strangerToken, null, 403);
            assertThat(call(get("/api/orders/" + id), salesToken, null, 200).at("/data/canConfirmPortalOrder").asBoolean()).isTrue();
            Set<Permission> revoked = EnumSet.copyOf(original);
            revoked.remove(Permission.ORDER_CONFIRM);
            matrix.updateRolePermissions(ERole.ROLE_SALES, revoked);
            call(put("/api/orders/" + id + "/confirm"), salesToken, null, 403);
            assertThat(call(get("/api/orders/" + id), salesToken, null, 200).at("/data/canConfirmPortalOrder").asBoolean()).isFalse();
            matrix.updateRolePermissions(ERole.ROLE_SALES, original);
            call(put("/api/orders/" + id + "/confirm"), salesToken, null, 200);
            assertThat(stock().getReservedQuantity()).isEqualByComparingTo("24");
        } finally { matrix.updateRolePermissions(ERole.ROLE_SALES, original); }
    }

    @Test
    void confirmationCannotReplaceApprovalOfDebtViolations() throws Exception {
        dealer.setDebtLimit(BigDecimal.ONE);
        dealers.save(dealer);
        long id = call(post("/api/portal/orders"), dealerToken, orderBody(1, false), 201).at("/data/id").asLong();
        call(put("/api/orders/" + id + "/confirm"), salesToken, null, 400);
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("0");
        call(put("/api/orders/" + id + "/approve"), managerToken, null, 200);
    }

    @Test
    void newCapabilitiesCanBeGrantedToAnotherRoleAndRevokedOnTheExistingToken() throws Exception {
        User representative = createUser("confirm-" + UUID.randomUUID(), ERole.ROLE_VIEWER, null);
        dealer.setAssignedUser(representative);
        dealers.save(dealer);
        String token = login(representative.getUsername());
        Set<Permission> original = matrix.getPermissions(ERole.ROLE_VIEWER);
        Set<Permission> dealerRights = matrix.getPermissions(ERole.ROLE_DEALER);
        long id = call(post("/api/portal/orders"), dealerToken, orderBody(1, false), 201).at("/data/id").asLong();
        try {
            call(put("/api/orders/" + id + "/confirm"), token, null, 403);
            Set<Permission> grant = EnumSet.copyOf(original);
            grant.add(Permission.ORDER_CONFIRM);
            matrix.updateRolePermissions(ERole.ROLE_VIEWER, grant);
            assertThat(call(get("/api/orders/" + id), token, null, 200).at("/data/canConfirmPortalOrder").asBoolean()).isTrue();
            call(put("/api/orders/" + id + "/confirm"), token, null, 200);
            Set<Permission> revoke = EnumSet.copyOf(dealerRights);
            revoke.remove(Permission.PRODUCT_VIEW);
            matrix.updateRolePermissions(ERole.ROLE_DEALER, revoke);
            call(post("/api/portal/orders/preview"), dealerToken, orderBody(1, false), 403);
            matrix.updateRolePermissions(ERole.ROLE_DEALER, dealerRights);
            call(post("/api/portal/orders/preview"), dealerToken, orderBody(1, false), 200);
        } finally {
            matrix.updateRolePermissions(ERole.ROLE_VIEWER, original);
            matrix.updateRolePermissions(ERole.ROLE_DEALER, dealerRights);
        }
    }

    @Test
    void futureShippingDateDoesNotChangeTheCurrentOrderPrice() throws Exception {
        var body = new HashMap<>(orderBody(1, false));
        body.put("expectedDeliveryDate", LocalDate.now().plusDays(90).toString());
        assertThat(call(post("/api/orders"), managerToken, body, 201).at("/data/totalAmount").decimalValue())
                .isEqualByComparingTo("480000");
    }

    @Test
    void pricingRespectsAssignedDealerAndInactiveProductsCannotBeOrdered() throws Exception {
        Map<String, Object> line = Map.of("dealerId", dealer.getId(), "productId", product.getId(), "quantity", 1, "unit", "lon");
        call(post("/api/orders/line-item/pricing"), strangerToken, line, 403);
        product.setActive(false);
        products.save(product);
        call(post("/api/orders/line-item/pricing"), managerToken, line, 400);
        call(post("/api/orders/calculate"), managerToken, orderBody(1, false), 400);
        call(post("/api/orders"), managerToken, orderBody(1, false), 400);
        call(post("/api/portal/orders/preview"), dealerToken, orderBody(1, false), 400);
        call(post("/api/portal/orders"), dealerToken, orderBody(1, false), 400);
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void warehouseNextStepRespectsRevocationAndAssignedWarehouse() throws Exception {
        User worker = createUser("worker-" + UUID.randomUUID(), ERole.ROLE_WAREHOUSE_STAFF, null);
        String token = login(worker.getUsername());
        long id = call(post("/api/orders"), managerToken, orderBody(1, false), 201).at("/data/id").asLong();
        Set<Permission> original = matrix.getPermissions(ERole.ROLE_WAREHOUSE_STAFF);
        try {
            assertThat(call(get("/api/orders/" + id), token, null, 200).at("/data/nextLifecycleStatus").asText()).isEqualTo("PICKING");
            Set<Permission> revoked = EnumSet.copyOf(original);
            revoked.remove(Permission.WAREHOUSE_EXPORT);
            matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_STAFF, revoked);
            assertThat(call(get("/api/orders/" + id), token, null, 200).at("/data/nextLifecycleStatus").isNull()).isTrue();
            call(put("/api/orders/" + id + "/status"), token, Map.of("status", "PICKING"), 403);
            matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_STAFF, original);
            worker.setWarehouses(Set.of(destination));
            users.save(worker);
            assertThat(call(get("/api/orders/" + id), token, null, 200).at("/data/nextLifecycleStatus").isNull()).isTrue();
            call(put("/api/orders/" + id + "/status"), token, Map.of("status", "PICKING"), 403);
            worker.setWarehouses(Set.of(warehouse));
            users.save(worker);
            assertThat(call(get("/api/orders/" + id), token, null, 200).at("/data/nextLifecycleStatus").asText()).isEqualTo("PICKING");
            call(put("/api/orders/" + id + "/status"), token, Map.of("status", "PICKING"), 200);
        } finally { matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_STAFF, original); }
    }

    @Test
    void savedMatrixRevokesAndRestoresAccessOnTheSameLoginToken() throws Exception {
        String admin = login("admin");
        Set<Permission> original = matrix.getPermissions(ERole.ROLE_SALES_MANAGER);
        Set<Permission> revoked = EnumSet.copyOf(original);
        revoked.remove(Permission.FINANCE_VIEW_COST_PRICE);
        try {
            call(get("/api/finance/cost-price"), managerToken, null, 200);
            call(put("/api/admin/permissions"), admin,
                    Map.of("ROLE_SALES_MANAGER", revoked), 200);
            call(get("/api/finance/cost-price"), managerToken, null, 403);
            assertThat(call(get("/api/auth/permissions"), managerToken, null, 200)
                    .at("/data/permissions").toString()).doesNotContain("FINANCE_VIEW_COST_PRICE");
            call(get("/api/finance/cost-price"), admin, null, 200);
            call(put("/api/admin/permissions"), admin,
                    Map.of("ROLE_SALES_MANAGER", original), 200);
            call(get("/api/finance/cost-price"), managerToken, null, 200);
            call(put("/api/admin/permissions"), managerToken,
                    Map.of("ROLE_SALES_MANAGER", original), 403);
        } finally {
            matrix.updateRolePermissions(ERole.ROLE_SALES_MANAGER, original);
        }
    }

    @Test
    void portalOrderApprovalCancellationAndPdfUseTheSameDatabase() throws Exception {
        assertThat(call(get("/api/portal/profile"), dealerToken, null, 200).at("/data/id").asLong())
                .isEqualTo(dealer.getId());
        JsonNode catalog = call(get("/api/portal/catalog"), dealerToken, null, 200).get("data");
        JsonNode current = null;
        for (JsonNode item : catalog) if (item.get("id").asLong() == product.getId()) current = item;
        assertThat(current).isNotNull();
        assertThat(current.toString()).doesNotContain("costPrice");
        assertThat(current.at("/units/1/price").decimalValue()).isEqualByComparingTo("480000");
        call(get("/api/products"), dealerToken, null, 403);
        call(get("/api/orders"), dealerToken, null, 403);
        JsonNode created = call(post("/api/portal/orders"), dealerToken, orderBody(2, true), 201).get("data");
        long id = created.get("id").asLong();
        assertThat(created.get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(created.get("totalAmount").decimalValue()).isEqualByComparingTo("960000");
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("0");
        call(get("/api/portal/orders/" + id), dealerToken, null, 200);
        call(put("/api/orders/" + id + "/approve"), strangerToken, null, 403);
        call(put("/api/orders/" + id + "/approve"), salesToken, null, 403);
        call(put("/api/orders/" + id + "/approve"), managerToken, null, 200);
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("48");
        JsonNode filtered = call(get("/api/orders").param("dealerId", dealer.getId().toString())
                .param("status", "RECORDED").param("page", "0").param("size", "1"), managerToken, null, 200);
        assertThat(filtered.get("totalAmount").decimalValue()).isEqualByComparingTo("960000");
        assertThat(filtered.get("totalElements").asInt()).isEqualTo(1);
        assertThat(filtered.get("data").size()).isEqualTo(1);
        MvcResult pdf = mvc.perform(get("/api/orders/" + id + "/pdf")
                .header("Authorization", "Bearer " + managerToken)).andReturn();
        assertThat(pdf.getResponse().getStatus()).isEqualTo(200);
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");
        assertThat(call(get("/api/dealers/" + dealer.getId() + "/purchase-history"), salesToken, null, 200)
                .get("data").size()).isEqualTo(1);
        call(put("/api/orders/" + id + "/cancel"), salesToken, Map.of("reason", "Đại lý đổi lịch"), 200);
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void adminWithoutDealerLinkCanSelectADealerThroughoutThePortal() throws Exception {
        String admin = login("admin");
        assertThat(users.findByUsername("admin").orElseThrow().getDealer()).isNull();
        JsonNode options = call(get("/api/portal/dealers"), admin, null, 200).get("data");
        assertThat(options.findValues("id").stream().map(JsonNode::asLong)).contains(dealer.getId());
        for (String path : List.of("profile", "catalog", "debt", "delivery-points", "orders")) {
            call(get("/api/portal/" + path).param("dealerId", dealer.getId().toString()), admin, null, 200);
            call(get("/api/portal/" + path), admin, null, 400);
        }
        call(get("/api/portal/profile").param("dealerId", "-1"), admin, null, 400);
        JsonNode created = call(post("/api/portal/orders").param("dealerId", dealer.getId().toString()),
                admin, orderBody(1, true), 201).get("data");
        long id = created.get("id").asLong();
        assertThat(created.get("dealerId").asLong()).isEqualTo(dealer.getId());
        assertThat(created.get("createdBy").asText()).isEqualTo("admin");
        assertThat(created.get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(created.get("totalAmount").decimalValue()).isEqualByComparingTo("480000");
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("0");
        JsonNode orders = call(get("/api/portal/orders").param("dealerId", dealer.getId().toString()),
                admin, null, 200).get("data");
        assertThat(orders.size()).isEqualTo(1);
        call(get("/api/portal/orders/" + id).param("dealerId", dealer.getId().toString()), admin, null, 200);
        Dealer other = dealers.save(Dealer.builder().code("OTHER-" + product.getCode()).name("Đại lý khác")
                .customerGroup(CustomerGroup.DAI_LY_CAP_2).warehouse(warehouse).build());
        assertThat(call(get("/api/portal/orders").param("dealerId", other.getId().toString()), admin, null, 200)
                .get("data").isEmpty()).isTrue();
        call(get("/api/portal/orders/" + id).param("dealerId", other.getId().toString()), admin, null, 403);
    }

    @Test
    void dealerCannotSelectAnotherDealerOrReadAdminOptions() throws Exception {
        Dealer other = dealers.save(Dealer.builder().code("OTHER-" + product.getCode()).name("Đại lý khác")
                .customerGroup(CustomerGroup.DAI_LY_CAP_2).warehouse(warehouse).build());
        call(get("/api/portal/dealers"), dealerToken, null, 403);
        for (String path : List.of("profile", "catalog", "products", "debt", "delivery-points", "orders", "orders/1")) {
            call(get("/api/portal/" + path).param("dealerId", other.getId().toString()), dealerToken, null, 403);
        }
        call(post("/api/portal/orders").param("dealerId", other.getId().toString()), dealerToken, orderBody(1, false), 403);
        call(get("/api/portal/profile").param("dealerId", dealer.getId().toString()), dealerToken, null, 200);
        Set<Permission> before = new HashSet<>(matrix.getPermissions(ERole.ROLE_DEALER));
        try {
            matrix.updateRolePermissions(ERole.ROLE_DEALER, EnumSet.of(Permission.ORDER_CREATE, Permission.PRODUCT_VIEW));
            for (String path : List.of("profile", "debt", "delivery-points", "orders", "orders/1")) {
                call(get("/api/portal/" + path).param("dealerId", dealer.getId().toString()), dealerToken, null, 403);
            }
        } finally {
            matrix.updateRolePermissions(ERole.ROLE_DEALER, before);
        }
    }

    @Test
    void receiptsAndTransfersKeepPhysicalStockSeparateFromDraftsAndTransit() throws Exception {
        String admin = login("admin");
        Supplier supplier = suppliers.save(Supplier.builder().code("NCC-" + product.getCode()).name("NCC kiểm thử").build());
        Map<String, Object> receipt = Map.of("warehouseId", warehouse.getId(), "supplierId", supplier.getId(),
                "documentNumber", "PN-TEST", "status", "DRAFT", "items",
                List.of(Map.of("productId", product.getId(), "unit", "thùng", "quantity", 2)));
        JsonNode draft = call(post("/api/warehouse/import"), admin, receipt, 200).get("data");
        assertThat(stock().getBaseQuantity()).isEqualByComparingTo("240");
        long receiptId = draft.get("id").asLong();
        call(post("/api/warehouse/vouchers/" + receiptId + "/confirm"), admin, null, 200);
        assertThat(stock().getBaseQuantity()).isEqualByComparingTo("288");
        call(put("/api/warehouse/vouchers/" + receiptId), admin, receipt, 400);
        JsonNode transfer = call(post("/api/warehouse/transfers"), admin,
                Map.of("fromWarehouseId", warehouse.getId(), "toWarehouseId", destination.getId(),
                        "items", List.of(Map.of("productId", product.getId(), "unit", "thùng", "quantity", 1))), 200).get("data");
        assertThat(stock().getBaseQuantity()).isEqualByComparingTo("264");
        var receiving = inventories.findByWarehouseIdAndProductId(destination.getId(), product.getId()).orElseThrow();
        assertThat(receiving.getBaseQuantity()).isEqualByComparingTo("0");
        assertThat(receiving.getInTransitQuantity()).isEqualByComparingTo("24");
        call(post("/api/warehouse/transfers/" + transfer.get("id").asLong() + "/receive"), admin, Map.of(), 200);
        receiving = inventories.findByWarehouseIdAndProductId(destination.getId(), product.getId()).orElseThrow();
        assertThat(receiving.getBaseQuantity()).isEqualByComparingTo("24");
        assertThat(receiving.getInTransitQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void concurrentSalesCannotReserveMoreThanPhysicalStock() {
        var first = CompletableFuture.supplyAsync(() -> createSale(8));
        var second = CompletableFuture.supplyAsync(() -> createSale(8));
        assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(201, 400);
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("192");
        assertThat(stock().getBaseQuantity()).isEqualByComparingTo("240");
    }

    @Test
    void dealerPermissionRevocationIsCheckedByTheServer() throws Exception {
        Set<Permission> before = new HashSet<>(matrix.getPermissions(ERole.ROLE_DEALER));
        try {
            matrix.updateRolePermissions(ERole.ROLE_DEALER, EnumSet.of(Permission.ORDER_VIEW));
            call(get("/api/portal/catalog"), dealerToken, null, 403);
            call(post("/api/portal/orders"), dealerToken, orderBody(1, false), 403);
        } finally {
            matrix.updateRolePermissions(ERole.ROLE_DEALER, before);
        }
    }

    @Test
    void adminCanSelectAndSaveTheDealerLinkedToAnAccount() throws Exception {
        String admin = login("admin");
        JsonNode options = call(get("/api/admin/assignment-options"), admin, null, 200);
        assertThat(options.at("/data/dealers").isArray()).isTrue();
        var account = users.findById(dealerUserId).orElseThrow();
        var body = new HashMap<String, Object>();
        body.put("username", account.getUsername()); body.put("email", account.getEmail());
        body.put("fullName", account.getFullName()); body.put("roles", List.of("ROLE_DEALER"));
        call(put("/api/admin/users/" + dealerUserId), admin, body, 400);
        body.put("dealerId", dealer.getId());
        assertThat(call(put("/api/admin/users/" + dealerUserId), admin, body, 200).at("/data/dealerId").asLong())
                .isEqualTo(dealer.getId());
    }

    @Test
    void transferUsesAvailableStockAndRequiresEveryDiscrepancyReason() throws Exception {
        String admin = login("admin");
        var inventory = stock(); inventory.setReservedQuantity(new BigDecimal("216")); inventories.save(inventory);
        Map<String, Object> body = transferBody(2);
        call(post("/api/warehouse/transfers"), admin, body, 400);
        assertThat(stock().getBaseQuantity()).isEqualByComparingTo("240");
        JsonNode transfer = call(post("/api/warehouse/transfers"), admin, transferBody(1), 200).get("data");
        assertThat(stock().getBaseQuantity()).isEqualByComparingTo("216");
        assertThat(stock().getReservedQuantity()).isEqualByComparingTo("216");
        assertThat(targetStock().getBaseQuantity()).isZero();
        assertThat(targetStock().getInTransitQuantity()).isEqualByComparingTo("24");
        long id = transfer.get("id").asLong(), itemId = transfer.at("/items/0/id").asLong();
        String receiveUrl = "/api/warehouse/transfers/" + id + "/receive";
        call(post(receiveUrl), admin, Map.of("items", List.of(Map.of("itemId", itemId, "receivedQuantity", "0.75"))), 400);
        assertThat(targetStock().getBaseQuantity()).isZero();
        assertThat(targetStock().getInTransitQuantity()).isEqualByComparingTo("24");
        JsonNode received = call(post(receiveUrl), admin, Map.of("items", List.of(Map.of("itemId", itemId,
                "receivedQuantity", "0.75", "discrepancyReason", "Thiếu khi bàn giao"))), 200).get("data");
        assertThat(received.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(received.at("/items/0/discrepancyBaseQuantity").decimalValue()).isEqualByComparingTo("6");
        assertThat(received.at("/items/0/discrepancyReason").asText()).isEqualTo("Thiếu khi bàn giao");
        assertThat(targetStock().getBaseQuantity()).isEqualByComparingTo("18");
        assertThat(targetStock().getInTransitQuantity()).isZero();
        call(post(receiveUrl), admin, Map.of(), 400);
        call(post("/api/warehouse/transfers/" + id + "/cancel"), admin, Map.of("reason", "Lặp"), 400);
        assertThat(targetStock().getBaseQuantity()).isEqualByComparingTo("18");
    }

    @Test
    void transferRejectsForgedLinesDuplicatesAndInvalidQuantitiesWithoutStockChanges() throws Exception {
        String admin = login("admin");
        var duplicate = new HashMap<>(transferBody(1));
        var line = Map.of("productId", product.getId(), "unit", "thùng", "quantity", 1);
        duplicate.put("items", List.of(line, line));
        call(post("/api/warehouse/transfers"), admin, duplicate, 400);
        var invalid = new HashMap<>(transferBody(1));
        invalid.put("items", List.of(Map.of("productId", product.getId(), "quantity", "0.00001")));
        call(post("/api/warehouse/transfers"), admin, invalid, 400);
        assertThat(stock().getBaseQuantity()).isEqualByComparingTo("240");
        JsonNode transfer = call(post("/api/warehouse/transfers"), admin, transferBody(1), 200).get("data");
        String url = "/api/warehouse/transfers/" + transfer.get("id").asLong() + "/receive";
        long itemId = transfer.at("/items/0/id").asLong();
        call(post(url), admin, Map.of("items", List.of(Map.of("itemId", 999999999, "receivedQuantity", 1))), 400);
        call(post(url), admin, Map.of("items", List.of(Map.of("itemId", itemId, "productId", 999999999, "receivedQuantity", 1))), 400);
        call(post(url), admin, Map.of("items", List.of(Map.of("itemId", itemId, "receivedQuantity", 1), Map.of("itemId", itemId, "receivedQuantity", 1))), 400);
        call(post(url), admin, Map.of("items", List.of(Map.of("itemId", itemId, "receivedQuantity", -1))), 400);
        assertThat(targetStock().getBaseQuantity()).isZero();
        assertThat(targetStock().getInTransitQuantity()).isEqualByComparingTo("24");
        call(post("/api/warehouse/transfers/" + transfer.get("id").asLong() + "/cancel"), admin, Map.of(), 400);
        call(post("/api/warehouse/transfers/" + transfer.get("id").asLong() + "/cancel"), admin, Map.of("reason", "Không còn nhu cầu"), 200);
        assertThat(stock().getBaseQuantity()).isEqualByComparingTo("240");
        assertThat(targetStock().getInTransitQuantity()).isZero();
    }

    @Test
    void transferMatrixAndWarehouseScopeApplyToLiveTokensWithoutExtraProductPermissions() throws Exception {
        Set<Permission> before = new HashSet<>(matrix.getPermissions(ERole.ROLE_VIEWER));
        try {
            matrix.updateRolePermissions(ERole.ROLE_VIEWER, EnumSet.of(Permission.WAREHOUSE_TRANSFER));
            User sender = createUser("sender-" + UUID.randomUUID(), ERole.ROLE_VIEWER, null);
            sender.setWarehouses(Set.of(warehouse)); users.save(sender);
            String token = login(sender.getUsername());
            JsonNode options = call(get("/api/warehouse/transfers/options"), token, null, 200).get("data");
            assertThat(options.get("sourceWarehouses").size()).isEqualTo(1);
            assertThat(options.at("/sourceWarehouses/0/id").asLong()).isEqualTo(warehouse.getId());
            JsonNode catalog = call(get("/api/warehouse/transfers/products").param("warehouseId", warehouse.getId().toString())
                    .param("keyword", product.getCode()), token, null, 200).at("/data/content/0");
            assertThat(catalog.get("productId").asLong()).isEqualTo(product.getId());
            assertThat(catalog.has("price")).isFalse(); assertThat(catalog.has("costPrice")).isFalse();
            call(get("/api/warehouse/transfers/products").param("warehouseId", destination.getId().toString()), token, null, 403);
            var forged = new HashMap<>(transferBody(1));
            forged.put("fromWarehouseId", destination.getId()); forged.put("toWarehouseId", warehouse.getId());
            call(post("/api/warehouse/transfers"), token, forged, 403);
            JsonNode transfer = call(post("/api/warehouse/transfers"), token, transferBody(1), 200).get("data");
            long id = transfer.get("id").asLong();
            assertThat(transfer.get("canReceive").asBoolean()).isFalse(); assertThat(transfer.get("canCancel").asBoolean()).isTrue();
            call(post("/api/warehouse/transfers/" + id + "/receive"), token, Map.of(), 403);
            User receiver = createUser("receiver-" + UUID.randomUUID(), ERole.ROLE_VIEWER, null);
            receiver.setWarehouses(Set.of(destination)); users.save(receiver);
            String receiverToken = login(receiver.getUsername());
            call(post("/api/warehouse/transfers/" + id + "/cancel"), receiverToken, Map.of("reason", "Sai kho"), 403);
            matrix.updateRolePermissions(ERole.ROLE_VIEWER, EnumSet.of(Permission.WAREHOUSE_VIEW));
            call(post("/api/warehouse/transfers"), token, transferBody(1), 403);
            call(post("/api/warehouse/transfers/" + id + "/receive"), receiverToken, Map.of(), 403);
            assertThat(call(get("/api/warehouse/transfers/" + id), receiverToken, null, 200).at("/data/canReceive").asBoolean()).isFalse();
            matrix.updateRolePermissions(ERole.ROLE_VIEWER, Set.of());
            call(get("/api/warehouse/transfers/options"), token, null, 403);
            call(get("/api/warehouse/transfers/search"), token, null, 403);
            call(get("/api/warehouse/transfers/" + id), token, null, 403);
            matrix.updateRolePermissions(ERole.ROLE_VIEWER, EnumSet.of(Permission.WAREHOUSE_TRANSFER));
            call(post("/api/warehouse/transfers/" + id + "/receive"), receiverToken, Map.of(), 200);
            sender = users.findById(sender.getId()).orElseThrow(); sender.setWarehouses(Set.of());
            sender.setRoles(Set.of(roles.findByName(ERole.ROLE_WAREHOUSE_STAFF).orElseThrow())); users.save(sender);
            Set<Permission> staffBefore = new HashSet<>(matrix.getPermissions(ERole.ROLE_WAREHOUSE_STAFF));
            try {
                matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_STAFF, EnumSet.of(Permission.WAREHOUSE_TRANSFER));
                assertThat(call(get("/api/warehouse/transfers/search"), token, null, 200).at("/data/totalElements").asInt()).isZero();
                call(post("/api/warehouse/transfers"), token, transferBody(1), 403);
            } finally { matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_STAFF, staffBefore); }
        } finally { matrix.updateRolePermissions(ERole.ROLE_VIEWER, before); }
    }

    @Test
    void transferRacesDoNotOversellOrReceiveTwice() throws Exception {
        String admin = login("admin");
        var inventory = stock(); inventory.setBaseQuantity(new BigDecimal("24")); inventories.save(inventory);
        var first = CompletableFuture.supplyAsync(() -> transferRequestStatus("/api/warehouse/transfers", admin, transferBody(1)));
        var second = CompletableFuture.supplyAsync(() -> transferRequestStatus("/api/warehouse/transfers", admin, transferBody(1)));
        assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(200, 400);
        assertThat(stock().getBaseQuantity()).isZero();
        assertThat(targetStock().getInTransitQuantity()).isEqualByComparingTo("24");
        JsonNode list = call(get("/api/warehouse/transfers/search").param("fromWarehouseId", warehouse.getId().toString()), admin, null, 200);
        long id = list.at("/data/content/0/id").asLong();
        String url = "/api/warehouse/transfers/" + id + "/receive";
        var a = CompletableFuture.supplyAsync(() -> transferRequestStatus(url, admin, Map.of()));
        var b = CompletableFuture.supplyAsync(() -> transferRequestStatus(url, admin, Map.of()));
        assertThat(List.of(a.join(), b.join())).containsExactlyInAnyOrder(200, 400);
        assertThat(targetStock().getBaseQuantity()).isEqualByComparingTo("24");
        assertThat(targetStock().getInTransitQuantity()).isZero();
    }

    @Test
    void transferCompetesSafelyWithOrderReservationAndReceiveCompetesWithCancel() throws Exception {
        String admin = login("admin");
        var inventory = stock(); inventory.setBaseQuantity(new BigDecimal("24")); inventories.save(inventory);
        var sale = CompletableFuture.supplyAsync(() -> createSale(1));
        var transfer = CompletableFuture.supplyAsync(() -> transferRequestStatus("/api/warehouse/transfers", admin, transferBody(1)));
        int saleStatus = sale.join(), transferStatus = transfer.join();
        assertThat((saleStatus == 201 && transferStatus == 400) || (saleStatus == 400 && transferStatus == 200)).isTrue();
        assertThat(stock().getBaseQuantity().subtract(stock().getReservedQuantity())).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        inventory = stock(); inventory.setBaseQuantity(new BigDecimal("240")); inventories.save(inventory);
        JsonNode created = call(post("/api/warehouse/transfers"), admin, transferBody(1), 200).get("data");
        BigDecimal beforeSource = stock().getBaseQuantity(), beforeTarget = targetStock().getBaseQuantity();
        BigDecimal beforeTransit = targetStock().getInTransitQuantity();
        String url = "/api/warehouse/transfers/" + created.get("id").asLong();
        var receive = CompletableFuture.supplyAsync(() -> transferRequestStatus(url + "/receive", admin, Map.of()));
        var cancel = CompletableFuture.supplyAsync(() -> transferRequestStatus(url + "/cancel", admin, Map.of("reason", "Dừng điều chuyển")));
        int received = receive.join(), cancelled = cancel.join();
        assertThat(List.of(received, cancelled)).containsExactlyInAnyOrder(200, 400);
        assertThat(targetStock().getInTransitQuantity()).isEqualByComparingTo(beforeTransit.subtract(new BigDecimal("24")));
        assertThat(stock().getBaseQuantity()).isEqualByComparingTo(cancelled == 200 ? beforeSource.add(new BigDecimal("24")) : beforeSource);
        assertThat(targetStock().getBaseQuantity()).isEqualByComparingTo(received == 200 ? beforeTarget.add(new BigDecimal("24")) : beforeTarget);
    }

    @Test
    void transferPartialReceiptIsAtomicAndKeepsFrozenConversionsIncludingOverage() throws Exception {
        String admin = login("admin");
        Product second = products.save(Product.builder().code("SECOND-" + UUID.randomUUID()).name("Hàng thứ hai")
                .category(product.getCategory()).unit("lon").price(BigDecimal.ONE).active(true).build());
        inventories.save(WarehouseInventory.builder().warehouse(warehouse).product(second).baseUnit("lon").baseQuantity(new BigDecimal("20")).build());
        var body = new HashMap<>(transferBody(1));
        body.put("items", List.of(Map.of("productId", product.getId(), "unit", "thùng", "quantity", 1),
                Map.of("productId", second.getId(), "unit", "lon", "quantity", 2)));
        JsonNode transfer = call(post("/api/warehouse/transfers"), admin, body, 200).get("data");
        String url = "/api/warehouse/transfers/" + transfer.get("id").asLong() + "/receive";
        long firstId = transfer.at("/items/0/id").asLong(), secondId = transfer.at("/items/1/id").asLong();
        var first = Map.of("itemId", firstId, "receivedQuantity", "1.25", "discrepancyReason", "Thừa khi đối chiếu");
        call(post(url), admin, Map.of("items", List.of(first)), 400);
        call(post(url), admin, Map.of("items", List.of(first, Map.of("itemId", secondId, "receivedQuantity", 0))), 400);
        assertThat(targetStock().getBaseQuantity()).isZero();
        assertThat(targetStock().getInTransitQuantity()).isEqualByComparingTo("24");
        var conversion = conversions.findByProductIdAndUnitNameIgnoreCase(product.getId(), "thùng").orElseThrow();
        conversion.setConversionFactor(new BigDecimal("30")); conversions.save(conversion);
        JsonNode received = call(post(url), admin, Map.of("items", List.of(first,
                Map.of("itemId", secondId, "receivedQuantity", 0, "discrepancyReason", "Hỏng khi chuyển"))), 200).get("data");
        assertThat(targetStock().getBaseQuantity()).isEqualByComparingTo("30"); // 1.25 x snapshot 24, not current 30
        assertThat(received.at("/items/0/discrepancyBaseQuantity").decimalValue()).isEqualByComparingTo("-6");
        var secondTarget = inventories.findByWarehouseIdAndProductId(destination.getId(), second.getId()).orElseThrow();
        assertThat(secondTarget.getBaseQuantity()).isZero(); assertThat(secondTarget.getInTransitQuantity()).isZero();
        call(get("/api/warehouse/transfers/search").param("page", "-1"), admin, null, 400);
        call(get("/api/warehouse/transfers/products").param("warehouseId", warehouse.getId().toString()).param("size", "201"), admin, null, 400);
    }

    private Map<String, Object> transferBody(int quantity) {
        return Map.of("fromWarehouseId", warehouse.getId(), "toWarehouseId", destination.getId(),
                "items", List.of(Map.of("productId", product.getId(), "unit", "thùng", "quantity", quantity)));
    }

    private WarehouseInventory targetStock() {
        return inventories.findByWarehouseIdAndProductId(destination.getId(), product.getId()).orElseThrow();
    }

    private int transferRequestStatus(String url, String token, Object body) {
        try { return mvc.perform(post(url).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn().getResponse().getStatus(); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private int createSale(int quantity) {
        try {
            return mvc.perform(post("/api/orders").header("Authorization", "Bearer " + salesToken)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(orderBody(quantity, false))))
                    .andReturn().getResponse().getStatus();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Map<String, Object> orderBody(int quantity, boolean forgedPrice) {
        var line = new HashMap<String, Object>();
        line.put("productId", product.getId()); line.put("unit", "thùng"); line.put("quantity", quantity);
        if (forgedPrice) line.put("price", 1);
        return Map.of("dealerId", dealer.getId(), "warehouseId", warehouse.getId(),
                "expectedDeliveryDate", LocalDate.now().plusDays(1).toString(), "items", List.of(line));
    }

    private User createUser(String username, ERole role, Dealer linkedDealer) {
        return users.save(User.builder().username(username).email(username + "@example.invalid")
                .fullName(username).password(passwords.encode("SprintTest2026"))
                .roles(Set.of(roles.findByName(role).orElseThrow())).dealer(linkedDealer)
                .warehouses(Set.of(warehouse, destination)).active(true).build());
    }

    private String login(String username) throws Exception {
        return call(post("/api/auth/login"), null, Map.of("username", username, "password", "SprintTest2026"), 200)
                .get("accessToken").asText();
    }

    private WarehouseInventory stock() {
        return inventories.findByWarehouseIdAndProductId(warehouse.getId(), product.getId()).orElseThrow();
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, Object body, int expected) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        String payload = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(response.getStatus()).as("%s", payload).isEqualTo(expected);
        return json.readTree(payload);
    }
}
