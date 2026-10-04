package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.SupplierRequest;
import com.quanlybanhang.model.Supplier;
import com.quanlybanhang.repository.SupplierRepository;
import com.quanlybanhang.repository.WarehouseVoucherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {
    @Mock SupplierRepository supplierRepository;
    @Mock WarehouseVoucherRepository warehouseVoucherRepository;
    @InjectMocks SupplierServiceImpl service;

    @Test
    void createPersistsAllFieldsOfferedByTheForm() {
        SupplierRequest request = new SupplierRequest();
        request.setCode("NCC001"); request.setName("Supplier"); request.setTaxCode("1234567890");
        request.setContactPerson("Contact"); request.setContactPhone("0901234567");
        request.setEmail("contact@example.test"); request.setPaymentTerms("NET30");
        request.setAddress("Test address"); request.setNotes("Test notes");
        when(supplierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.createSupplier(request);
        var captor = org.mockito.ArgumentCaptor.forClass(Supplier.class);
        verify(supplierRepository).save(captor.capture());
        assertThat(captor.getValue().getContactPhone()).isEqualTo("0901234567");
        assertThat(captor.getValue().getEmail()).isEqualTo("contact@example.test");
        assertThat(captor.getValue().getAddress()).isEqualTo("Test address");
        assertThat(captor.getValue().getNotes()).isEqualTo("Test notes");
        assertThat(response.getContactPerson()).isEqualTo("Contact");
        assertThat(response.getActive()).isTrue();
    }

    @Test
    void supplierWithVouchersCannotBeDeletedButCanBeDeactivated() {
        Supplier supplier = Supplier.builder().id(1L).code("NCC001").name("Supplier")
                .contactPerson("Contact").notes("Keep history").active(true).build();
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(warehouseVoucherRepository.existsBySupplierId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteSupplier(1L)).isInstanceOf(RuntimeException.class);
        verify(supplierRepository, never()).delete(any());
        when(supplierRepository.save(supplier)).thenReturn(supplier);
        var response = service.updateStatus(1L, false);
        assertThat(response.getActive()).isFalse();
        assertThat(response.getContactPerson()).isEqualTo("Contact");
        assertThat(response.getNotes()).isEqualTo("Keep history");
        assertThat(service.updateStatus(1L, true).getActive()).isTrue();
    }
}
