package com.ruoyi.tinc.security;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.tinc.security.mapper.TincResourceGrantMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real Spring transactions and row locking, using an isolated in-memory database. */
class TincResourceGrantTransactionTest {
    private AnnotationConfigApplicationContext context;
    private TincResourceAccess access;
    private TincResourceGrantMapper mapper;
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        access = context.getBean(TincResourceAccess.class);
        mapper = context.getBean(TincResourceGrantMapper.class);
        admin();
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }
    private static void admin() {
        SysUser user = new SysUser(); user.setUserId(1L);
        LoginUser principal = new LoginUser(user, Collections.singleton("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));
    }
    private static ResourceGrants request(List<Long> servers, List<Long> networks) {
        ResourceGrants request = new ResourceGrants(); request.setServerIds(servers); request.setNetworkIds(networks); return request;
    }

    @Test void insertFailureRollsBackDeletionOfOriginalGrants() {
        // The test-only constraint injects a failure after validation and DELETE.
        assertThrows(RuntimeException.class, () -> access.replace(20L,
                request(Collections.emptyList(), Collections.singletonList(9L))));
        assertEquals(Collections.singletonList(3L), mapper.serverIds(20L));
        assertTrue(mapper.networkIds(20L).isEmpty());
    }

    @Test void successfulReplacementAndRevokeAllCommitAsOneTransaction() {
        access.replace(20L, request(Collections.emptyList(), Collections.singletonList(8L)));
        assertTrue(mapper.serverIds(20L).isEmpty());
        assertEquals(Collections.singletonList(8L), mapper.networkIds(20L));
        access.replace(20L, request(Collections.emptyList(), Collections.emptyList()));
        assertTrue(mapper.networkIds(20L).isEmpty());
    }

    @Test void concurrentReplacementsCannotAccidentallyMergeTheirGrants() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            Future<?> a = pool.submit(() -> replaceAfterBarrier(start, request(Collections.singletonList(3L), Collections.emptyList())));
            Future<?> b = pool.submit(() -> replaceAfterBarrier(start, request(Collections.emptyList(), Collections.singletonList(8L))));
            a.get(15, TimeUnit.SECONDS); b.get(15, TimeUnit.SECONDS);
            assertEquals(1, mapper.serverIds(20L).size() + mapper.networkIds(20L).size());
        } finally { pool.shutdownNow(); }
    }
    private void replaceAfterBarrier(CyclicBarrier start, ResourceGrants request) {
        admin();
        try { start.await(5, TimeUnit.SECONDS); access.replace(20L, request); }
        catch (Exception error) { throw new RuntimeException(error); }
        finally { SecurityContextHolder.clearContext(); }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() throws Exception {
            DataSource source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:"
                    + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            try (Connection connection = source.getConnection(); Statement sql = connection.createStatement()) {
                sql.execute("create table sys_user(user_id bigint primary key, status varchar(8), del_flag varchar(8))");
                sql.execute("create table tinc_server(Id bigint primary key, server_name varchar(64))");
                sql.execute("create table tinc_network(id bigint primary key, server_id bigint, network_name varchar(64))");
                sql.execute("create table tinc_resource_grant(user_id bigint, resource_type varchar(16), resource_id bigint,"
                        + "granted_by bigint, granted_at timestamp default current_timestamp,"
                        + "primary key(user_id,resource_type,resource_id), check(resource_id != 9))");
                sql.execute("insert into sys_user values(20,'0','0')");
                sql.execute("insert into tinc_server values(3,'server')");
                sql.execute("insert into tinc_network values(8,3,'network'),(9,3,'failure_test')");
                sql.execute("insert into tinc_resource_grant(user_id,resource_type,resource_id,granted_by)"
                        + " values(20,'SERVER',3,1)");
            }
            return source;
        }
        @Bean SqlSessionFactory factory(DataSource source) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/tinc_security/TincResourceGrantMapper.xml"));
            return factory.getObject();
        }
        @Bean TincResourceGrantMapper mapper(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(TincResourceGrantMapper.class);
        }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean TincResourceAccess access(TincResourceGrantMapper mapper) { return new TincResourceAccess(mapper); }
    }
}
