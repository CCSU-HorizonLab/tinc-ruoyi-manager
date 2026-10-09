package com.ruoyi.tinc_node.service.impl;

import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.mapper.TincNetworkMangeMapper;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.mapper.TincNodeMangeMapper;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc_server.service.IMangeServerService;
import com.ruoyi.tinc.runtime.TincRuntimeRouter;
import com.ruoyi.common.tinc.access.AccessOperationResult;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.security.TincNodePasswordCodec;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TincNodeMangeServiceImplTest {
    @Mock private TincNodeMangeMapper nodeMapper;
    @Mock private TincNetworkMangeMapper networkMapper;
    @Mock private IMangeServerService serverService;
    @Mock private TincRuntimeRouter runtimeRouter;
    @Mock private RedisCache redisCache;
    private TincNodeMangeServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TincNodeMangeServiceImpl();
        ReflectionTestUtils.setField(service, "tincNodeMangeMapper", nodeMapper);
        ReflectionTestUtils.setField(service, "tincNetworkMangeMapper", networkMapper);
        ReflectionTestUtils.setField(service, "mangeServerService", serverService);
        ReflectionTestUtils.setField(service, "tincRuntimeRouter", runtimeRouter);
        ReflectionTestUtils.setField(service, "redisCache", redisCache);
        when(serverService.selectMangeServerById(1L)).thenReturn(server());
        when(nodeMapper.insertTincNodeMange(any())).thenReturn(1);
    }

    @Test
    void createsNodeFromNetworkIdAndAllowsSameNameAcrossNetworks() {
        when(networkMapper.selectTincNetworkMangeByIdForUpdate(10L)).thenReturn(network(10L, "network_a", "10.0.10"));
        when(networkMapper.selectTincNetworkMangeByIdForUpdate(20L)).thenReturn(network(20L, "network_b", "10.0.11"));

        service.insertTincNodeMange(node(10L, "alice", "10.0.10.20"));
        service.insertTincNodeMange(node(20L, "alice", "10.0.11.20"));

        ArgumentCaptor<TincNodeMange> captor = ArgumentCaptor.forClass(TincNodeMange.class);
        verify(nodeMapper, times(2)).insertTincNodeMange(captor.capture());
        assertEquals(10L, captor.getAllValues().get(0).getNetworkId());
        assertEquals(20L, captor.getAllValues().get(1).getNetworkId());
        assertTrue(TincNodePasswordCodec.matches("test-password", captor.getAllValues().get(0).getPassword()));
        assertTrue(TincNodePasswordCodec.isEncoded(captor.getAllValues().get(0).getPassword()));
    }

    @Test
    void legacyPasswordUpgradesOnlyAfterSuccessfulAuthentication() {
        TincNodeMange existing = node(10L, "alice", "10.0.10.20");
        existing.setId(50L);
        existing.setPassword("legacy-password");
        String fingerprint = TincNodePasswordCodec.legacyFingerprint("legacy-password");
        when(nodeMapper.upgradeLegacyPassword(eq(50L), eq(fingerprint), anyString())).thenReturn(1);

        assertTrue(service.verifyClientPassword(existing, "legacy-password"));
        verify(nodeMapper).upgradeLegacyPassword(eq(50L), eq(fingerprint),
                argThat(encoded -> TincNodePasswordCodec.matches("legacy-password", encoded)));
        assertTrue(!service.verifyClientPassword(existing, "wrong-password"));
    }

    @Test
    void rejectsPemInCredentialWithoutTouchingRuntime() {
        when(networkMapper.selectTincNetworkMangeByIdForUpdate(10L)).thenReturn(network(10L, "network_a", "10.0.10"));
        TincNodeMange newNode = node(10L, "alice", "10.0.10.20");
        newNode.setPassword("-----BEGIN RSA PUBLIC KEY-----\nabc\n-----END RSA PUBLIC KEY-----");

        assertThrows(IllegalArgumentException.class, () -> service.insertTincNodeMange(newNode));
        verify(nodeMapper, never()).insertTincNodeMange(any());
        verifyNoInteractions(runtimeRouter);
    }

    @Test
    void rejectsDuplicateNameInsideSameNetwork() {
        when(networkMapper.selectTincNetworkMangeByIdForUpdate(10L)).thenReturn(network(10L, "network_a", "10.0.10"));
        when(nodeMapper.countNodeNameInNetwork(10L, "alice", null)).thenReturn(1);

        assertThrows(IllegalStateException.class,
                () -> service.insertTincNodeMange(node(10L, "alice", "10.0.10.20")));
        verify(nodeMapper, never()).insertTincNodeMange(any());
    }

    @Test
    void rejectsDuplicateIpInsideSameNetwork() {
        when(networkMapper.selectTincNetworkMangeByIdForUpdate(10L)).thenReturn(network(10L, "network_a", "10.0.10"));
        when(nodeMapper.countNetworkIpInNetwork(10L, "10.0.10.20", null)).thenReturn(1);

        assertThrows(IllegalStateException.class,
                () -> service.insertTincNodeMange(node(10L, "alice", "10.0.10.20")));
        verify(nodeMapper, never()).insertTincNodeMange(any());
    }

    @Test
    void rejectsUnknownNetworkIdBeforeInsert() {
        when(networkMapper.selectTincNetworkMangeByIdForUpdate(99L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> service.insertTincNodeMange(node(99L, "alice", "10.0.10.20")));
        verify(nodeMapper, never()).insertTincNodeMange(any());
    }

    @Test
    void automaticallyGeneratesUniqueFormattedAccessCodeOnCreation() {
        when(networkMapper.selectTincNetworkMangeByIdForUpdate(10L)).thenReturn(network(10L, "network_a", "10.0.10"));
        when(nodeMapper.countByAccessCode(anyString(), isNull())).thenReturn(0);

        TincNodeMange newNode = node(10L, "bob", "10.0.10.30");
        service.insertTincNodeMange(newNode);

        ArgumentCaptor<TincNodeMange> captor = ArgumentCaptor.forClass(TincNodeMange.class);
        verify(nodeMapper).insertTincNodeMange(captor.capture());
        String accessCode = captor.getValue().getAccessCode();
        assertNotNull(accessCode);
        assertTrue(accessCode.startsWith("TL-"), "Access code must start with TL-");
        assertEquals(12, accessCode.length(), "Access code length must be 12 (TL-XXXX-XXXX)");
        assertTrue(accessCode.matches("^TL-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}$"),
                "Access code must match TL-XXXX-XXXX without confusing characters");
    }

    @Test
    void rotateAccessCodeGeneratesNewUniqueCodeWithoutAffectingNodeIdentity() {
        TincNodeMange existing = node(10L, "charlie", "10.0.10.40");
        existing.setId(77L);
        existing.setAccessCode("TL-OLD1-CODE");
        when(nodeMapper.selectTincNodeMangeById(77L)).thenReturn(existing);
        when(nodeMapper.countByAccessCode(anyString(), isNull())).thenReturn(0);
        when(nodeMapper.updateAccessCode(eq(77L), anyString(), any())).thenReturn(1);

        String newCode = service.rotateAccessCode(77L);

        assertNotNull(newCode);
        assertTrue(newCode.startsWith("TL-"));
        verify(nodeMapper).updateAccessCode(eq(77L), eq(newCode), any());
    }

    @Test
    void updateNodeProhibitsModifyingAccessCode() {
        TincNodeMange existing = node(10L, "dave", "10.0.10.50");
        existing.setId(88L);
        existing.setAccessCode("TL-AAAA-BBBB");
        when(nodeMapper.selectTincNodeMangeById(88L)).thenReturn(existing);

        TincNodeMange updateAttempt = node(10L, "dave", "10.0.10.50");
        updateAttempt.setId(88L);
        updateAttempt.setAccessCode("TL-HACK-CODE");

        assertThrows(IllegalStateException.class,
                () -> service.updateTincNodeMange(updateAttempt));
        verify(nodeMapper, never()).updateTincNodeMange(any());
    }

    @Test
    void metadataEditKeepsExistingCredentialAndDoesNotTouchRuntime() {
        TincNodeMange existing = node(10L, "alice", "10.0.10.20");
        existing.setId(88L);
        when(nodeMapper.selectTincNodeMangeById(88L)).thenReturn(existing);
        when(networkMapper.selectTincNetworkMangeById(10L)).thenReturn(network(10L, "network_a", "10.0.10"));
        when(nodeMapper.updateTincNodeMange(any())).thenReturn(1);
        TincNodeMange update = new TincNodeMange();
        update.setId(88L);
        update.setExplanation("updated");

        assertEquals(1, service.updateTincNodeMange(update));
        verify(nodeMapper).updateTincNodeMange(argThat(value -> value.getPassword() == null));
        verifyNoInteractions(runtimeRouter);
    }

    @Test
    void passwordResetStoresOnlyHashAndRevokesIssuedTokens() {
        TincNodeMange existing = node(10L, "alice", "10.0.10.20");
        existing.setId(88L);
        when(nodeMapper.selectTincNodeMangeById(88L)).thenReturn(existing);
        when(networkMapper.selectTincNetworkMangeById(10L)).thenReturn(network(10L, "network_a", "10.0.10"));
        when(nodeMapper.updateTincNodeMange(any())).thenReturn(1);
        when(redisCache.getCacheSet("tinc:client:node-tokens:88")).thenReturn(Set.of("old-token-hash"));
        TincNodeMange update = new TincNodeMange();
        update.setId(88L);
        update.setPassword("new-password");

        assertEquals(1, service.updateTincNodeMange(update));
        verify(nodeMapper).updateTincNodeMange(argThat(value ->
                TincNodePasswordCodec.matches("new-password", value.getPassword())));
        verify(redisCache).deleteObject("tinc:client:token:old-token-hash");
        verifyNoInteractions(runtimeRouter);
    }

    @Test
    void deleteRoutesPeerRevocationThroughNetworkServerBeforeDatabaseDelete() {
        TincNodeMange node = node(10L, "alice", "10.0.10.20");
        node.setId(50L);
        when(nodeMapper.selectTincNodeMangeById(50L)).thenReturn(node);
        when(networkMapper.selectTincNetworkMangeById(10L)).thenReturn(network(10L, "network_a", "10.0.10"));
        when(nodeMapper.deleteTincNodeMangeById(50L)).thenReturn(1);
        when(runtimeRouter.deletePeer(any(), eq("network_a"), eq("alice")))
                .thenReturn(AccessOperationResult.of(true, null));
        when(redisCache.getCacheSet("tinc:client:node-tokens:50")).thenReturn(Set.of("token-hash"));

        assertEquals(1, service.deleteTincNodeMangeById(50L));

        verify(runtimeRouter).deletePeer(argThat(s -> s.getId().equals(1L)), eq("network_a"), eq("alice"));
        verify(redisCache).setCacheObject("tinc:client:revoked-token:token-hash", "50", 24, TimeUnit.HOURS);
        verify(redisCache).deleteObject("tinc:client:token:token-hash");
        verify(nodeMapper).deleteTincNodeMangeById(50L);
    }

    private static TincNodeMange node(Long networkId, String name, String ip) {
        TincNodeMange result = new TincNodeMange();
        result.setNetworkId(networkId);
        result.setNodeName(name);
        result.setNetworkIp(ip);
        result.setPassword("test-password");
        return result;
    }

    private static TincNetworkMange network(Long id, String name, String segment) {
        TincNetworkMange result = new TincNetworkMange();
        result.setId(id);
        result.setServerId(1L);
        result.setServerName("gateway_a");
        result.setNetworkName(name);
        result.setSegment(segment);
        return result;
    }

    private static MangeServer server() {
        MangeServer result = new MangeServer();
        result.setId(1L);
        result.setServerName("gateway_a");
        result.setServerIp("203.0.113.10");
        return result;
    }
}
