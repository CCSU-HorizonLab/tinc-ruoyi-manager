package com.ruoyi.tinc_server.service.impl;

import com.ruoyi.tinc_network.mapper.TincNetworkMangeMapper;
import com.ruoyi.tinc_node.mapper.TincNodeMangeMapper;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc_server.mapper.MangeServerMapper;
import com.ruoyi.tinc.runtime.AccessAgentClient;
import com.ruoyi.common.tinc.access.AccessAgentHealth;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class MangeServerServiceImplTest {
    @Mock private MangeServerMapper serverMapper;
    @Mock private TincNetworkMangeMapper networkMapper;
    @Mock private TincNodeMangeMapper nodeMapper;
    @Mock private AccessAgentClient agentClient;
    private MangeServerServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MangeServerServiceImpl();
        ReflectionTestUtils.setField(service, "mangeServerMapper", serverMapper);
        ReflectionTestUtils.setField(service, "tincNetworkMangeMapper", networkMapper);
        ReflectionTestUtils.setField(service, "tincNodeMangeMapper", nodeMapper);
        ReflectionTestUtils.setField(service, "accessAgentClient", agentClient);
    }

    @Test
    void refusesDeletingServerThatHasNetworksByServerId() {
        MangeServer server = new MangeServer();
        server.setId(7L);
        server.setServerName("gateway_a");
        when(serverMapper.selectMangeServerByIdForUpdate(7L)).thenReturn(server);
        when(networkMapper.countByServerId(7L)).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> service.deleteMangeServerById(7L));
        verify(serverMapper, never()).deleteMangeServerById(anyLong());
    }

    @Test
    void forbidsServerRenameDuringPhaseOne() {
        MangeServer existing = new MangeServer();
        existing.setId(7L);
        existing.setServerName("gateway_a");
        when(serverMapper.selectMangeServerById(7L)).thenReturn(existing);

        MangeServer update = new MangeServer();
        update.setId(7L);
        update.setServerName("gateway_b");
        assertThrows(IllegalStateException.class, () -> service.updateMangeServer(update));
        verify(serverMapper, never()).updateMangeServer(any());
    }

    @Test
    void agentServerBecomesOnlineOnlyAfterAuthenticatedHealthSucceeds() {
        MangeServer server = agentServer();
        AccessAgentHealth health = new AccessAgentHealth();
        health.setStatus("UP"); health.setAgentId("access-a"); health.setAgentVersion("2.0.0");
        health.setCpuUsage(12.5); health.setMemoryUsage(34.5); health.setNetworkCount(2);
        when(serverMapper.insertMangeServer(server)).thenReturn(1);
        when(serverMapper.selectMangeServerById(7L)).thenReturn(server);
        when(agentClient.health(server)).thenReturn(health);

        assertEquals(1, service.insertMangeServer(server));

        assertEquals("ONLINE", server.getAgentStatus());
        assertEquals(1L, server.getStatus());
        verify(serverMapper).updateAgentHealth(server);
    }

    @Test
    void unreachableAgentRecordIsRetainedButNeverMarkedOnline() {
        MangeServer server = agentServer();
        when(serverMapper.insertMangeServer(server)).thenReturn(1);
        when(serverMapper.selectMangeServerById(7L)).thenReturn(server);
        when(agentClient.health(server)).thenThrow(new RuntimeException("connection refused"));

        assertEquals(1, service.insertMangeServer(server));

        assertEquals("UNREACHABLE", server.getAgentStatus());
        assertEquals(0L, server.getStatus());
        verify(serverMapper).insertMangeServer(server);
        verify(serverMapper).updateAgentHealth(server);
    }

    private MangeServer agentServer() {
        MangeServer server = new MangeServer();
        server.setId(7L); server.setServerName("gateway_a"); server.setServerIp("192.0.2.10");
        server.setRuntimeType("AGENT"); server.setAgentPort(9088);
        server.setAgentSecret("12345678901234567890123456789012");
        return server;
    }

    @Test
    void serverJsonNeverReturnsAgentOrSshSecret() throws Exception {
        MangeServer server = agentServer();
        server.setSshPassword("legacy-password");
        String json = new ObjectMapper().writeValueAsString(server);
        assertFalse(json.contains("12345678901234567890123456789012"));
        assertFalse(json.contains("legacy-password"));
        assertFalse(json.contains("agentSecret"));
        assertFalse(json.contains("sshPassword"));
    }
}
