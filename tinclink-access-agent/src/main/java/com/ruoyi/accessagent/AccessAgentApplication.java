package com.ruoyi.accessagent;

import com.ruoyi.common.config.TincTransportConfig;
import com.ruoyi.common.tinc.access.LocalTincAccessRuntime;
import com.ruoyi.common.tinc.runtime.LocalTincRuntimeManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        MybatisAutoConfiguration.class
})
@EnableConfigurationProperties(AgentProperties.class)
@Import({LocalTincRuntimeManager.class, TincTransportConfig.class, LocalTincAccessRuntime.class})
public class AccessAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccessAgentApplication.class, args);
    }
}
