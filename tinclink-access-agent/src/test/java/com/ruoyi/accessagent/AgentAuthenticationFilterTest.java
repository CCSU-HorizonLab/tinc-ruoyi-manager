package com.ruoyi.accessagent;

import com.ruoyi.common.tinc.access.AccessRuntimeSummary;
import com.ruoyi.common.tinc.access.TincAccessRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentAuthenticationFilterTest {
    private static final String SECRET = "12345678901234567890123456789012";
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AgentProperties properties = new AgentProperties();
        properties.setId("access-test");
        properties.setSecret(SECRET);
        properties.afterPropertiesSet();
        TincAccessRuntime runtime = mock(TincAccessRuntime.class);
        when(runtime.inspectRuntime()).thenReturn(new AccessRuntimeSummary());
        AgentController controller = new AgentController(runtime, properties);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AgentExceptionHandler())
                .addFilters(new AgentAuthenticationFilter(properties))
                .build();
    }

    @Test
    void missingSecretReturns401() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AGENT_UNAUTHORIZED")));
    }

    @Test
    void wrongSecretReturns403() throws Exception {
        mvc.perform(get("/api/v1/health").header("Authorization", "Bearer wrong-secret"))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctSecretReturnsSanitizedHealth() throws Exception {
        mvc.perform(get("/api/v1/health").header("Authorization", "Bearer " + SECRET))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("access-test")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(SECRET))));
    }

    @Test
    void arbitraryExecEndpointDoesNotExist() throws Exception {
        mvc.perform(post("/api/v1/exec").header("Authorization", "Bearer " + SECRET))
                .andExpect(status().isNotFound());
    }
}
