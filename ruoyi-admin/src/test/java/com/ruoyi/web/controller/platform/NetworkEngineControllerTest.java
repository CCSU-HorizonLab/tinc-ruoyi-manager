package com.ruoyi.web.controller.platform;

import com.ruoyi.platform.NetworkEngineRegistry;
import com.ruoyi.tinc.platform.TincNetworkEngineProvider;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NetworkEngineControllerTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsOnlyRegisteredCapabilityMetadataToAuthenticatedManagementUser() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(SecurityConfig.class, NetworkEngineController.class,
                    NetworkEngineRegistry.class, TincNetworkEngineProvider.class);
            context.refresh();
            NetworkEngineController controller = context.getBean(NetworkEngineController.class);
            assertThrows(AuthenticationCredentialsNotFoundException.class, controller::list);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", "unused", Collections.emptyList()));
            MockMvc http = MockMvcBuilders.standaloneSetup(controller).build();
            http.perform(get("/platform/network-engines"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].engineId").value("tinc"))
                    .andExpect(jsonPath("$.data[0].capabilities[0]").exists())
                    .andExpect(jsonPath("$.data[0].runtimeAvailability").doesNotExist());
        }
    }

    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class SecurityConfig { }
}
