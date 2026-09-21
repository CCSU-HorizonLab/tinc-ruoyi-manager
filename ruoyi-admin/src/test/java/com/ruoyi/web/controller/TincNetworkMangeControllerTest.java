package com.ruoyi.web.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import com.ruoyi.tinc.runtime.TincRuntimeRouter;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.service.ITincNetworkMangeService;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc_server.service.IMangeServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TincNetworkMangeControllerTest {
    @Mock private ITincNetworkMangeService networkService;
    @Mock private IMangeServerService serverService;
    @Mock private TincRuntimeRouter runtimeRouter;

    private TincNetworkMangeController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new TincNetworkMangeController();
        ReflectionTestUtils.setField(controller, "tincNetworkMangeService", networkService);
        ReflectionTestUtils.setField(controller, "mangeServerService", serverService);
        ReflectionTestUtils.setField(controller, "tincRuntimeRouter", runtimeRouter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void runtimeRoutesLocalNetworkByStableNetworkId() {
        assertStableRoute(8L, "new_network", 3L, "Aliyun_tinc", "LOCAL");
    }

    @Test
    void runtimeRoutesDevAToAccessAByStableIds() {
        assertStableRoute(11L, "DEV_A", 4L, "Access-A", "AGENT");
    }

    @Test
    void runtimeRoutesDevBToAccessBByStableIds() {
        assertStableRoute(12L, "DEV_B", 5L, "Access-B", "AGENT");
    }

    @Test
    void runtimeRejectsNetworkNameAsPathIdentifier() throws Exception {
        mockMvc.perform(get("/tinc/network/runtime/DEV_A"))
                .andExpect(status().isBadRequest());

        verify(networkService, never()).selectTincNetworkMangeById(anyLong());
    }

    @Test
    void runtimeReportsMissingLegacyRelationWithoutGuessingByName() {
        TincNetworkMange network = new TincNetworkMange();
        network.setId(99L);
        network.setNetworkName("legacy_network");
        when(networkService.selectTincNetworkMangeById(99L)).thenReturn(network);

        AjaxResult result = controller.runtime(99L);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("网络或 server_id 不存在", result.get(AjaxResult.MSG_TAG));
        verify(serverService, never()).selectMangeServerById(anyLong());
    }

    @Test
    void runtimeReportsUnknownStableId() {
        when(networkService.selectTincNetworkMangeById(404L)).thenReturn(null);

        AjaxResult result = controller.runtime(404L);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("网络或 server_id 不存在", result.get(AjaxResult.MSG_TAG));
        verify(runtimeRouter, never()).inspectNetwork(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private void assertStableRoute(Long networkId, String networkName, Long serverId,
                                   String serverName, String runtimeType) {
        TincNetworkMange network = new TincNetworkMange();
        network.setId(networkId);
        network.setNetworkName(networkName);
        network.setServerId(serverId);

        MangeServer server = new MangeServer();
        server.setId(serverId);
        server.setServerName(serverName);
        server.setRuntimeType(runtimeType);

        TincNetworkStatus expected = new TincNetworkStatus();
        expected.setNetName(networkName);
        expected.setReadiness("READY");

        when(networkService.selectTincNetworkMangeById(networkId)).thenReturn(network);
        when(serverService.selectMangeServerById(serverId)).thenReturn(server);
        when(runtimeRouter.inspectNetwork(server, networkName)).thenReturn(expected);

        AjaxResult result = controller.runtime(networkId);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertSame(expected, result.get(AjaxResult.DATA_TAG));
        verify(networkService).selectTincNetworkMangeById(networkId);
        verify(serverService).selectMangeServerById(serverId);
        verify(runtimeRouter).inspectNetwork(server, networkName);
    }
}
