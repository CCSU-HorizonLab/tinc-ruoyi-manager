package com.ruoyi.accessagent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;

@ConfigurationProperties(prefix = "agent")
public class AgentProperties implements InitializingBean {
    private String id;
    private String secret;
    private String version = "2.0.0";

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    @Override
    public void afterPropertiesSet() {
        if (id == null || !id.matches("[A-Za-z0-9_.-]{1,64}")) {
            throw new IllegalStateException("agent.id 必须配置且只能包含字母、数字、点、下划线或连字符");
        }
        if (secret == null || secret.length() < 32 || secret.length() > 512) {
            throw new IllegalStateException("agent.secret 必须通过配置或环境变量注入，长度为 32-512");
        }
    }
}
