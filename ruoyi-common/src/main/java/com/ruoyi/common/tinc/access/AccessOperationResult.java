package com.ruoyi.common.tinc.access;

import com.ruoyi.common.tinc.runtime.TincNetworkStatus;

public class AccessOperationResult {
    private String status = "OK";
    private boolean changed;
    private TincNetworkStatus networkStatus;
    private String serverMasterHost;

    public static AccessOperationResult of(boolean changed, TincNetworkStatus networkStatus) {
        AccessOperationResult result = new AccessOperationResult();
        result.setChanged(changed);
        result.setNetworkStatus(networkStatus);
        return result;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isChanged() { return changed; }
    public void setChanged(boolean changed) { this.changed = changed; }
    public TincNetworkStatus getNetworkStatus() { return networkStatus; }
    public void setNetworkStatus(TincNetworkStatus networkStatus) { this.networkStatus = networkStatus; }
    public String getServerMasterHost() { return serverMasterHost; }
    public void setServerMasterHost(String serverMasterHost) { this.serverMasterHost = serverMasterHost; }
}
