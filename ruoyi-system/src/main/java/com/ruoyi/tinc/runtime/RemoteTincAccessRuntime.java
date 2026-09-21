package com.ruoyi.tinc.runtime;

import com.ruoyi.common.tinc.access.AccessNetworkSpec;
import com.ruoyi.common.tinc.access.AccessOperationResult;
import com.ruoyi.common.tinc.access.AccessPeerSpec;
import com.ruoyi.common.tinc.access.AccessRuntimeSummary;
import com.ruoyi.common.tinc.access.TincAccessRuntime;
import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import com.ruoyi.tinc_server.domain.MangeServer;

/** Per-request adapter; it contains no mutable credentials and never executes local shell commands. */
public class RemoteTincAccessRuntime implements TincAccessRuntime {
    private final MangeServer server;
    private final AccessAgentClient client;

    public RemoteTincAccessRuntime(MangeServer server, AccessAgentClient client) {
        this.server = server;
        this.client = client;
    }

    public AccessOperationResult createNetwork(AccessNetworkSpec spec) { return client.createNetwork(server, spec); }
    public TincNetworkStatus inspectNetwork(String name) { return client.inspectNetwork(server, name); }
    public AccessOperationResult decommissionNetwork(String name) { return client.decommissionNetwork(server, name); }
    public AccessOperationResult upsertPeer(String name, AccessPeerSpec peer) { return client.upsertPeer(server, name, peer); }
    public AccessOperationResult deletePeer(String name, String node) { return client.deletePeer(server, name, node); }
    public AccessOperationResult reloadNetwork(String name) { return client.reloadNetwork(server, name); }
    public String readServerMaster(String name) { return client.readServerMaster(server, name); }
    public AccessRuntimeSummary inspectRuntime() { throw new UnsupportedOperationException("请通过 Agent health/runtime 查询"); }
}
