package com.ruoyi.web.controller.system;

import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.service.ITincNodeMangeService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TincClientRouteSnapshotTest {
    private ITincNodeMangeService nodes;
    private TincClientRouteSnapshot snapshots;

    @BeforeEach
    void setUp() {
        nodes = mock(ITincNodeMangeService.class);
        snapshots = new TincClientRouteSnapshot();
        ReflectionTestUtils.setField(snapshots, "nodeService", nodes);
    }

    @Test
    void peersSeeEachOtherAndGatewayButNotSelfOrOtherNetworks() {
        TincNodeMange a = node(1, 17, "10.0.11.211");
        TincNodeMange b = node(2, 17, "10.0.11.212");
        TincNodeMange other = node(3, 18, "10.0.11.213");
        when(nodes.selectTincNodeMangeList(any())).thenReturn(Arrays.asList(a, b, other));

        assertEquals(Arrays.asList("10.0.11.1/32", "10.0.11.212/32"),
                snapshots.build(a, network()).getRoutes());
        assertEquals(Arrays.asList("10.0.11.1/32", "10.0.11.211/32"),
                snapshots.build(b, network()).getRoutes());
        verify(nodes, times(2)).selectTincNodeMangeList(argThat(q -> Long.valueOf(17L).equals(q.getNetworkId())));
    }

    @Test
    void offlineMemberStaysButRevokedAndInvalidMembersDisappear() {
        TincNodeMange a = node(1, 17, "10.0.11.211");
        TincNodeMange offline = node(2, 17, "10.0.11.212");
        offline.setNodeStatus("离线");
        TincNodeMange revoked = node(3, 17, "10.0.11.213");
        revoked.setStatus("已撤销");
        TincNodeMange invalid = node(4, 17, "10.0.12.99");
        when(nodes.selectTincNodeMangeList(any())).thenReturn(Arrays.asList(a, offline, revoked, invalid));

        TincClientRouteSnapshot.Snapshot before = snapshots.build(a, network());
        assertEquals(Arrays.asList("10.0.11.1/32", "10.0.11.212/32"), before.getRoutes());
        offline.setNodeStatus("在线");
        assertEquals(before.getRevision(), snapshots.build(a, network()).getRevision());
        offline.setStatus("禁用");
        TincClientRouteSnapshot.Snapshot after = snapshots.build(a, network());
        assertEquals(Collections.singletonList("10.0.11.1/32"), after.getRoutes());
        assertNotEquals(before.getRevision(), after.getRevision());
    }

    @Test
    void malformedNetworkAndCurrentNodeFailClosed() {
        TincNodeMange a = node(1, 17, "10.0.11.211");
        when(nodes.selectTincNodeMangeList(any())).thenReturn(Collections.emptyList());
        TincNetworkMange wrong = network();
        wrong.setSegment("10.0.12");
        assertThrows(IllegalArgumentException.class, () -> snapshots.build(a, wrong));
        a.setStatus("禁用");
        assertThrows(IllegalStateException.class, () -> snapshots.build(a, network()));
    }

    @Test
    void concurrentMemberReplacementNeverSeparatesRoutesFromRevision() throws Exception {
        TincNodeMange a = node(1, 17, "10.0.11.211");
        TincNodeMange b = node(2, 17, "10.0.11.212");
        TincNodeMange c = node(3, 17, "10.0.11.213");
        List<TincNodeMange> ab = Arrays.asList(a, b);
        List<TincNodeMange> ac = Arrays.asList(a, c);
        AtomicReference<List<TincNodeMange>> current = new AtomicReference<>(ab);
        when(nodes.selectTincNodeMangeList(any())).thenAnswer(call -> current.get());
        TincClientRouteSnapshot.Snapshot expectedB = snapshots.build(a, network());
        current.set(ac);
        TincClientRouteSnapshot.Snapshot expectedC = snapshots.build(a, network());
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> writer = pool.submit(() -> {
                for (int i = 0; i < 5000; i++) current.set(i % 2 == 0 ? ab : ac);
            });
            for (int i = 0; i < 1000; i++) {
                TincClientRouteSnapshot.Snapshot actual = snapshots.build(a, network());
                if (actual.getRoutes().equals(expectedB.getRoutes())) {
                    assertEquals(expectedB.getRevision(), actual.getRevision());
                } else {
                    assertEquals(expectedC.getRoutes(), actual.getRoutes());
                    assertEquals(expectedC.getRevision(), actual.getRevision());
                }
            }
            writer.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private static TincNodeMange node(long id, long networkId, String ip) {
        TincNodeMange node = new TincNodeMange();
        node.setId(id);
        node.setNetworkId(networkId);
        node.setNetworkIp(ip);
        return node;
    }

    private static TincNetworkMange network() {
        TincNetworkMange network = new TincNetworkMange();
        network.setId(17L);
        network.setSegment("10.0.11");
        return network;
    }
}
