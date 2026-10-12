package com.ruoyi.web.controller.system;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.service.ITincNetworkMangeService;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.service.ITincNodeMangeService;
import com.ruoyi.tinc_node.service.impl.TincNodeMangeServiceImpl;
import com.ruoyi.tinc_node.mapper.TincNodeMangeMapper;
import com.ruoyi.tinc_network.mapper.TincNetworkMangeMapper;
import com.ruoyi.tinc_server.service.IMangeServerService;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc.runtime.TincRuntimeRouter;
import com.ruoyi.common.tinc.access.AccessOperationResult;
import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import com.ruoyi.common.utils.RsaUtils;
import com.ruoyi.common.security.TincNodePasswordCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TincClientApiControllerTest {
    @Mock private ITincNetworkMangeService networkService;
    @Mock private ITincNodeMangeService nodeService;
    @Mock private RedisCache redisCache;
    @Mock private IMangeServerService serverService;
    @Mock private TincRuntimeRouter runtimeRouter;
    @Mock private TincClientLoginLimiter loginLimiter;
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
        ReflectionTestUtils.setField(controller, "loginLimiter", loginLimiter);
        when(loginLimiter.consume(anyString())).thenReturn(0L);
        when(nodeService.verifyClientPassword(any(TincNodeMange.class), anyString()))
                .thenAnswer(invocation -> {
                    TincNodeMange node = invocation.getArgument(0);
                    String raw = invocation.getArgument(1);
                    return TincNodePasswordCodec.matches(raw, node.getPassword());
                });
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
    void loginRejectsSignedIpv4OctetsBeforeIssuingToken() {
        TincNodeMange node = node(5L, 8L, "new_client", "secret", "new_network", "10.0.11.+211");
        when(nodeService.selectByNodeNameExact("new_client")).thenReturn(Collections.singletonList(node));

        JSONObject result = controller.login(loginRequest("new_client", "secret"), response);

        assertEquals("CLIENT_NETWORK_IP_INVALID", result.getString("code"));
        verify(redisCache, never()).setCacheObject(startsWith("tinc:client:token:"), any(), anyInt(), any());
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
        when(nodeService.markClientConfigured(5L)).thenReturn(1);
        JSONObject request = new JSONObject();
        request.put("sid", "new_client"); request.put("token", "token"); request.put("action", "exchangeFile");
        request.put("content", "Subnet = 10.0.11.211/32\n\n" + publicKey);

        ResponseEntity<?> responseEntity = controller.uploadKey(MediaType.APPLICATION_JSON_VALUE, request.toJSONString());

        assertEquals(200, responseEntity.getStatusCodeValue());
        assertEquals(serverMaster.replace("\r\n", "\n"), responseEntity.getBody());
        verify(nodeService).markClientConfigured(5L);
        verify(nodeService, never()).updateTincNodeMange(any());
        verify(runtimeRouter).upsertPeer(eq(server), eq("new_network"),
                argThat(peer -> "new_client".equals(peer.getNodeName())
                        && "10.0.11.211/32".equals(peer.getSubnet())));
    }

    @Test
    void keyUploadAcceptsAlreadyConfiguredRowButRejectsMissingRow() {
        TincNodeMange node = node(5L, 8L, "new_client", "secret", "new_network", "10.0.11.211");
        node.setStatus("已配置");
        TincNetworkMange network = network(8L, "new_network");
        network.setServerId(3L);
        MangeServer server = new MangeServer();
        server.setId(3L); server.setServerIp("203.0.113.10");
        String publicKey = RsaUtils.generateKeys().get("publicKey");
        when(nodeService.selectByNodeNameExact("new_client")).thenReturn(Collections.singletonList(node));
        when(redisCache.getCacheObject(anyString())).thenAnswer(call ->
                ((String) call.getArgument(0)).startsWith("tinc:client:token:") ? "5" : null);
        when(networkService.selectTincNetworkMangeById(8L)).thenReturn(network);
        when(serverService.selectMangeServerById(3L)).thenReturn(server);
        when(runtimeRouter.readServerMaster(server, "new_network")).thenReturn(
                "Address = 203.0.113.10\nPort = 600\nSubnet = 10.0.11.1/32\n\n" + publicKey);
        TincNetworkStatus ready = new TincNetworkStatus(); ready.setReadiness("READY");
        when(runtimeRouter.upsertPeer(eq(server), eq("new_network"), any()))
                .thenReturn(AccessOperationResult.of(false, ready));
        when(nodeService.markClientConfigured(5L)).thenReturn(0);
        when(nodeService.selectTincNodeMangeById(5L)).thenReturn(node, null);
        JSONObject request = new JSONObject();
        request.put("sid", "new_client"); request.put("token", "token");
        request.put("action", "exchangeFile");
        request.put("content", "Subnet = 10.0.11.211/32\n\n" + publicKey);

        assertEquals(200, controller.uploadKey(MediaType.APPLICATION_JSON_VALUE, request.toJSONString())
                .getStatusCodeValue());
        ResponseEntity<?> missingRow = controller.uploadKey(MediaType.APPLICATION_JSON_VALUE, request.toJSONString());
        assertEquals(503, missingRow.getStatusCodeValue());
        assertEquals("CLIENT_CONFIG_STATE_PERSIST_FAILED", JSONObject.parseObject(
                new String((byte[]) missingRow.getBody(), StandardCharsets.UTF_8)).getString("code"));
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
    }

    @Test
    void revokedTokenCannotReportClientStatus() {
        when(redisCache.getCacheObject(startsWith("tinc:client:revoked-token:"))).thenReturn("5");
        Map<String, String> request = new HashMap<>();
        request.put("sid", "new_client"); request.put("token", "revoked-token");
        request.put("type", "add"); request.put("result", "success");

        JSONObject result = controller.editAddInfo(request, response);

        assertEquals("CLIENT_NODE_REVOKED", result.getString("code"));
        verify(response).setStatus(410);
        verifyNoInteractions(nodeService);
    }

    @Test
    void statusAndKeepaliveDoNotClaimSuccessWhenDatabaseStateIsMissing() {
        TincNodeMange node = node(5L, 8L, "new_client", "secret", "new_network", "10.0.11.211");
        node.setStatus("配置成功");
        node.setNodeStatus("在线");
        when(nodeService.selectByNodeNameExact("new_client")).thenReturn(Collections.singletonList(node));
        when(redisCache.getCacheObject(anyString())).thenAnswer(call ->
                ((String) call.getArgument(0)).startsWith("tinc:client:token:") ? "5" : null);
        when(nodeService.updateTincNodeMange(any())).thenReturn(0);
        when(nodeService.selectTincNodeMangeById(5L)).thenReturn(node, null, node, null);
        Map<String, String> request = new HashMap<>();
        request.put("sid", "new_client"); request.put("token", "token");
        request.put("type", "add"); request.put("result", "success");

        assertEquals("success", controller.editAddInfo(request, response).getString("status"));
        assertEquals("CLIENT_STATUS_UPDATE_FAILED", controller.editAddInfo(request, response).getString("code"));
        assertEquals("success", controller.keepAlive(request, response).getString("status"));
        assertEquals("CLIENT_KEEPALIVE_FAILED", controller.keepAlive(request, response).getString("code"));
        verify(response, times(2)).setStatus(503);
    }

    @Test
    void changedPublicKeyMustNotRehashPasswordOrRevokeSession() {
        TincNodeMangeMapper mapper = mock(TincNodeMangeMapper.class);
        TincNetworkMangeMapper networks = mock(TincNetworkMangeMapper.class);
        TincNodeMangeServiceImpl realService = new TincNodeMangeServiceImpl();
        ReflectionTestUtils.setField(realService, "tincNodeMangeMapper", mapper);
        ReflectionTestUtils.setField(realService, "tincNetworkMangeMapper", networks);
        ReflectionTestUtils.setField(realService, "mangeServerService", serverService);
        ReflectionTestUtils.setField(realService, "redisCache", redisCache);
        ReflectionTestUtils.setField(controller, "nodeMangeService", realService);

        String storedHash = TincNodePasswordCodec.encode("secret");
        TincNodeMange node = node(5L, 8L, "new_client", storedHash, "new_network", "10.0.11.211");
        TincNetworkMange network = network(8L, "new_network");
        network.setServerId(3L);
        network.setSegment("10.0.11");
        MangeServer server = new MangeServer();
        server.setId(3L); server.setServerIp("203.0.113.10");
        when(mapper.selectByNodeNameExact("new_client")).thenReturn(Collections.singletonList(node));
        when(mapper.selectTincNodeMangeById(5L)).thenReturn(node);
        when(mapper.updateTincNodeMange(any())).thenReturn(1);
        when(mapper.markClientConfigured(5L)).thenThrow(new IllegalStateException("test failure"))
                .thenReturn(1);
        when(networks.selectTincNetworkMangeById(8L)).thenReturn(network);
        when(networkService.selectTincNetworkMangeById(8L)).thenReturn(network);
        when(serverService.selectMangeServerById(3L)).thenReturn(server);
        when(redisCache.getCacheObject(anyString())).thenAnswer(call ->
                ((String) call.getArgument(0)).startsWith("tinc:client:token:") ? "5" : null);
        String publicKey = RsaUtils.generateKeys().get("publicKey");
        when(runtimeRouter.readServerMaster(server, "new_network")).thenReturn(
                "Address = 203.0.113.10\nPort = 600\nSubnet = 10.0.11.1/32\n\n" + publicKey);
        TincNetworkStatus ready = new TincNetworkStatus(); ready.setReadiness("READY");
        when(runtimeRouter.upsertPeer(eq(server), eq("new_network"), any()))
                .thenReturn(AccessOperationResult.of(true, ready), AccessOperationResult.of(false, ready));
        JSONObject request = new JSONObject();
        request.put("sid", "new_client"); request.put("token", "token");
        request.put("action", "exchangeFile");
        request.put("content", "Subnet = 10.0.11.211/32\n\n" + publicKey);

        ResponseEntity<?> failedStatus = controller.uploadKey(MediaType.APPLICATION_JSON_VALUE, request.toJSONString());
        assertEquals(503, failedStatus.getStatusCodeValue());
        assertEquals("CLIENT_CONFIG_STATE_PERSIST_FAILED", JSONObject.parseObject(
                new String((byte[]) failedStatus.getBody(), StandardCharsets.UTF_8)).getString("code"));
        assertEquals(storedHash, node.getPassword(), "Applying a public key must not rewrite credentials");
        assertTrue(realService.verifyClientPassword(node, "secret"));
        // An unchanged-key retry repairs the status after the first database write fails.
        assertEquals(200, controller.uploadKey(MediaType.APPLICATION_JSON_VALUE, request.toJSONString())
                .getStatusCodeValue());
        verify(mapper, times(2)).markClientConfigured(5L);
        verify(mapper, never()).updateTincNodeMange(any());
        verify(redisCache, never()).deleteObject(anyString());
        verify(redisCache, never()).setCacheObject(startsWith("tinc:client:revoked-token:"), any(), anyInt(), any());
        assertEquals(1, controller.login(loginRequest("new_client", "secret"), response).getIntValue("status"));
    }

    @Test
    void accessCodeLoginSuccessResolvesNodeDirectlyWithoutNodeNameLookup() {
        TincNodeMange node = node(5L, 8L, "new_client", "secret", "new_network", "10.0.11.211");
        node.setAccessCode("TL-7K9M-2Q4F");
        TincNetworkMange network = network(8L, "new_network");
        when(nodeService.selectByAccessCode("TL-7K9M-2Q4F")).thenReturn(node);
        when(networkService.selectTincNetworkMangeById(8L)).thenReturn(network);

        JSONObject result = controller.login(accessCodeLoginRequest("TL-7k9m-2q4f", "secret"), response);

        assertEquals(1, result.getIntValue("status"));
        assertEquals("new_client", result.getString("sid"));
        assertEquals("TL-7K9M-2Q4F", result.getString("access_code"));
        assertEquals("new_network", result.getString("net_name"));
        assertEquals("10.0.11.211", result.getString("node_ip"));
        assertEquals(5L, result.getLongValue("node_id"));
        assertEquals(8L, result.getLongValue("network_id"));
        verify(nodeService).selectByAccessCode("TL-7K9M-2Q4F");
        verify(nodeService, never()).selectByNodeNameExact(anyString());
        verify(redisCache).setCacheObject(startsWith("tinc:client:token:"), eq("5"), eq(30), any());
    }

    @Test
    void accessCodeLoginWithWrongPasswordFailsWithoutGeneratingToken() {
        TincNodeMange node = node(5L, 8L, "new_client", "secret", "new_network", "10.0.11.211");
        node.setAccessCode("TL-7K9M-2Q4F");
        when(nodeService.selectByAccessCode("TL-7K9M-2Q4F")).thenReturn(node);

        JSONObject result = controller.login(accessCodeLoginRequest("TL-7K9M-2Q4F", "wrong_pass"), response);

        assertEquals(0, result.getIntValue("status"));
        assertEquals("CLIENT_CREDENTIAL_INVALID", result.getString("code"));
        assertEquals("接入码或密码无效", result.getString("msg"));
        verify(redisCache, never()).setCacheObject(startsWith("tinc:client:token:"), any(), anyInt(), any());
        verify(loginLimiter, never()).clearCredential(anyString());
    }

    @Test
    void repeatedLoginIsLimitedBeforeNodeLookupAcrossAccessCodeAliases() {
        when(loginLimiter.consume("access:TL-7K9M-2Q4F")).thenReturn(47L);

        JSONObject result = controller.login(accessCodeLoginRequest("TL-7k9m-2q4f", "guess"), response);
        Map<String, String> alias = new HashMap<>();
        alias.put("access_code", "TL-7K9M-2Q4F");
        alias.put("password", "guess");
        JSONObject aliasResult = controller.login(alias, response);

        assertEquals("CLIENT_LOGIN_RATE_LIMITED", result.getString("code"));
        assertEquals(47L, result.getLongValue("retry_after"));
        assertEquals("CLIENT_LOGIN_RATE_LIMITED", aliasResult.getString("code"));
        verify(response, times(2)).setStatus(429);
        verify(response, times(2)).setHeader("Retry-After", "47");
        verify(nodeService, never()).selectByAccessCode(anyString());
        verify(redisCache, never()).setCacheObject(startsWith("tinc:client:token:"), any(), anyInt(), any());
    }

    @Test
    void limiterFailureRejectsLoginWithoutIssuingAToken() {
        when(loginLimiter.consume(anyString())).thenThrow(
                new TincClientLoginLimiter.LimiterUnavailableException(new IllegalStateException("test Redis outage")));

        JSONObject result = controller.login(accessCodeLoginRequest("TL-7K9M-2Q4F", "secret"), response);

        assertEquals("CLIENT_LOGIN_LIMITER_UNAVAILABLE", result.getString("code"));
        verify(response).setStatus(503);
        verify(nodeService, never()).selectByAccessCode(anyString());
        verify(redisCache, never()).setCacheObject(startsWith("tinc:client:token:"), any(), anyInt(), any());
    }

    @Test
    void accessCodeLoginWithNonExistentCodeFails() {
        when(nodeService.selectByAccessCode("TL-NONO-NONE")).thenReturn(null);

        JSONObject result = controller.login(accessCodeLoginRequest("TL-NONO-NONE", "any_pass"), response);

        assertEquals(0, result.getIntValue("status"));
        assertEquals("CLIENT_CREDENTIAL_INVALID", result.getString("code"));
        assertEquals("接入码或密码无效", result.getString("msg"));
    }

    @Test
    void crossNetworkSameNameAndSamePasswordCanLoginViaRespectiveAccessCodes() {
        // Network A (id=10) has node "client_common" (id=101, accessCode="TL-NETA-1111")
        TincNodeMange nodeA = node(101L, 10L, "client_common", "same_pass", "network_a", "10.0.10.2");
        nodeA.setAccessCode("TL-NETA-1111");
        TincNetworkMange netA = network(10L, "network_a");

        // Network B (id=20) has node "client_common" (id=202, accessCode="TL-NETB-2222")
        TincNodeMange nodeB = node(202L, 20L, "client_common", "same_pass", "network_b", "10.0.20.2");
        nodeB.setAccessCode("TL-NETB-2222");
        TincNetworkMange netB = network(20L, "network_b");

        when(nodeService.selectByAccessCode("TL-NETA-1111")).thenReturn(nodeA);
        when(nodeService.selectByAccessCode("TL-NETB-2222")).thenReturn(nodeB);
        when(networkService.selectTincNetworkMangeById(10L)).thenReturn(netA);
        when(networkService.selectTincNetworkMangeById(20L)).thenReturn(netB);

        // Login to Node A
        JSONObject resultA = controller.login(accessCodeLoginRequest("TL-NETA-1111", "same_pass"), response);
        assertEquals(1, resultA.getIntValue("status"));
        assertEquals(101L, resultA.getLongValue("node_id"));
        assertEquals(10L, resultA.getLongValue("network_id"));
        assertEquals("network_a", resultA.getString("net_name"));

        // Login to Node B
        JSONObject resultB = controller.login(accessCodeLoginRequest("TL-NETB-2222", "same_pass"), response);
        assertEquals(1, resultB.getIntValue("status"));
        assertEquals(202L, resultB.getLongValue("node_id"));
        assertEquals(20L, resultB.getLongValue("network_id"));
        assertEquals("network_b", resultB.getString("net_name"));
    }

    @Test
    void legacySidWithAmbiguousCandidatesReturnsClientLegacyLoginAmbiguous() {
        TincNodeMange nodeA = node(101L, 10L, "client_common", "pass_a", "network_a", "10.0.10.2");
        TincNodeMange nodeB = node(202L, 20L, "client_common", "pass_b", "network_b", "10.0.20.2");
        when(nodeService.selectByNodeNameExact("client_common")).thenReturn(java.util.Arrays.asList(nodeA, nodeB));

        JSONObject result = controller.login(loginRequest("client_common", "pass_a"), response);

        assertEquals(0, result.getIntValue("status"));
        assertEquals("CLIENT_LEGACY_LOGIN_AMBIGUOUS", result.getString("code"));
        assertTrue(result.getString("msg").contains("接入码"));
        verify(redisCache, never()).setCacheObject(startsWith("tinc:client:token:"), any(), anyInt(), any());
    }

    private static Map<String, String> loginRequest(String sid, String password) {
        Map<String, String> request = new HashMap<>();
        request.put("sid", sid);
        request.put("password", password);
        return request;
    }

    private static Map<String, String> accessCodeLoginRequest(String accessCode, String password) {
        Map<String, String> request = new HashMap<>();
        request.put("accessCode", accessCode);
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
