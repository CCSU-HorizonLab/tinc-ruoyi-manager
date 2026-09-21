package com.ruoyi.tinc.runtime;

import com.ruoyi.tinc_server.service.IMangeServerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "tinclink.agent.health.enabled", havingValue = "true", matchIfMissing = true)
public class AccessAgentHealthScheduler {
    private final IMangeServerService serverService;

    public AccessAgentHealthScheduler(IMangeServerService serverService) {
        this.serverService = serverService;
    }

    @Scheduled(fixedDelayString = "${tinclink.agent.health.interval-ms:20000}")
    public void poll() { serverService.pollAgentHealth(); }
}
