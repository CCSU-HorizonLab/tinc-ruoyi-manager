package com.ruoyi.common.tinc.access;

public class AccessNetworkSpec {
    private String runtimeName;
    private String publicAddress;
    private String segment;
    private int port;

    public String getRuntimeName() { return runtimeName; }
    public void setRuntimeName(String runtimeName) { this.runtimeName = runtimeName; }
    public String getPublicAddress() { return publicAddress; }
    public void setPublicAddress(String publicAddress) { this.publicAddress = publicAddress; }
    public String getSegment() { return segment; }
    public void setSegment(String segment) { this.segment = segment; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
}
