package com.ruoyi.common.tinc.runtime;

public class TincNetworkStatus {
    private String netName;
    private boolean systemdEnabled;
    private boolean systemdActive;
    private boolean mainPidPresent;
    private String interfaceName;
    private boolean interfacePresent;
    private String expectedVpnIp;
    private String actualVpnIp;
    private int port;
    private boolean tcpListening;
    private boolean udpListening;
    private boolean configValid;
    private boolean privateKeyPresent;
    private String lastReloadResult;
    private String readiness = "NOT_READY";
    private String failureCode;
    private String failureMessage;

    public String getNetName() { return netName; }
    public void setNetName(String netName) { this.netName = netName; }
    public boolean isSystemdEnabled() { return systemdEnabled; }
    public void setSystemdEnabled(boolean systemdEnabled) { this.systemdEnabled = systemdEnabled; }
    public boolean isSystemdActive() { return systemdActive; }
    public void setSystemdActive(boolean systemdActive) { this.systemdActive = systemdActive; }
    public boolean isMainPidPresent() { return mainPidPresent; }
    public void setMainPidPresent(boolean mainPidPresent) { this.mainPidPresent = mainPidPresent; }
    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
    public boolean isInterfacePresent() { return interfacePresent; }
    public void setInterfacePresent(boolean interfacePresent) { this.interfacePresent = interfacePresent; }
    public String getExpectedVpnIp() { return expectedVpnIp; }
    public void setExpectedVpnIp(String expectedVpnIp) { this.expectedVpnIp = expectedVpnIp; }
    public String getActualVpnIp() { return actualVpnIp; }
    public void setActualVpnIp(String actualVpnIp) { this.actualVpnIp = actualVpnIp; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public boolean isTcpListening() { return tcpListening; }
    public void setTcpListening(boolean tcpListening) { this.tcpListening = tcpListening; }
    public boolean isUdpListening() { return udpListening; }
    public void setUdpListening(boolean udpListening) { this.udpListening = udpListening; }
    public boolean isConfigValid() { return configValid; }
    public void setConfigValid(boolean configValid) { this.configValid = configValid; }
    public boolean isPrivateKeyPresent() { return privateKeyPresent; }
    public void setPrivateKeyPresent(boolean privateKeyPresent) { this.privateKeyPresent = privateKeyPresent; }
    public String getLastReloadResult() { return lastReloadResult; }
    public void setLastReloadResult(String lastReloadResult) { this.lastReloadResult = lastReloadResult; }
    public String getReadiness() { return readiness; }
    public void setReadiness(String readiness) { this.readiness = readiness; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }

    public boolean isReady() { return "READY".equals(readiness); }

    public void fail(TincRuntimeErrorCode code, String message) {
        this.readiness = "NOT_READY";
        this.failureCode = code.name();
        this.failureMessage = message;
    }
}
