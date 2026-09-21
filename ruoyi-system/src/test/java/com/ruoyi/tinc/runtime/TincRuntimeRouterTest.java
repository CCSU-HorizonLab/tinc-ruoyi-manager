package com.ruoyi.tinc.runtime;

import com.ruoyi.common.tinc.access.AccessAgentHealth;
import com.ruoyi.common.tinc.access.AccessNetworkSpec;
import com.ruoyi.common.tinc.access.AccessOperationResult;
import com.ruoyi.common.tinc.access.AccessPeerSpec;
import com.ruoyi.common.tinc.access.LocalTincAccessRuntime;
import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import com.ruoyi.tinc_server.domain.MangeServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

class TincRuntimeRouterTest {
    @Mock private LocalTincAccessRuntime local;
    @Mock private AccessAgentClient client;
    private TincRuntimeRouter router;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        router = new TincRuntimeRouter(local, client);
    }

    @Test
    void routesAAndBToTheirOwnAgent() {
        MangeServer a = agent(1L, "192.0.2.10");
        MangeServer b = agent(2L, "192.0.2.11");
        AccessAgentHealth up = new AccessAgentHealth();
        up.setStatus("UP");
        when(client.health(any())).thenReturn(up);
        when(client.createNetwork(any(), any())).thenReturn(AccessOperationResult.of(true, ready()));
        AccessNetworkSpec spec = new AccessNetworkSpec();
        spec.setRuntimeName("network1"); spec.setSegment("10.0.11"); spec.setPort(600);
        spec.setPublicAddress("192.0.2.10");

        router.createNetwork(a, spec);
        router.createNetwork(b, spec);

        verify(client).createNetwork(same(a), same(spec));
        verify(client).createNetwork(same(b), same(spec));
        verifyNoInteractions(local);
    }

    @Test
    void routesPeerAndDeleteOnlyToBoundAgent() {
        MangeServer a = agent(1L, "192.0.2.10");
        AccessPeerSpec peer = new AccessPeerSpec();
        router.upsertPeer(a, "a1", peer);
        router.deletePeer(a, "a1", "alice");
        verify(client).upsertPeer(same(a), eq("a1"), same(peer));
        verify(client).deletePeer(same(a), eq("a1"), eq("alice"));
        verifyNoInteractions(local);
    }

    @Test
    void refusesCreateWhenRemoteHealthIsNotUp() {
        MangeServer a = agent(1L, "192.0.2.10");
        AccessAgentHealth down = new AccessAgentHealth(); down.setStatus("DEGRADED");
        when(client.health(a)).thenReturn(down);
        assertThrows(AccessAgentException.class, () -> router.createNetwork(a, new AccessNetworkSpec()));
        verify(client, never()).createNetwork(any(), any());
    }

    private MangeServer agent(Long id, String ip) {
        MangeServer s = new MangeServer(); s.setId(id); s.setServerIp(ip); s.setAgentPort(9088);
        s.setAgentSecret("12345678901234567890123456789012"); s.setRuntimeType("AGENT"); return s;
    }
    private TincNetworkStatus ready() { TincNetworkStatus s = new TincNetworkStatus(); s.setReadiness("READY"); return s; }
}
