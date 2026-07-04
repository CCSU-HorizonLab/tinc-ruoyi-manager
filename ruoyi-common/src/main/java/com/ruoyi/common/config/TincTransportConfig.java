package com.ruoyi.common.config;

import com.ruoyi.common.transport.LocalTincConfigTransport;
import com.ruoyi.common.transport.SshTincConfigTransport;
import com.ruoyi.common.transport.TincConfigTransport;
import com.ruoyi.common.utils.TincConfigUtils;
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

    @Bean
    public TincConfigTransport tincConfigTransport() {
        TincConfigTransport transport;

        if ("ssh".equalsIgnoreCase(transportMode)) {
            log.info("========== Tinc 配置传输模式: SSH 远程推送 ==========");
            SshTincConfigTransport sshTransport = new SshTincConfigTransport();
            sshTransport.setDefaultSshPort(defaultSshPort);
            sshTransport.setDefaultSshUser(defaultSshUser);
            sshTransport.setDefaultSshKeyPath(defaultSshKeyPath);
            sshTransport.setDefaultSshPassword(defaultSshPassword);
            sshTransport.setConnectTimeout(connectTimeout);
            transport = sshTransport;
        } else {
            log.info("========== Tinc 配置传输模式: 本地文件写入（单机部署）==========");
            String basePath = System.getProperty("os.name").toLowerCase().startsWith("win")
                    ? "D:/tinc"
                    : "/etc/tinc";
            transport = new LocalTincConfigTransport(basePath);
        }

        // 注入到 TincConfigUtils 静态方法中
        TincConfigUtils.setTransport(transport);
        return transport;
    }
}
