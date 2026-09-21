package com.ruoyi.web.controller.system;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.service.ITincNetworkMangeService;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.service.ITincNodeMangeService;
import com.ruoyi.tinc_server.service.IMangeServerService;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc.runtime.TincRuntimeRouter;
import com.ruoyi.common.tinc.access.AccessOperationResult;
import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import com.ruoyi.common.utils.RsaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TincClientApiControllerTest {
    @Mock private ITincNetworkMangeService networkService;
    @Mock private ITincNodeMangeService nodeService;
    @Mock private RedisCache redisCache;
    @Mock private IMangeServerService serverService;
    @Mock private TincRuntimeRouter runtimeRouter;
    @Mock private HttpServletResponse response;
    private TincClientApiController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new TincClientApiController();
        ReflectionTestUtils.setField(controller, "networkMangeService", networkService);
        ReflectionTestUtils.setField(controller, "nodeMangeService", nodeService);
        ReflectionTestUtils.setField(controller, "redisCache", redisCache);
        ReflectionTestUtils.setField(controller, "mangeServerService", serverService);
        ReflectionTestUtils.setField(controller, "tincRuntimeRouter", runtimeRouter);
    }

    @Test
    void loginKeepsClientProtocolAndResolvesNetworkByStableId() {
        TincNodeMange node = node(5L, 8L, "new_client", "secret", "new_network", "10.0.11.211");
        TincNetworkMange network = network(8L, "new_network");
        when(nodeService.selectByNodeNameExact("new_client")).thenReturn(Collections.singletonList(node));
        when(networkService.selectTincNetworkMangeById(8L)).thenReturn(network);

        JSONObject result = controller.login(loginRequest("new_client", "secret"), response);

        assertEquals(1, result.getIntValue("status"));
        assertEquals("new_client", result.getString("sid"));
        assertEquals("new_network", result.getString("net_name"));
        assertEquals("10.0.11.211", result.getString("node_ip"));
        assertEquals(5L, result.getLongValue("node_id"));
        assertEquals(8L, result.getLongValue("network_id"));
        verify(networkService).selectTincNetworkMangeById(8L);
        verify(networkService, never()).selectByNetworkNameExact(anyString());
        verify(redisCache).setCacheObject(startsWith("tinc:client:token:"), eq("5"), eq(30), any());
    }

    @Test
    void legacyNodeWithoutNetworkIdUsesUniqueExactFallback() {
        TincNodeMange node = node(5L, null, "new_client", "secret", "new_network", "10.0.11.211");
        when(nodeService.selectByNodeNameExact("new_client")).thenReturn(Collections.singletonList(node));
        when(networkService.selectByNetworkNameExact("new_network")).thenReturn(network(8L, "new_network"));

        JSONObject result = controller.login(loginRequest("new_client", "secret"), response);

        assertEquals(1, result.getIntValue("status"));
        verify(networkService).selectByNetworkNameExact("new_network");
        verify(networkService, never()).selectTincNetworkMangeList(any());
    }

    @Test
    void keyUploadRoutesOnlyToNetworkBoundAccessServerAndKeepsProtocol() {
        TincNodeMange node = node(5L, 8L, "new_client", "secret", "new_network", "10.0.11.211");
        TincNetworkMange network = network(8L, "new_network");
        network.setServerId(3L);
        MangeServer server = new MangeServer();
        server.setId(3L); server.setServerIp("203.0.113.10"); server.setRuntimeType("AGENT");
        String publicKey = RsaUtils.generateKeys().get("publicKey");
        String serverMaster = "Address = 203.0.113.10\nPort = 600\nSubnet = 10.0.11.1/32\n\n" + publicKey;
        when(nodeService.selectByNodeNameExact("new_client")).thenReturn(Collections.singletonList(node));
        when(redisCache.getCacheObject(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key.startsWith("tinc:client:token:") ? "5" : null;
        });
        when(networkService.selectTincNetworkMangeById(8L)).thenReturn(network);
        when(serverService.selectMangeServerById(3L)).thenReturn(server);
        when(runtimeRouter.readServerMaster(server, "new_network")).thenReturn(serverMaster);
        TincNetworkStatus ready = new TincNetworkStatus(); ready.setReadiness("READY");
        when(runtimeRouter.upsertPeer(eq(server), eq("new_network"), any()))
                .thenReturn(AccessOperationResult.of(false, ready));
        JSONObject request = new JSONObject();
        request.put("sid", "new_client"); request.put("token", "token"); request.put("action", "exchangeFile");
        request.put("content", "Subnet = 10.0.11.211/32\n\n" + publicKey);

        ResponseEntity<?> responseEntity = controller.uploadKey(MediaType.APPLICATION_JSON_VALUE, request.toJSONString());

        assertEquals(200, responseEntity.getStatusCodeValue());
        assertEquals(serverMaster.replace("\r\n", "\n"), responseEntity.getBody());
        verify(runtimeRouter).upsertPeer(eq(server), eq("new_network"),
                argThat(peer -> "new_client".equals(peer.getNodeName())
                        && "10.0.11.211/32".equals(peer.getSubnet())));
    }

    @Test
    void revokedTokenReturnsStableCodeBeforeNodeLookup() {
        when(redisCache.getCacheObject(startsWith("tinc:client:revoked-token:"))).thenReturn("5");
        Map<String, String> request = new HashMap<>();
        request.put("sid", "new_client");
        request.put("token", "revoked-token");

        JSONObject result = controller.keepAlive(request, response);

        assertEquals("CLIENT_NODE_REVOKED", result.getString("code"));
        verify(response).setStatus(410);
        verify(nodeService, never()).selectByNodeNameExact(anyString());
    }

    private static Map<String, String> loginRequest(String sid, String password) {
        Map<String, String> request = new HashMap<>();
        request.put("sid", sid);
        request.put("password", password);
        return request;
    }

    private static TincNodeMange node(Long id, Long networkId, String name, String password,
                                      String networkName, String ip) {
        TincNodeMange result = new TincNodeMange();
        result.setId(id);
        result.setNetworkId(networkId);
        result.setNodeName(name);
        result.setPassword(password);
        result.setNetworkName(networkName);
        result.setNetworkIp(ip);
        return result;
    }

    private static TincNetworkMange network(Long id, String name) {
        TincNetworkMange result = new TincNetworkMange();
        result.setId(id);
        result.setNetworkName(name);
        result.setPort("600");
        return result;
    }
}
