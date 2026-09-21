package com.ruoyi.common.tinc.access;

public class AccessAgentHealth {
    private String status;
    private String agentId;
    private String hostname;
    private String agentVersion;
    private String os;
    private long uptime;
    private double cpuUsage;
    private double memoryUsage;
    private int networkCount;
    private long timestamp;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    public String getAgentVersion() { return agentVersion; }
    public void setAgentVersion(String agentVersion) { this.agentVersion = agentVersion; }
    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }
    public long getUptime() { return uptime; }
    public void setUptime(long uptime) { this.uptime = uptime; }
    public double getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }
    public double getMemoryUsage() { return memoryUsage; }
    public void setMemoryUsage(double memoryUsage) { this.memoryUsage = memoryUsage; }
    public int getNetworkCount() { return networkCount; }
    public void setNetworkCount(int networkCount) { this.networkCount = networkCount; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
