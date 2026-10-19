package com.quanlybanhang.config;

import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.Permission;
import com.quanlybanhang.model.RolePermission;
import com.quanlybanhang.repository.RolePermissionRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs on H2 by default; permission.test.datasource.url may point to an empty, disposable MySQL database. */
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=update", "spring.jpa.show-sql=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:role-permissions-legacy.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RolePermissionConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RolePermissionPersistenceTest {
    @Autowired RolePermissionRepository repository;
    @Autowired RolePermissionConfig startupMatrix;
    @Autowired DataSource dataSource;

    private List<RolePermission> startupRows;
    private Map<ERole, Set<Permission>> startupPermissions;
    private final Map<String, Integer> columnTypes = new HashMap<>();
    private RolePermissionConfig matrix;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getProperty("permission.test.datasource.url",
                "jdbc:h2:mem:permission-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1");
        boolean mysql = url.startsWith("jdbc:mysql:");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getProperty(
                "permission.test.datasource.username", mysql ? "root" : "sa"));
        properties.add("spring.datasource.password", () -> System.getProperty("permission.test.datasource.password", ""));
        properties.add("spring.datasource.driver-class-name", () -> mysql ? "com.mysql.cj.jdbc.Driver" : "org.h2.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> mysql
                ? "org.hibernate.dialect.MySQLDialect" : "org.hibernate.dialect.H2Dialect");
    }

    @BeforeAll
    void captureMigratedSchemaBeforeFixtures() throws Exception {
        startupRows = repository.findAll();
        startupPermissions = startupMatrix.getAllPermissions();
        try (var connection = dataSource.getConnection();
             var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "%", "%")) {
            while (columns.next()) {
                if ("role_permissions".equalsIgnoreCase(columns.getString("TABLE_NAME"))) {
                    columnTypes.put(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT), columns.getInt("DATA_TYPE"));
                }
            }
        }
    }

    @BeforeEach
    void fixtures() {
        repository.replaceMatrix(Map.of(
                ERole.ROLE_ADMIN, EnumSet.allOf(Permission.class),
                ERole.ROLE_SALES, EnumSet.of(Permission.ORDER_VIEW)));
        matrix = reload();
    }

    @Test
    void upgradesLegacyEnumColumnsWithoutRewritingExistingGrants() {
        assertThat(columnTypes).containsEntry("role_name", Types.VARCHAR).containsEntry("permission", Types.VARCHAR);
        assertThat(startupRows).filteredOn(row -> row.getRole() != ERole.ROLE_ADMIN)
                .singleElement().satisfies(row -> {
                    assertThat(row.getId()).isEqualTo(1L);
                    assertThat(row.getRole()).isEqualTo(ERole.ROLE_SALES);
                    assertThat(row.getPermission()).isEqualTo(Permission.ORDER_VIEW);
                });
        assertThat(startupRows).filteredOn(row -> row.getRole() == ERole.ROLE_ADMIN)
                .extracting(RolePermission::getPermission).containsExactlyInAnyOrder(Permission.values());
        assertThat(startupPermissions.get(ERole.ROLE_SALES)).containsExactly(Permission.ORDER_VIEW);
        assertThat(startupPermissions.get(ERole.ROLE_VIEWER)).isEmpty();
        assertThat(startupPermissions.get(ERole.ROLE_ADMIN)).containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void persistsAllCurrentPermissionAndRoleNames() {
        Map<ERole, Set<Permission>> all = new EnumMap<>(ERole.class);
        for (ERole role : ERole.values()) all.put(role, EnumSet.allOf(Permission.class));
        repository.replaceMatrix(all);
        for (ERole role : ERole.values()) {
            assertThat(repository.findByRole(role)).extracting(RolePermission::getPermission)
                    .containsExactlyInAnyOrder(Permission.values());
        }
    }

    @Test
    void fullyRevokedRolesStayRevokedAfterReloadAndAdminRemainsAllowed() {
        // Old installations may have no persisted ADMIN rows at all.
        repository.replaceMatrix(Map.of(ERole.ROLE_SALES, EnumSet.of(Permission.ORDER_VIEW)));
        matrix = reload();
        matrix.updateRolePermissions(ERole.ROLE_SALES, Set.of());
        RolePermissionConfig restarted = reload();
        assertThat(repository.findByRole(ERole.ROLE_SALES)).isEmpty();
        assertThat(restarted.getPermissions(ERole.ROLE_SALES)).isEmpty();
        assertThat(restarted.hasPermission(ERole.ROLE_SALES, Permission.ORDER_CREATE)).isFalse();
        assertThat(repository.findByRole(ERole.ROLE_ADMIN)).hasSize(Permission.values().length);
        assertThat(restarted.hasPermission(ERole.ROLE_ADMIN, Permission.WAREHOUSE_MANAGE)).isTrue();
    }

    @Test
    void savesNewWarehousePermissionAndReloadsWithoutReplacingRows() {
        matrix.updateRolePermissions(ERole.ROLE_WAREHOUSE_MANAGER, EnumSet.of(Permission.WAREHOUSE_MANAGE));
        List<Long> before = repository.findAll().stream().map(RolePermission::getId).sorted().toList();
        assertThat(reload().getPermissions(ERole.ROLE_WAREHOUSE_MANAGER)).containsExactly(Permission.WAREHOUSE_MANAGE);
        assertThat(repository.findAll().stream().map(RolePermission::getId).sorted().toList()).isEqualTo(before);
    }

    @Test
    void failedRoleReplacementRollsBackTheDeleteAndAnyInserts() {
        assertThatThrownBy(() -> repository.replaceByRole(ERole.ROLE_SALES, invalidPermissions()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findByRole(ERole.ROLE_SALES)).extracting(RolePermission::getPermission)
                .containsExactly(Permission.ORDER_VIEW);
    }

    @Test
    void failedMatrixReplacementRollsBackEveryRole() {
        long before = repository.count();
        assertThatThrownBy(() -> repository.replaceMatrix(Map.of(ERole.ROLE_SALES, invalidPermissions())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.count()).isEqualTo(before);
        assertThat(repository.findByRole(ERole.ROLE_ADMIN)).hasSize(Permission.values().length);
        assertThat(repository.findByRole(ERole.ROLE_SALES)).extracting(RolePermission::getPermission)
                .containsExactly(Permission.ORDER_VIEW);
    }

    private Set<Permission> invalidPermissions() {
        return new HashSet<>(Arrays.asList(Permission.ORDER_CREATE, null));
    }

    private RolePermissionConfig reload() {
        RolePermissionConfig loaded = new RolePermissionConfig();
        loaded.setRolePermissionRepository(repository);
        return loaded;
    }
}
