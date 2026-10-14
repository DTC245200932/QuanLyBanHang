package com.quanlybanhang.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DemoDataInitializerTest {
    @Mock DemoDataSeeder seeder;
    @InjectMocks DemoDataInitializer initializer;

    @Test
    void disabledByDefault() {
        initializer.run();
        verifyNoInteractions(seeder);
    }

    @Test
    void restoresLegacyUserSwitch() {
        ReflectionTestUtils.setField(initializer, "seedDemoUsers", true);
        ReflectionTestUtils.setField(initializer, "demoUserPassword", "configured-password");
        initializer.run();
        verify(seeder).seed(true, false, "configured-password");
    }

    @Test
    void dataCanBeEnabledWithoutUsersOrPassword() {
        ReflectionTestUtils.setField(initializer, "seedDemoData", true);
        initializer.run();
        verify(seeder).seed(false, true, null);
    }
}
