package com.ruoyi.web.controller.platform;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.platform.NetworkEngineRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only capability catalogue. It does not claim that an Access Server can run an engine. */
@RestController
@RequestMapping("/platform/network-engines")
@PreAuthorize("isAuthenticated()")
public class NetworkEngineController {
    private final NetworkEngineRegistry registry;

    public NetworkEngineController(NetworkEngineRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public AjaxResult list() {
        return AjaxResult.success(registry.descriptors());
    }
}
