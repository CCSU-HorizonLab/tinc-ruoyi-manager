package com.ruoyi.web.controller.system;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.tinc.session.TincClientSessionStore;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.service.ITincNetworkMangeService;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.service.ITincNodeMangeService;
import com.ruoyi.tinc_server.service.IMangeServerService;
import com.ruoyi.tinc.runtime.TincRuntimeRouter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spring MVC HTTP/JSON contract with isolated in-memory member fixtures. */
class TincClientApiHttpContractTest {
    private final ITincNodeMangeService nodes = mock(ITincNodeMangeService.class);
    private final ITincNetworkMangeService networks = mock(ITincNetworkMangeService.class);
    private final TincClientSessionStore sessions = mock(TincClientSessionStore.class);
    private final TincClientLoginLimiter limiter = mock(TincClientLoginLimiter.class);
    private MockMvc http;
    private TincNodeMange self;
    private TincNodeMange peer;
    private final AtomicReference<java.util.List<TincNodeMange>> members = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        self = node(5L, "client_a", "10.0.11.211");
        self.setAccessCode("TL-7K9M-2Q4F");
        peer = node(6L, "client_b", "10.0.11.212");
        members.set(Arrays.asList(self, peer));
        TincNetworkMange network = new TincNetworkMange();
        network.setId(8L);
        network.setNetworkName("new_network");
        network.setSegment("10.0.11");
        when(nodes.selectByAccessCode("TL-7K9M-2Q4F")).thenReturn(self);
        when(nodes.verifyClientPassword(eq(self), anyString())).thenReturn(true);
        when(nodes.selectByNodeNameExact("client_a")).thenReturn(Collections.singletonList(self));
        when(nodes.selectTincNodeMangeList(any())).thenAnswer(call -> members.get());
        when(nodes.updateClientStatus(5L, null, "在线")).thenReturn(1);
        when(networks.selectTincNetworkMangeById(8L)).thenReturn(network);
        when(sessions.issue(5L)).thenReturn("integration-test-token");
        when(sessions.isValid(5L, "integration-test-token")).thenReturn(true);
        when(sessions.renew(5L, "integration-test-token")).thenReturn(true);
        when(limiter.consume(anyString())).thenReturn(0L);

