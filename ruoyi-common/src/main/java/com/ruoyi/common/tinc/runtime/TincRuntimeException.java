package com.ruoyi.common.tinc.runtime;

public class TincRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final TincRuntimeErrorCode code;

    public TincRuntimeException(TincRuntimeErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public TincRuntimeException(TincRuntimeErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public TincRuntimeErrorCode getCode() {
        return code;
    }
}
