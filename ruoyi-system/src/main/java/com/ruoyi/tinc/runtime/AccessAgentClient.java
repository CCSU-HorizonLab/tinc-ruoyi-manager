package com.ruoyi.tinc.runtime;

import com.ruoyi.common.tinc.access.AccessAgentHealth;
import com.ruoyi.common.tinc.access.AccessNetworkSpec;
import com.ruoyi.common.tinc.access.AccessOperationResult;
import com.ruoyi.common.tinc.access.AccessPeerSpec;
import com.ruoyi.common.tinc.runtime.TincNetworkStatus;
import com.ruoyi.tinc_server.domain.MangeServer;

public interface AccessAgentClient {
    AccessAgentHealth health(MangeServer server);
    AccessOperationResult createNetwork(MangeServer server, AccessNetworkSpec spec);
    TincNetworkStatus inspectNetwork(MangeServer server, String runtimeName);
    AccessOperationResult decommissionNetwork(MangeServer server, String runtimeName);
    AccessOperationResult upsertPeer(MangeServer server, String runtimeName, AccessPeerSpec peer);
    AccessOperationResult deletePeer(MangeServer server, String runtimeName, String nodeName);
    AccessOperationResult reloadNetwork(MangeServer server, String runtimeName);
    String readServerMaster(MangeServer server, String runtimeName);
}
