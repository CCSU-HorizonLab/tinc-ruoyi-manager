package com.ruoyi.tinc.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.tinc.access.AccessAgentHealth;
import com.ruoyi.common.tinc.access.AccessNetworkSpec;
import com.ruoyi.common.tinc.access.AccessOperationResult;
import com.ruoyi.common.tinc.access.AccessPeerSpec;
import com.ruoyi.common.tinc.access.AccessRuntimeValidator;
import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import com.ruoyi.tinc_server.domain.MangeServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Service
public class HttpAccessAgentClient implements AccessAgentClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public HttpAccessAgentClient(ObjectMapper objectMapper,
            @Value("${tinclink.agent.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${tinclink.agent.read-timeout-ms:60000}") int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
    }

    @Override
    public AccessAgentHealth health(MangeServer server) {
        return exchange(server, "/api/v1/health", HttpMethod.GET, null, AccessAgentHealth.class);
    }

    @Override
    public AccessOperationResult createNetwork(MangeServer server, AccessNetworkSpec spec) {
        return exchange(server, "/api/v1/networks", HttpMethod.POST, spec, AccessOperationResult.class);
    }

    @Override
    public TincNetworkStatus inspectNetwork(MangeServer server, String runtimeName) {
        return exchange(server, networkPath(runtimeName) + "/status", HttpMethod.GET, null, TincNetworkStatus.class);
    }

    @Override
    public AccessOperationResult decommissionNetwork(MangeServer server, String runtimeName) {
        return exchange(server, networkPath(runtimeName), HttpMethod.DELETE, null, AccessOperationResult.class);
    }

    @Override
    public AccessOperationResult upsertPeer(MangeServer server, String runtimeName, AccessPeerSpec peer) {
        return exchange(server, networkPath(runtimeName) + "/peers", HttpMethod.POST, peer, AccessOperationResult.class);
    }

    @Override
    public AccessOperationResult deletePeer(MangeServer server, String runtimeName, String nodeName) {
        AccessRuntimeValidator.peerName(nodeName);
        return exchange(server, networkPath(runtimeName) + "/peers/" + nodeName,
                HttpMethod.DELETE, null, AccessOperationResult.class);
    }

    @Override
    public AccessOperationResult reloadNetwork(MangeServer server, String runtimeName) {
        return exchange(server, networkPath(runtimeName) + "/reload", HttpMethod.POST,
                new Object(), AccessOperationResult.class);
    }

    @Override
    public String readServerMaster(MangeServer server, String runtimeName) {
        AccessOperationResult result = exchange(server, networkPath(runtimeName) + "/server-master",
                HttpMethod.GET, null, AccessOperationResult.class);
        if (result == null || result.getServerMasterHost() == null) {
            throw new AccessAgentException("TINC_CONFIG_INVALID", "Access Agent 未返回 server_master 配置");
        }
        return result.getServerMasterHost();
    }

    private String networkPath(String runtimeName) {
        return "/api/v1/networks/" + AccessRuntimeValidator.name(runtimeName, "网络名称");
    }

    private <T> T exchange(MangeServer server, String path, HttpMethod method, Object request, Class<T> type) {
        validateServer(server);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(server.getAgentSecret());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        try {
            ResponseEntity<T> response = restTemplate.exchange(baseUrl(server) + path, method,
                    new HttpEntity<>(request, headers), type);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            String code = (e.getRawStatusCode() == 401 || e.getRawStatusCode() == 403)
                    ? "AGENT_UNAUTHORIZED" : readError(e.getResponseBodyAsByteArray(), "RUNTIME_FAILURE", "code");
            String message = readError(e.getResponseBodyAsByteArray(),
                    "Access Agent 返回错误（HTTP " + e.getRawStatusCode() + "）", "message");
            throw new AccessAgentException(code, message);
        } catch (ResourceAccessException e) {
            throw new AccessAgentException("AGENT_UNREACHABLE", "Access Agent 无法连接或响应超时", e);
        }
    }

    private String readError(byte[] body, String fallback, String field) {
        try {
            JsonNode node = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode value = node.get(field);
            return value != null && value.isTextual() ? value.asText() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String baseUrl(MangeServer server) {
        return "http://" + server.getServerIp() + ":" + server.getAgentPort();
    }

    private void validateServer(MangeServer server) {
        if (server == null || server.getServerIp() == null
                || !server.getServerIp().matches("(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}")) {
            throw new AccessAgentException("AGENT_UNREACHABLE", "Access Server 控制地址无效");
        }
        if (server.getAgentPort() == null || server.getAgentPort() < 1 || server.getAgentPort() > 65535) {
            throw new AccessAgentException("AGENT_UNREACHABLE", "Access Agent 端口无效");
        }
        if (server.getAgentSecret() == null || server.getAgentSecret().length() < 32) {
            throw new AccessAgentException("AGENT_UNAUTHORIZED", "Access Agent Secret 未配置");
        }
    }
}
