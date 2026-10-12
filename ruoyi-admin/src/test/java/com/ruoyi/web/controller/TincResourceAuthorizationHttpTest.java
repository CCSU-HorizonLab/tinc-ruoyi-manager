package com.ruoyi.web.controller;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.transport.TincConfigTransport;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.tinc.runtime.TincRuntimeRouter;
import com.ruoyi.tinc.security.TincResourceAccess;
import com.ruoyi.tinc.security.mapper.TincResourceGrantMapper;
import com.ruoyi.tinc_network.service.ITincNetworkMangeService;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.service.ITincNodeMangeService;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc_server.service.IMangeServerService;
import com.ruoyi.web.controller.monitor.NetworkMonitorController;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.util.Collections;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring method-security proxies + real resource guard; no production database/Runtime. */
class TincResourceAuthorizationHttpTest {
    private AnnotationConfigApplicationContext context;
    private MockMvc http;
    private TincResourceGrantMapper grants;
    private ITincNodeMangeService nodes;
    private ITincNetworkMangeService networks;
    private IMangeServerService servers;
    private TincRuntimeRouter runtime;

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        grants = context.getBean(TincResourceGrantMapper.class);
        nodes = context.getBean(ITincNodeMangeService.class);
        networks = context.getBean(ITincNetworkMangeService.class);
        servers = context.getBean(IMangeServerService.class);
        runtime = context.getBean(TincRuntimeRouter.class);
        when(grants.activeUser(20L)).thenReturn(1);
        authenticate(20L, true);
        http = MockMvcBuilders.standaloneSetup(context.getBean(TincNodeMangeController.class),
                context.getBean(TincNetworkMangeController.class), context.getBean(MangeServerController.class),
                context.getBean(NetworkMonitorController.class), context.getBean(TincResourceGrantController.class))
                .setControllerAdvice(new TincResourceAccessAdvice(), new GlobalExceptionHandler()).build();
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }
    private void authenticate(Long id, boolean functions) {
        SysUser user = new SysUser(); user.setUserId(id); user.setUserName("test_user");
        LoginUser principal = new LoginUser(user, functions ? Collections.singleton("*:*:*") : Collections.emptySet());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));
    }

    @Test void guessedIdsAndLegacyRoutesAreRejectedBeforeReadOrRuntime() throws Exception {
        for (String url : new String[] {"/tinc/server/4", "/manger/mangeServer/4", "/tinc/network/9",
                "/TincNetworkMange/TincNetworkMange/9", "/tinc/node/6", "/node_mange/node_mange/6",
                "/tinc/network/runtime/9", "/monitor/network/single-stats/9"}) {
            http.perform(get(url)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("TINC_RESOURCE_FORBIDDEN"));
        }
        verifyNoInteractions(nodes, networks, servers, runtime);
    }

    @Test void bothFunctionalPermissionAndResourceGrantAreRequired() throws Exception {
        when(grants.canNode(20L, 5L)).thenReturn(1);
        TincNodeMange node = new TincNodeMange(); node.setId(5L);
        when(nodes.selectTincNodeMangeById(5L)).thenReturn(node);
        http.perform(get("/tinc/node/5")).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(5));
        authenticate(20L, false);
        http.perform(get("/tinc/node/5")).andExpect(status().isForbidden());
        verify(nodes, times(1)).selectTincNodeMangeById(5L);
    }

    @Test void mixedBatchDeletesCheckEntireScopeBeforeAnyMutation() throws Exception {
        when(grants.canNode(20L, 5L)).thenReturn(1);
        when(grants.canNetwork(20L, 8L)).thenReturn(1);
        when(grants.canServer(20L, 3L)).thenReturn(1);
        for (String url : new String[] {"/tinc/node/5,6", "/tinc/network/8,9", "/tinc/server/3,4"}) {
            http.perform(delete(url)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(nodes, networks, servers, runtime);
    }

    @Test void createEditRotationDownloadAndProbeCannotBypassResourceChecks() throws Exception {
        when(grants.canNode(20L, 5L)).thenReturn(1);
        http.perform(put("/tinc/node").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":5,\"networkId\":9}")).andExpect(status().isForbidden());
        http.perform(post("/tinc/node").contentType(MediaType.APPLICATION_JSON)
                .content("{\"networkId\":9}")).andExpect(status().isForbidden());
        http.perform(post("/tinc/network").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serverId\":4}")).andExpect(status().isForbidden());
        http.perform(post("/tinc/server").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        http.perform(put("/tinc/node/rotate-access-code/6")).andExpect(status().isForbidden());
        http.perform(post("/tinc/node/download/6")).andExpect(status().isForbidden());
        http.perform(post("/tinc/node/download/5")).andExpect(status().isForbidden());
        when(grants.canServer(20L, 3L)).thenReturn(1);
        http.perform(put("/tinc/server").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":3,\"runtimeType\":\"LOCAL\"}")).andExpect(status().isForbidden());
        http.perform(post("/tinc/server/4/agent/probe")).andExpect(status().isForbidden());
        http.perform(get("/tinc/server/4/tinc/check")).andExpect(status().isForbidden());
        http.perform(post("/tinc/server/4/tinc/install")).andExpect(status().isForbidden());
        verifyNoInteractions(nodes, networks, servers, runtime);
    }

    @Test void networkParentOptionsExposeOnlyIdAndLabel() throws Exception {
        MangeServer server = new MangeServer(); server.setId(3L); server.setServerName("parent");
        server.setSshKeyPath("/private/path"); server.setAgentSecret("test-secret"); server.setServerIp("203.0.113.10");
        server.setStartPort(600L); server.setEndPort(699L);
        when(servers.selectMangeServerList(any())).thenReturn(Collections.singletonList(server));
        http.perform(get("/tinc/server/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].id").value(3))
                .andExpect(jsonPath("$.rows[0].serverName").value("parent"))
                .andExpect(jsonPath("$.rows[0].sshKeyPath").doesNotExist())
                .andExpect(jsonPath("$.rows[0].serverIp").doesNotExist())
                .andExpect(jsonPath("$.rows[0].startPort").doesNotExist())
                .andExpect(jsonPath("$.rows[0].agentSecret").doesNotExist());
        verify(servers).selectMangeServerList(argThat(q -> Long.valueOf(20L).equals(q.getParams().get("tincScopeUserId"))
                && Boolean.TRUE.equals(q.getParams().get("tincIncludeParentServers"))));
        when(grants.canServer(20L, 3L)).thenReturn(1);
        http.perform(get("/tinc/server/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].startPort").value(600));
    }

    @Test void listAndExportOverwriteForgedScopeAndKeepQueryFilters() throws Exception {
        when(nodes.selectTincNodeMangeList(any())).thenReturn(Collections.emptyList());
        http.perform(get("/tinc/node/list").param("params[tincScopeUserId]", "1")
                .param("params[tincIncludeParentServers]", "true").param("networkId", "9"))
                .andExpect(status().isOk());
        http.perform(post("/tinc/node/export").param("params[tincScopeUserId]", "1").param("networkId", "9"))
                .andExpect(status().isOk());
        verify(nodes, times(2)).selectTincNodeMangeList(argThat(q -> Long.valueOf(20L).equals(q.getParams().get("tincScopeUserId"))
                && Long.valueOf(9L).equals(q.getNetworkId()) && Boolean.FALSE.equals(q.getParams().get("tincIncludeParentServers"))));
    }

    @Test void ordinaryFunctionalAdminCannotGrantResourcesToItself() throws Exception {
        http.perform(get("/tinc/resource-grants/options")).andExpect(status().isForbidden());
        http.perform(get("/tinc/resource-grants/20")).andExpect(status().isForbidden());
        http.perform(put("/tinc/resource-grants/20").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serverIds\":[3],\"networkIds\":[]}")).andExpect(status().isForbidden());
        verify(grants, never()).deleteForUser(anyLong());
        authenticate(1L, true);
        when(grants.lockUser(20L)).thenReturn(20L);
        when(grants.serverExists(3L)).thenReturn(1);
        when(grants.insert(20L, "SERVER", 3L, 1L)).thenReturn(1);
        http.perform(put("/tinc/resource-grants/20").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serverIds\":[3],\"networkIds\":[]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(grants).insert(20L, "SERVER", 3L, 1L);
    }

    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
    static class Config {
        @Bean TincResourceGrantMapper grants() { return mock(TincResourceGrantMapper.class); }
        @Bean(name = "tincAccess") TincResourceAccess access(TincResourceGrantMapper mapper) { return new TincResourceAccess(mapper); }
        @Bean(name = "ss") PermissionService permissions() { return new PermissionService(); }
        @Bean ITincNodeMangeService nodes() { return mock(ITincNodeMangeService.class); }
        @Bean ITincNetworkMangeService networks() { return mock(ITincNetworkMangeService.class); }
        @Bean IMangeServerService servers() { return mock(IMangeServerService.class); }
        @Bean TincRuntimeRouter runtime() { return mock(TincRuntimeRouter.class); }
        @Bean TincConfigTransport transport() { return mock(TincConfigTransport.class); }
        @Bean TincNodeMangeController nodeController() { return new TincNodeMangeController(); }
        @Bean TincNetworkMangeController networkController() { return new TincNetworkMangeController(); }
        @Bean MangeServerController serverController() { return new MangeServerController(); }
        @Bean NetworkMonitorController monitorController() { return new NetworkMonitorController(); }
        @Bean TincResourceGrantController grantController(TincResourceAccess access, TincResourceGrantMapper mapper) {
            return new TincResourceGrantController(access, mapper);
        }
    }
}
