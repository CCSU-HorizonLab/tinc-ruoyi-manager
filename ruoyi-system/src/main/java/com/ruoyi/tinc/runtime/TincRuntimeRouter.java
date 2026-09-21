package com.ruoyi.tinc.runtime;

import com.ruoyi.common.tinc.access.AccessAgentHealth;
import com.ruoyi.common.tinc.access.AccessNetworkSpec;
import com.ruoyi.common.tinc.access.AccessOperationResult;
import com.ruoyi.common.tinc.access.AccessPeerSpec;
import com.ruoyi.common.tinc.access.LocalTincAccessRuntime;
import com.ruoyi.common.tinc.access.TincAccessRuntime;
import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import com.ruoyi.tinc_server.domain.MangeServer;
import org.springframework.stereotype.Service;

@Service
public class TincRuntimeRouter {
    public static final String RUNTIME_LOCAL = "LOCAL";
    public static final String RUNTIME_AGENT = "AGENT";

    private final LocalTincAccessRuntime localRuntime;
    private final AccessAgentClient agentClient;

    public TincRuntimeRouter(LocalTincAccessRuntime localRuntime, AccessAgentClient agentClient) {
        this.localRuntime = localRuntime;
        this.agentClient = agentClient;
    }

    public AccessOperationResult createNetwork(MangeServer server, AccessNetworkSpec spec) {
        if (isAgent(server)) {
            AccessAgentHealth health = agentClient.health(server);
            if (health == null || !"UP".equals(health.getStatus())) {
                throw new AccessAgentException("AGENT_UNREACHABLE", "Access Agent 未达到 ONLINE 状态");
            }
        }
        return runtime(server).createNetwork(spec);
    }

    public TincNetworkStatus inspectNetwork(MangeServer server, String name) {
        return runtime(server).inspectNetwork(name);
    }
    public AccessOperationResult decommissionNetwork(MangeServer server, String name) {
        return runtime(server).decommissionNetwork(name);
    }
    public AccessOperationResult upsertPeer(MangeServer server, String name, AccessPeerSpec peer) {
        return runtime(server).upsertPeer(name, peer);
    }
    public AccessOperationResult deletePeer(MangeServer server, String name, String node) {
        return runtime(server).deletePeer(name, node);
    }
    public String readServerMaster(MangeServer server, String name) { return runtime(server).readServerMaster(name); }
    public AccessAgentHealth health(MangeServer server) { return agentClient.health(server); }

    public boolean isAgent(MangeServer server) {
        return server != null && RUNTIME_AGENT.equalsIgnoreCase(server.getRuntimeType());
    }

    private TincAccessRuntime runtime(MangeServer server) {
        if (server == null) throw new IllegalArgumentException("接入服务器不能为空");
        return isAgent(server) ? new RemoteTincAccessRuntime(server, agentClient) : localRuntime;
    }
}
