package com.ruoyi.common.tinc.runtime;

public interface TincRuntimeManager {
    TincNetworkStatus validateNetworkConfig(String netName);
    TincNetworkStatus ensureNetworkReady(String netName);
    void startAndEnableNetwork(String netName);
    void reloadNetwork(String netName);
    TincNetworkStatus inspectNetworkStatus(String netName);
    void verifyPeerApplied(String netName, String sid, String expectedSha256);
    void ensureFirewallPort(int port);
    void detectInterfaceAndPortConflicts(String netName);
    /** Stop and disable the service without deleting configuration or key material. */
    TincNetworkStatus decommissionNetwork(String netName);
}
