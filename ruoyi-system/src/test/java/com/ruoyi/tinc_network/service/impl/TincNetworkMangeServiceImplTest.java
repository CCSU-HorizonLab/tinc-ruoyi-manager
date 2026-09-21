package com.ruoyi.tinc_network.service.impl;

import com.ruoyi.tinc.runtime.TincRuntimeRouter;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.mapper.TincNetworkMangeMapper;
import com.ruoyi.tinc_node.mapper.TincNodeMangeMapper;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc_server.service.IMangeServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TincNetworkMangeServiceImplTest {
    @Mock private TincNetworkMangeMapper networkMapper;
    @Mock private TincNodeMangeMapper nodeMapper;
    @Mock private IMangeServerService serverService;
    @Mock private TincRuntimeRouter runtimeRouter;
    private TestableNetworkService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TestableNetworkService();
        ReflectionTestUtils.setField(service, "tincNetworkMangeMapper", networkMapper);
        ReflectionTestUtils.setField(service, "tincNodeMangeMapper", nodeMapper);
        ReflectionTestUtils.setField(service, "mangeServerService", serverService);
        ReflectionTestUtils.setField(service, "tincRuntimeRouter", runtimeRouter);
        when(networkMapper.selectByServerIdForConflictCheck(anyLong())).thenReturn(Collections.emptyList());
    }

    @Test
    void createsNetworkFromServerIdAndPersistsStableRelation() {
        MangeServer server = server(7L, "gateway_a");
        when(serverService.selectMangeServerById(7L)).thenReturn(server);
        when(networkMapper.insertTincNetworkMange(any())).thenReturn(1);

        TincNetworkMange network = network(7L, "network_a", "10.20.30", "1600");
        assertEquals(1, service.insertTincNetworkMange(network));

        assertEquals(7L, network.getServerId());
        assertEquals("gateway_a", network.getServerName());
        assertTrue(service.runtimeInitialized);
        verify(networkMapper).insertTincNetworkMange(same(network));
    }

    @Test
    void rejectsUnknownServerIdBeforeInsertOrRuntimeMutation() {
        when(serverService.selectMangeServerById(99L)).thenReturn(null);
        TincNetworkMange network = network(99L, "network_a", "10.20.30", "1600");

        assertThrows(IllegalArgumentException.class, () -> service.insertTincNetworkMange(network));
        verify(networkMapper, never()).insertTincNetworkMange(any());
        assertFalse(service.runtimeInitialized);
    }

    @Test
    void rejectsSameServerPortAndSegmentConflictsBeforeInsert() {
        when(serverService.selectMangeServerById(7L)).thenReturn(server(7L, "gateway_a"));
        when(networkMapper.countPortExcludingId(7L, "1600", null)).thenReturn(1);
        assertThrows(IllegalStateException.class,
                () -> service.insertTincNetworkMange(network(7L, "network_a", "10.20.30", "1600")));

        reset(networkMapper);
        TincNetworkMange existing = network(7L, "network_b", "10.20.30", "1601");
        existing.setId(2L);
        when(networkMapper.selectByServerIdForConflictCheck(7L)).thenReturn(Collections.singletonList(existing));
        assertThrows(IllegalStateException.class,
                () -> service.insertTincNetworkMange(network(7L, "network_a", "10.20.30", "1600")));
        verify(networkMapper, never()).insertTincNetworkMange(any());
    }

    @Test
    void refusesDeletingNetworkThatHasNodesByNetworkId() {
        TincNetworkMange network = network(7L, "network_a", "10.20.30", "1600");
        network.setId(12L);
        when(networkMapper.selectTincNetworkMangeByIdForUpdate(12L)).thenReturn(network);
        when(nodeMapper.countByNetworkId(12L)).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> service.deleteTincNetworkMangeById(12L));
        verifyNoInteractions(runtimeRouter);
        verify(networkMapper, never()).deleteTincNetworkMangeById(anyLong());
    }

    @Test
    void allowsSameResourcesOnDifferentServersByScopingEveryQuery() {
        when(serverService.selectMangeServerById(8L)).thenReturn(server(8L, "gateway_b"));
        when(networkMapper.insertTincNetworkMange(any())).thenReturn(1);
        TincNetworkMange network = network(8L, "network_a", "10.20.30", "1600");

        assertEquals(1, service.insertTincNetworkMange(network));

        verify(networkMapper).countNetworkNameExcludingId(8L, "network_a", null);
        verify(networkMapper).countPortExcludingId(8L, "1600", null);
        verify(networkMapper).selectByServerIdForConflictCheck(8L);
    }

    @Test
    void rejectsPortAndSegmentOutsideSelectedServerRange() {
        MangeServer server = server(7L, "gateway_a");
        server.setStartPort(1600L);
        server.setEndPort(1699L);
        server.setStartSegment("10.20.30");
        server.setEndSegment("10.20.40");
        when(serverService.selectMangeServerById(7L)).thenReturn(server);

        assertThrows(IllegalStateException.class,
                () -> service.insertTincNetworkMange(network(7L, "network_a", "10.20.31", "1700")));
        assertThrows(IllegalStateException.class,
                () -> service.insertTincNetworkMange(network(7L, "network_a", "10.20.41", "1601")));
    }

    private static TincNetworkMange network(Long serverId, String name, String segment, String port) {
        TincNetworkMange result = new TincNetworkMange();
        result.setServerId(serverId);
        result.setNetworkName(name);
        result.setSegment(segment);
        result.setPort(port);
        return result;
    }

    private static MangeServer server(Long id, String name) {
        MangeServer result = new MangeServer();
        result.setId(id);
        result.setServerName(name);
        result.setServerIp("203.0.113.10");
        return result;
    }

    private static class TestableNetworkService extends TincNetworkMangeServiceImpl {
        private boolean runtimeInitialized;

        @Override
        protected void initializeNetworkRuntime(TincNetworkMange network, MangeServer selectedServer) {
            runtimeInitialized = true;
        }
    }
}
