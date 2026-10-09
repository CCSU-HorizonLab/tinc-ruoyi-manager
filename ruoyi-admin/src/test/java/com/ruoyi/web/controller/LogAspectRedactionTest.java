package com.ruoyi.web.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.framework.aspectj.LogAspect;
import com.ruoyi.system.domain.SysOperLog;
import java.util.LinkedHashMap;
import java.util.Map;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class LogAspectRedactionTest {
    @Log(isSaveRequestData = false)
    private void operation() {
    }

    @Test
    void auditResponseExcludesCredentialsAndTokens() throws Exception {
        Log annotation = getClass().getDeclaredMethod("operation").getAnnotation(Log.class);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("nodeId", 5);
        response.put("accessCode", "hidden-access-code");
        response.put("password", "hidden-password");
        response.put("token", "hidden-token");
        SysOperLog audit = new SysOperLog();

        new LogAspect().getControllerMethodDescription(mock(JoinPoint.class), annotation, audit, response);

        assertTrue(audit.getJsonResult().contains("nodeId"));
        assertFalse(audit.getJsonResult().contains("hidden-access-code"));
        assertFalse(audit.getJsonResult().contains("hidden-password"));
        assertFalse(audit.getJsonResult().contains("hidden-token"));
    }
}