        TincClientRouteSnapshot routeSnapshot = new TincClientRouteSnapshot();
        ReflectionTestUtils.setField(routeSnapshot, "nodeService", nodes);
        TincClientApiController controller = new TincClientApiController();
        ReflectionTestUtils.setField(controller, "nodeMangeService", nodes);
        ReflectionTestUtils.setField(controller, "networkMangeService", networks);
        ReflectionTestUtils.setField(controller, "mangeServerService", mock(IMangeServerService.class));
        ReflectionTestUtils.setField(controller, "tincRuntimeRouter", mock(TincRuntimeRouter.class));
        ReflectionTestUtils.setField(controller, "sessionStore", sessions);
        ReflectionTestUtils.setField(controller, "loginLimiter", limiter);
        ReflectionTestUtils.setField(controller, "routeSnapshot", routeSnapshot);
        http = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void actualLoginAndKeepaliveHttpJsonPreserveLegacyFieldsAndRevision() throws Exception {
        MvcResult loginResponse = http.perform(post("/api/tinc/client/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessCode\":\"TL-7K9M-2Q4F\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        JSONObject login = body(loginResponse);
        assertEquals(1, login.getIntValue("status"));
        assertEquals("client_a", login.getString("sid"));
        assertEquals("TL-7K9M-2Q4F", login.getString("access_code"));
        assertEquals("integration-test-token", login.getString("token"));
        assertEquals("new_network", login.getString("net_name"));
        assertEquals(8, login.getIntValue("network_id"));
        assertEquals(5, login.getIntValue("node_id"));
        assertEquals("10.0.11.211", login.getString("node_ip"));
        assertEquals("登录成功", login.getString("msg"));
        assertRoutesAndRevision(login, "10.0.11.1/32", "10.0.11.212/32");
        assertEquals(120, login.getIntValue("route_lease_seconds"));
        System.out.println("PHASE1C_LOGIN_JSON=" + login.toJSONString().replace("integration-test-token", "<redacted>"));

        MvcResult keepaliveResponse = http.perform(post("/api/tinc/client/keepalive")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sid\":\"client_a\",\"token\":\"integration-test-token\"}"))
                .andExpect(status().isOk()).andReturn();
        assertEquals("no-store", keepaliveResponse.getResponse().getHeader("Cache-Control"));
        java.util.UUID.fromString(keepaliveResponse.getResponse().getHeader("X-Tinc-Request-Id"));
        JSONObject keepalive = body(keepaliveResponse);
        assertEquals("success", keepalive.getString("status"));
        assertEquals("心跳成功", keepalive.getString("msg"));
        assertRoutesAndRevision(keepalive, "10.0.11.1/32", "10.0.11.212/32");
        assertEquals(login.getString("route_revision"), keepalive.getString("route_revision"));
        System.out.println("PHASE1C_KEEPALIVE_JSON=" + keepalive.toJSONString());
    }

    @Test
    void httpRejectsInvalidRevokedAndUnavailableSessionsWithoutNewLease() throws Exception {
        String request = "{\"sid\":\"client_a\",\"token\":\"bad-token\"}";
        JSONObject invalid = response(request, 401);
        assertEquals("CLIENT_TOKEN_INVALID", invalid.getString("code"));
        assertNoLease(invalid);

        when(sessions.isRevoked("bad-token")).thenReturn(true);
        JSONObject revoked = response(request, 410);
        assertEquals("CLIENT_NODE_REVOKED", revoked.getString("code"));
        assertNoLease(revoked);

        when(sessions.isRevoked("bad-token")).thenThrow(new IllegalStateException("Redis offline"));
        JSONObject unavailable = response(request, 503);
        assertEquals("CLIENT_KEEPALIVE_FAILED", unavailable.getString("code"));
        assertNoLease(unavailable);
        verify(sessions, never()).renew(eq(5L), eq("bad-token"));
    }

    @Test
    void revokedMemberChangesAuthenticatedHttpRouteSnapshot() throws Exception {
        String request = "{\"sid\":\"client_a\",\"token\":\"integration-test-token\"}";
        JSONObject before = response(request, 200);
        assertRoutesAndRevision(before, "10.0.11.1/32", "10.0.11.212/32");
        peer.setStatus("已撤销");
        JSONObject after = response(request, 200);
        assertRoutesAndRevision(after, "10.0.11.1/32");
        assertNotEquals(before.getString("route_revision"), after.getString("route_revision"));
        members.set(Collections.singletonList(self));
        assertEquals(after.getString("route_revision"), response(request, 200).getString("route_revision"));
    }

    @Test
    void loginRouteQueryFailureReturns503WithoutTokenOrLease() throws Exception {
        when(nodes.selectTincNodeMangeList(any())).thenThrow(new IllegalStateException("isolated query failure"));
        MvcResult result = http.perform(post("/api/tinc/client/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessCode\":\"TL-7K9M-2Q4F\",\"password\":\"test-password\"}"))
                .andExpect(status().isServiceUnavailable()).andReturn();
        JSONObject error = body(result);
        assertEquals(0, error.getIntValue("status"));
        assertEquals("CLIENT_ROUTE_SNAPSHOT_UNAVAILABLE", error.getString("code"));
        assertFalse(error.containsKey("token"));
        assertFalse(error.containsKey("allowed_routes"));
        assertFalse(error.containsKey("route_revision"));
        assertFalse(error.containsKey("route_lease_seconds"));
        verify(sessions, never()).issue(anyLong());
    }

    private JSONObject response(String request, int expectedHttpStatus) throws Exception {
        MvcResult result = http.perform(post("/api/tinc/client/keepalive")
                .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().is(expectedHttpStatus)).andReturn();
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        java.util.UUID.fromString(result.getResponse().getHeader("X-Tinc-Request-Id"));
        return body(result);
    }

    private static JSONObject body(MvcResult result) {
        return JSONObject.parseObject(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    private static void assertNoLease(JSONObject body) {
        assertEquals("error", body.getString("status"));
        assertFalse(body.containsKey("allowed_routes"));
        assertFalse(body.containsKey("route_revision"));
        assertFalse(body.containsKey("route_lease_seconds"));
    }

    private static void assertRoutesAndRevision(JSONObject body, String... expected) throws Exception {
        JSONArray routes = body.getJSONArray("allowed_routes");
        assertNotNull(routes);
        assertEquals(expected.length, routes.size());
        StringBuilder canonical = new StringBuilder();
        for (int i = 0; i < expected.length; i++) {
            assertTrue(routes.get(i) instanceof String, "route must be a JSON string");
            assertEquals(expected[i], routes.getString(i));
            canonical.append(i == 0 ? "gateway=" : "peer=").append(expected[i]).append('\n');
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder("routes-");
        for (byte b : digest) hex.append(String.format("%02x", b & 255));
        assertEquals(hex.toString(), body.getString("route_revision"));
    }

    private static TincNodeMange node(Long id, String name, String ip) {
        TincNodeMange result = new TincNodeMange();
        result.setId(id);
        result.setNetworkId(8L);
        result.setNetworkName("new_network");
        result.setNodeName(name);
        result.setNetworkIp(ip);
        return result;
    }
}
