package com.quanlybanhang.config;

import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionConfigTest {
    private RolePermissionConfig permissions;

    @BeforeEach
    void setUp() {
        permissions = new RolePermissionConfig();
    }

    @Test
    void salesManagerAloneCanSeeCostAndMargin() {
        assertThat(permissions.hasPermission(ERole.ROLE_SALES_MANAGER, Permission.FINANCE_VIEW_COST_PRICE)).isTrue();
        assertThat(permissions.hasPermission(ERole.ROLE_SALES_MANAGER, Permission.FINANCE_VIEW_PROFIT_MARGIN)).isTrue();
        assertThat(permissions.hasPermission(ERole.ROLE_ADMIN, Permission.FINANCE_VIEW_COST_PRICE)).isFalse();
        assertThat(permissions.hasPermission(ERole.ROLE_ACCOUNTANT, Permission.FINANCE_VIEW_COST_PRICE)).isFalse();
        assertThat(permissions.hasPermission(ERole.ROLE_WAREHOUSE_MANAGER, Permission.FINANCE_VIEW_COST_PRICE)).isFalse();
    }

    @Test
    void salesCanCreateOrdersButCannotChangeInventory() {
        assertThat(permissions.hasPermission(ERole.ROLE_SALES, Permission.ORDER_CREATE)).isTrue();
        assertThat(permissions.hasPermission(ERole.ROLE_SALES, Permission.WAREHOUSE_ADJUST)).isFalse();
    }

    @Test
    void warehouseStaffCanMoveStockButCannotSeeFinancialData() {
        assertThat(permissions.hasPermission(ERole.ROLE_WAREHOUSE_STAFF, Permission.WAREHOUSE_IMPORT)).isTrue();
        assertThat(permissions.hasPermission(ERole.ROLE_WAREHOUSE_STAFF, Permission.WAREHOUSE_EXPORT)).isTrue();
        assertThat(permissions.hasPermission(ERole.ROLE_WAREHOUSE_STAFF, Permission.FINANCE_VIEW_DEBT)).isFalse();
    }

    @Test
    void accountantCanManageDebtWithoutCostPrice() {
        assertThat(permissions.hasPermission(ERole.ROLE_ACCOUNTANT, Permission.FINANCE_MANAGE)).isTrue();
        assertThat(permissions.hasPermission(ERole.ROLE_ACCOUNTANT, Permission.FINANCE_VIEW_COST_PRICE)).isFalse();
    }
}
