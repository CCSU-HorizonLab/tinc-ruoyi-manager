package com.ruoyi.common.config;

import com.ruoyi.common.transport.LocalTincConfigTransport;
import com.ruoyi.common.transport.TincConfigTransport;
import com.ruoyi.common.utils.TincConfigUtils;
import com.ruoyi.common.tinc.runtime.TincRuntimeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tinc 配置传输层自动配置
 * <p>
 * 根据 application.yml 中的 tinc.transport.mode 配置，
 * 自动选择本地写入或 SSH 远程推送模式，并注入到 TincConfigUtils 中。
 * </p>
 *
 * @author Sun
 * @date 2026-07-03
 */
@Configuration
public class TincTransportConfig {

    private static final Logger log = LoggerFactory.getLogger(TincTransportConfig.class);

    @Value("${tinc.transport.mode:local}")
    private String transportMode;

    @Value("${tinc.transport.ssh.default-port:22}")
    private int defaultSshPort;

    @Value("${tinc.transport.ssh.default-user:root}")
    private String defaultSshUser;

    @Value("${tinc.transport.ssh.default-key-path:}")
    private String defaultSshKeyPath;

    @Value("${tinc.transport.ssh.default-password:}")
    private String defaultSshPassword;

    @Value("${tinc.transport.ssh.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${tinc.runtime.file-owner:root}")
    private String fileOwner;

    @Value("${tinc.runtime.file-group:root}")
    private String fileGroup;

    @Value("${tinc.runtime.config-root:/etc/tinc}")
    private String runtimeConfigRoot;

    @Bean
    public TincConfigTransport tincConfigTransport(TincRuntimeManager runtimeManager) {
        TincConfigTransport transport;

        if ("ssh".equalsIgnoreCase(transportMode)) {
            throw new IllegalStateException(
                    "SSH Tinc 传输模式已禁用：安全远程 RuntimeManager 尚未实现，请使用 local 模式");
        } else if ("local".equalsIgnoreCase(transportMode)) {
            log.info("========== Tinc 配置传输模式: 本地文件写入（单机部署）==========");
            transport = new LocalTincConfigTransport(runtimeConfigRoot, fileOwner, fileGroup, runtimeManager);
        } else {
            throw new IllegalStateException("不支持的 Tinc 配置传输模式，仅允许 local 或 ssh");
        }

        // 注入到 TincConfigUtils 静态方法中
        TincConfigUtils.setBasePath(runtimeConfigRoot);
        TincConfigUtils.setTransport(transport);
        TincConfigUtils.setRuntimeManager(runtimeManager);
        return transport;
    }
}
