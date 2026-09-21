package com.ruoyi.accessagent;

import com.ruoyi.common.tinc.runtime.TincRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class AgentExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(AgentExceptionHandler.class);

    @ExceptionHandler(TincRuntimeException.class)
    public ResponseEntity<Map<String, Object>> runtime(TincRuntimeException e) {
        HttpStatus status = e.getCode().name().endsWith("CONFLICT") ? HttpStatus.CONFLICT
                : HttpStatus.SERVICE_UNAVAILABLE;
        return response(status, e.getCode().name(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalid(IllegalArgumentException e) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        log.error("Access Agent 请求失败，错误类型={}", e.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "RUNTIME_FAILURE", "Access Agent 运行失败");
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(body);
    }
}
