package com.ruoyi.tinc.runtime;

public class AccessAgentException extends RuntimeException {
    private final String code;

    public AccessAgentException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AccessAgentException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() { return code; }
}
