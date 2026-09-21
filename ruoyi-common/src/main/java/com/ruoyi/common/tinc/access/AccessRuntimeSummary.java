package com.ruoyi.common.tinc.access;

public class AccessRuntimeSummary {
    private String agentStatus = "UP";
    private int networkCount;
    private int activeNetworkCount;

    public String getAgentStatus() { return agentStatus; }
    public void setAgentStatus(String agentStatus) { this.agentStatus = agentStatus; }
    public int getNetworkCount() { return networkCount; }
    public void setNetworkCount(int networkCount) { this.networkCount = networkCount; }
    public int getActiveNetworkCount() { return activeNetworkCount; }
    public void setActiveNetworkCount(int activeNetworkCount) { this.activeNetworkCount = activeNetworkCount; }
}
