package com.ruoyi.web.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.web.controller.monitor.NetworkMonitorController;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {TincNodeMangeController.class, TincNetworkMangeController.class,
        MangeServerController.class, TincResourceGrantController.class, NetworkMonitorController.class})
@Order(-1)
public class TincResourceAccessAdvice {
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public AjaxResult denied(AccessDeniedException ignored) {
        AjaxResult result = AjaxResult.error(403, "无权访问该资源，或资源不存在，请联系管理员分配权限");
        result.put("errorCode", "TINC_RESOURCE_FORBIDDEN");
        return result;
    }
}
