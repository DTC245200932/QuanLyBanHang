package com.quanlybanhang.config;

import com.quanlybanhang.model.ERole;
import com.quanlybanhang.model.Role;
import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.RoleRepository;
import com.quanlybanhang.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {
    @Mock RoleRepository roles;
    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks DataInitializer initializer;

    @Test
    void existingAdminDoesNotRequireBootstrapSecretsOrCreateAccounts() {
        when(users.existsByUsername("admin")).thenReturn(true);
        for (ERole role : ERole.values()) when(roles.existsByName(role)).thenReturn(true);
        initializer.run();
        verify(users, never()).save(any());
        verifyNoInteractions(passwordEncoder);
        verify(roles, never()).save(any());
    }

    @Test
    void bootstrapRequiresRealContactInformationBeforeWritingAnything() {
        ReflectionTestUtils.setField(initializer, "bootstrapAdminPassword", "test-bootstrap-password");
        assertThatThrownBy(() -> initializer.run()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BOOTSTRAP_ADMIN_EMAIL");
        verifyNoInteractions(roles, passwordEncoder);
        verify(users, never()).save(any());
    }

    @Test
    void bootstrapUsesConfiguredEmailAndEncodedPasswordWithoutFakePhone() {
        ReflectionTestUtils.setField(initializer, "bootstrapAdminPassword", "test-bootstrap-password");
        ReflectionTestUtils.setField(initializer, "bootstrapAdminEmail", " admin@example.invalid ");
        Role adminRole = new Role(ERole.ROLE_ADMIN);
        when(roles.findByName(ERole.ROLE_ADMIN)).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("test-bootstrap-password")).thenReturn("encoded-password");
        initializer.run();
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.invalid");
        assertThat(saved.getValue().getPassword()).isEqualTo("encoded-password");
        assertThat(saved.getValue().getPhone()).isNull();
        assertThat(saved.getValue().getRoles()).containsExactly(adminRole);
        verify(roles, times(ERole.values().length)).save(any(Role.class));
    }
}
