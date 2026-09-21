package com.ruoyi.common.tinc.access;

import com.ruoyi.common.tinc.runtime.TincNetworkStatus;

public interface TincAccessRuntime {
    AccessOperationResult createNetwork(AccessNetworkSpec spec);
    TincNetworkStatus inspectNetwork(String runtimeName);
    AccessOperationResult decommissionNetwork(String runtimeName);
    AccessOperationResult upsertPeer(String runtimeName, AccessPeerSpec peer);
    AccessOperationResult deletePeer(String runtimeName, String nodeName);
    AccessOperationResult reloadNetwork(String runtimeName);
    String readServerMaster(String runtimeName);
    AccessRuntimeSummary inspectRuntime();
}
