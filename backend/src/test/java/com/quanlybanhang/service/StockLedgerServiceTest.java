package com.quanlybanhang.service;

import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockLedgerServiceTest {
    @Mock StockLedgerRepository ledger;
    @Mock WarehouseRepository warehouses;
    @Mock CategoryRepository categories;
    @Mock UserRepository users;
    @InjectMocks StockLedgerService service;
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private void auth(String... grants) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", "unused",
                Arrays.stream(grants).map(SimpleGrantedAuthority::new).toList()));
    }
    @Test void warehouseRoleNeverGrantsMissingViewPermission() {
        auth("ROLE_WAREHOUSE_MANAGER", "WAREHOUSE_MANAGE");
        assertThatThrownBy(service::options).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(ledger, warehouses, categories, users);
    }
    @Test void invalidPaginationAndKeywordCannotReachRepository() {
        for (int[] paging : List.of(new int[]{-1,20}, new int[]{0,0}, new int[]{0,201})) {
            assertThatThrownBy(() -> service.search(null,null,null,null,paging[0],paging[1]))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.search(null,null,"x".repeat(151),null,0,20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(-1L,null,null,null,0,20)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ledger, warehouses, categories, users);
    }
    @Test void searchEscapesSqlWildcardsAndPassesEveryFilterToPagedQuery() {
        auth("ROLE_VIEWER", "WAREHOUSE_VIEW");
        Warehouse w = Warehouse.builder().id(3L).build();
        when(warehouses.findAllByOrderByCodeAsc()).thenReturn(List.of(w));
        when(users.findByUsername("reader")).thenReturn(Optional.of(User.builder().warehouses(Set.of(w)).build()));
        when(ledger.findLedger(anyList(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
        service.search(3L, 8L, " A%_! ", StockStatus.FULLY_RESERVED, 2, 50);
        verify(ledger).findLedger(eq(List.of(3L)), eq(3L), eq(8L), eq("%a!%!_!!%"), eq("FULLY_RESERVED"),
                argThat(p -> p.getPageNumber() == 2 && p.getPageSize() == 50));
    }
    @Test void explicitAssignmentsRestrictEvenNonWarehouseRole() {
        auth("ROLE_VIEWER", "WAREHOUSE_VIEW");
        Warehouse a = Warehouse.builder().id(1L).build(), b = Warehouse.builder().id(2L).build();
        when(warehouses.findAllByOrderByCodeAsc()).thenReturn(List.of(a,b));
        when(users.findByUsername("reader")).thenReturn(Optional.of(User.builder().warehouses(Set.of(a)).build()));
        when(categories.findAllByOrderByLevelAscNameAsc()).thenReturn(List.of());
        assertThat(service.options().warehouses()).extracting(o -> o.id()).containsExactly(1L);
        assertThatThrownBy(() -> service.search(2L,null,null,null,0,20)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(ledger);
    }
    @Test void warehouseRoleWithoutAssignedWarehouseGetsNoDataNotGlobalAccess() {
        auth("ROLE_WAREHOUSE_STAFF", "WAREHOUSE_VIEW");
        when(warehouses.findAllByOrderByCodeAsc()).thenReturn(List.of(Warehouse.builder().id(1L).build()));
        when(users.findByUsername("reader")).thenReturn(Optional.of(User.builder().warehouses(Set.of()).build()));
        assertThat(service.search(null,null,null,null,0,20)).isEmpty();
        verifyNoInteractions(ledger);
    }
}
