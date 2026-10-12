package com.ruoyi.web.controller.monitor;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import com.ruoyi.tinc.runtime.TincRuntimeRouter;
import com.ruoyi.tinc.security.TincResourceAccess;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.service.ITincNetworkMangeService;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.service.ITincNodeMangeService;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc_server.service.IMangeServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NetworkMonitorControllerTest {
    private final ITincNetworkMangeService networks = mock(ITincNetworkMangeService.class);
    private final ITincNodeMangeService nodes = mock(ITincNodeMangeService.class);
    private final IMangeServerService servers = mock(IMangeServerService.class);
    private final TincRuntimeRouter router = mock(TincRuntimeRouter.class);
    private final NetworkMonitorController controller = new NetworkMonitorController();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(controller, "tincNetworkMangeService", networks);
        ReflectionTestUtils.setField(controller, "tincNodeMangeService", nodes);
        ReflectionTestUtils.setField(controller, "mangeServerService", servers);
        ReflectionTestUtils.setField(controller, "runtimeRouter", router);
        ReflectionTestUtils.setField(controller, "resourceAccess", mock(TincResourceAccess.class));
        when(nodes.selectTincNodeMangeList(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void globalStatusUsesReadOnlyBoundRuntimeNotPersistedNameStatus() {
        TincNetworkMange a = network(11L, 4L, "DEV_A");
        TincNetworkMange b = network(12L, 5L, "DEV_B");
        TincNetworkMange local = network(8L, 3L, "new_network");
        a.setNetworkStatus("在线"); b.setNetworkStatus("READY");
        MangeServer serverA = server(4L, "AGENT");
        MangeServer serverB = server(5L, "AGENT");
        MangeServer serverLocal = server(3L, "LOCAL");
        when(networks.selectTincNetworkMangeList(any())).thenReturn(Arrays.asList(a, b, local));
        when(servers.selectMangeServerList(any())).thenReturn(Arrays.asList(serverA, serverB, serverLocal));
        when(router.inspectNetwork(serverA, "DEV_A")).thenReturn(status("NOT_READY"));
        when(router.inspectNetwork(serverB, "DEV_B")).thenReturn(status("READY"));
        when(router.inspectNetwork(serverLocal, "new_network")).thenReturn(status("READY"));

        Map<String, Object> data = data(controller.getGlobalStats());
        assertEquals(2, data.get("onlineNetworks"));
        assertEquals(1, data.get("notReadyNetworks"));
        assertEquals(0, data.get("unknownNetworks"));
        assertNull(data.get("averageResponseTime"));
        assertNull(data.get("healthScore"));
        assertEquals(false, data.get("telemetryCollected"));
        verify(router).inspectNetwork(serverA, "DEV_A");
        verify(router).inspectNetwork(serverB, "DEV_B");
        verify(router).inspectNetwork(serverLocal, "new_network");
        verifyNoMoreInteractions(router);
    }

    @Test
    void failedProbeIsUnknownWithoutLeakingExceptionOrPretendingOffline() {
        when(networks.selectTincNetworkMangeById(11L)).thenReturn(network(11L, 4L, "DEV_A"));
        MangeServer server = server(4L, "AGENT");
        when(servers.selectMangeServerById(4L)).thenReturn(server);
        when(router.inspectNetwork(server, "DEV_A")).thenThrow(new IllegalStateException("/secret/path token=test-secret"));
        Map<String, Object> data = data(controller.getSingleStats(11L));
        assertEquals("UNKNOWN", data.get("readiness"));
        assertEquals("STATUS_QUERY_FAILED", data.get("failureCode"));
        assertNull(data.get("isOnline"));
        assertFalse(data.toString().contains("test-secret"));
        assertFalse(data.toString().contains("/secret/path"));
        verify(nodes).selectTincNodeMangeList(argThat(n -> Long.valueOf(11L).equals(n.getNetworkId())));
    }

    @Test
    void configuredNodesAreNotAdvertisedAsLiveConnections() {
        TincNodeMange node = new TincNodeMange();
        node.setStatus("已配置"); node.setNodeStatus("在线");
        when(nodes.selectTincNodeMangeList(any())).thenReturn(Collections.singletonList(node));
        when(networks.selectTincNetworkMangeById(11L)).thenReturn(network(11L, null, "legacy"));
        Map<String, Object> data = data(controller.getSingleStats(11L));
        assertEquals("LEGACY_RELATION_MISSING", data.get("failureCode"));
        Map<?, ?> summary = (Map<?, ?>) data.get("nodeSummary");
        assertEquals(1, summary.get("configured"));
        assertNull(summary.get("online"));
        verifyNoInteractions(router);
    }

    @Test
    void noReadyNetworkDoesNotFallBackToAllReady() {
        MangeServer server = server(3L, "LOCAL");
        when(servers.selectMangeServerList(any())).thenReturn(Collections.singletonList(server));
        when(networks.selectTincNetworkMangeList(any())).thenReturn(Collections.singletonList(network(8L, 3L, "new_network")));
        when(router.inspectNetwork(server, "new_network")).thenReturn(status("NOT_READY"));
        assertEquals(0, data(controller.getGlobalStats()).get("onlineRate"));
        when(networks.selectTincNetworkMangeList(any())).thenReturn(Collections.emptyList());
        assertNull(data(controller.getGlobalStats()).get("onlineRate"));
    }

    @Test
    void invalidOrMissingNetworkNeverProbesRuntime() {
        assertEquals(500, controller.getSingleStats(-1L).get("code"));
        assertEquals(500, controller.getSingleStats(999L).get("code"));
        verifyNoInteractions(router, nodes, servers);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(AjaxResult result) { return (Map<String, Object>) result.get("data"); }
    private TincNetworkMange network(Long id, Long serverId, String name) {
        TincNetworkMange network = new TincNetworkMange();
        network.setId(id); network.setServerId(serverId); network.setNetworkName(name);
        return network;
    }
    private MangeServer server(Long id, String type) {
        MangeServer server = new MangeServer(); server.setId(id); server.setRuntimeType(type); return server;
    }
    private TincNetworkStatus status(String readiness) {
        TincNetworkStatus status = new TincNetworkStatus(); status.setReadiness(readiness); return status;
    }
}
