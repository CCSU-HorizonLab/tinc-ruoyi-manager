package com.ruoyi.tinc.security;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.ruoyi.tinc.security.mapper.TincResourceGrantMapper;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.mapper.TincNetworkMangeMapper;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.mapper.TincNodeMangeMapper;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc_server.mapper.MangeServerMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Execute the production MyBatis XML and migration against isolated H2/MySQL-mode data. */
class TincResourceScopeSqlTest {
    private SqlSession session;
    private TincResourceGrantMapper grants;
    private TincNetworkMangeMapper networks;
    private TincNodeMangeMapper nodes;
    private MangeServerMapper servers;

    @BeforeEach void setup() throws Exception {
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.getTypeAliasRegistry().registerAlias("MangeServer", MangeServer.class);
        config.getTypeAliasRegistry().registerAlias("TincNetworkMange", TincNetworkMange.class);
        config.getTypeAliasRegistry().registerAlias("TincNodeMange", TincNodeMange.class);
        PageInterceptor paging = new PageInterceptor();
        Properties properties = new Properties(); properties.setProperty("helperDialect", "mysql");
        paging.setProperties(properties); config.addInterceptor(paging);
        for (String resource : new String[] { "mapper/tinc_security/TincResourceGrantMapper.xml",
                "mapper/tinc_server/MangeServerMapper.xml", "mapper/tinc_network/TincNetworkMangeMapper.xml",
                "mapper/tinc_node/TincNodeMangeMapper.xml" }) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input);
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        session = new SqlSessionFactoryBuilder().build(config).openSession();
        Connection connection = session.getConnection();
        try (Statement sql = connection.createStatement()) {
            sql.execute("create table sys_user(user_id bigint primary key, status varchar(8), del_flag varchar(8))");
            sql.execute("create table tinc_server(Id bigint primary key, server_name varchar(64), server_ip varchar(64),"
                    + "start_segment varchar(64), end_segment varchar(64), start_port int, end_port int, remark varchar(255),"
                    + "number int, status int, ssh_port int, ssh_user varchar(64), ssh_key_path varchar(255), ssh_password varchar(255),"
                    + "runtime_type varchar(32), agent_port int, agent_status varchar(32), agent_version varchar(32), agent_last_seen timestamp,"
                    + "agent_secret varchar(255), agent_id varchar(64), agent_cpu_usage double, agent_memory_usage double, agent_network_count int,"
                    + "create_by varchar(64), create_time timestamp, update_by varchar(64), update_time timestamp)");
            sql.execute("create table tinc_network(id bigint primary key, server_id bigint, create_by varchar(64), server_name varchar(64),"
                    + "network_name varchar(64), create_time timestamp, port varchar(16), segment varchar(32), nodes int,"
                    + "network_status varchar(32), remark varchar(255), update_by varchar(64), update_time timestamp)");
            sql.execute("create table tinc_node(id bigint primary key, network_id bigint, access_code varchar(64), user_name varchar(64),"
                    + "device_id varchar(64), server_name varchar(64), network_name varchar(64), password varchar(255), node_name varchar(64),"
                    + "network_ip varchar(32), remark varchar(255), create_time timestamp, node_status varchar(32), status varchar(32),"
                    + "create_by varchar(64), update_by varchar(64), update_time timestamp)");
            String migration = new String(Files.readAllBytes(Paths.get("../sql/20261011_tinc_resource_grants.sql")), StandardCharsets.UTF_8);
            int begin = migration.indexOf("CREATE TABLE");
            sql.execute(migration.substring(begin, migration.indexOf(';', begin)));
            sql.execute("insert into sys_user values(20,'0','0'),(21,'0','0')");
            sql.execute("insert into tinc_server(Id,server_name) values(3,'gateway'),(4,'gateway_other')");
            sql.execute("insert into tinc_network(id,server_id,network_name) values(8,3,'same_name'),(9,3,'sibling'),(10,4,'same_name')");
            sql.execute("insert into tinc_node(id,network_id,node_name,network_name) values(5,8,'client','same_name'),"
                    + "(6,9,'client','sibling'),(7,10,'client','same_name'),(8,null,'client','same_name')");
            sql.execute("insert into tinc_resource_grant(user_id,resource_type,resource_id,granted_by) values"
                    + "(20,'NETWORK',8,1),(21,'SERVER',3,1)");
        }
        grants = session.getMapper(TincResourceGrantMapper.class);
        networks = session.getMapper(TincNetworkMangeMapper.class);
        nodes = session.getMapper(TincNodeMangeMapper.class);
        servers = session.getMapper(MangeServerMapper.class);
    }
    @AfterEach void close() { PageHelper.clearPage(); if (session != null) session.close(); }

    @Test void networkOnlyGrantCannotEscapeViaSiblingSameNameOrLegacyOrphan() {
        assertEquals(1, grants.canNetwork(20L, 8L));
        assertEquals(0, grants.canNetwork(20L, 9L));
        assertEquals(0, grants.canNetwork(20L, 10L));
        assertEquals(0, grants.canServer(20L, 3L));
        assertEquals(1, grants.canNode(20L, 5L));
        assertEquals(0, grants.canNode(20L, 6L));
        assertEquals(0, grants.canNode(20L, 7L));
        assertEquals(0, grants.canNode(20L, 8L));
        TincNodeMange filter = new TincNodeMange(); filter.getParams().put("tincScopeUserId", 20L);
        assertEquals(1, nodes.selectTincNodeMangeList(filter).size());
        filter.setNetworkId(10L);
        assertTrue(nodes.selectTincNodeMangeList(filter).isEmpty());
    }

    @Test void scopeIsAppliedBeforePaginationAndNameSearch() {
        TincNetworkMange filter = new TincNetworkMange(); filter.getParams().put("tincScopeUserId", 21L);
        Page<TincNetworkMange> page = PageHelper.startPage(1, 1);
        assertEquals(1, networks.selectTincNetworkMangeList(filter).size());
        assertEquals(2L, page.getTotal()); // not all three networks
        filter.setNetworkName("same_name");
        assertEquals(8L, networks.selectTincNetworkMangeList(filter).get(0).getId());
        filter.setServerId(4L);
        assertTrue(networks.selectTincNetworkMangeList(filter).isEmpty());
    }

    @Test void parentServerVisibilityIsExplicitAndDoesNotGrantServerAuthority() {
        MangeServer filter = new MangeServer(); filter.getParams().put("tincScopeUserId", 20L);
        assertTrue(servers.selectMangeServerList(filter).isEmpty());
        filter.getParams().put("tincIncludeParentServers", true);
        assertEquals(3L, servers.selectMangeServerList(filter).get(0).getId());
        assertEquals(0, grants.canServer(20L, 3L));
    }

    @Test void revokeAndDeletedResourcesTakeEffectWithoutNameFallback() throws Exception {
        grants.deleteForUser(20L);
        assertEquals(0, grants.canNetwork(20L, 8L));
        assertEquals(1, grants.canNode(21L, 5L));
        try (Statement sql = session.getConnection().createStatement()) {
            sql.execute("delete from tinc_network where id = 8");
        }
        session.clearCache();
        assertEquals(0, grants.canNode(21L, 5L));
        assertEquals(1, grants.canNode(21L, 6L));
    }

    @Test void serverGrantAutomaticallyIncludesNewChildrenAndUniqueConstraintPreventsDuplicates() throws Exception {
        try (Statement sql = session.getConnection().createStatement()) {
            sql.execute("insert into tinc_network(id,server_id,network_name) values(11,3,'new_child')");
            assertThrows(java.sql.SQLException.class, () -> sql.execute(
                    "insert into tinc_resource_grant(user_id,resource_type,resource_id,granted_by) values(21,'SERVER',3,1)"));
        }
        assertEquals(1, grants.canNetwork(21L, 11L));
        assertEquals(0, grants.canNetwork(20L, 11L));
    }
}
