package com.ruoyi.common.tinc.runtime;

public final class TincCommandResult {
    private final int exitCode;
    private final String output;
    private final boolean timedOut;

    public TincCommandResult(int exitCode, String output, boolean timedOut) {
        this.exitCode = exitCode;
        this.output = output == null ? "" : output;
        this.timedOut = timedOut;
    }

    public int getExitCode() { return exitCode; }
    public String getOutput() { return output; }
    public boolean isTimedOut() { return timedOut; }
    public boolean isSuccess() { return !timedOut && exitCode == 0; }
}
