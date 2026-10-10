package com.ruoyi.tinc_node.mapper;

import com.ruoyi.tinc_node.domain.TincNodeMange;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class TincNodeStatusSqlTest {
    @Test
    void configuredStatusStatementCannotWriteCredentialOrIdentityColumns() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias("TincNodeMange", TincNodeMange.class);
        String resource = "mapper/tinc_node/TincNodeMangeMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        BoundSql sql = configuration.getMappedStatement(TincNodeMangeMapper.class.getName()
                + ".markClientConfigured").getBoundSql(Collections.singletonMap("id", 5L));
        assertEquals("update tinc_node set status = '已配置' where id = ?",
                sql.getSql().trim().replaceAll("\\s+", " "));
        assertEquals(1, sql.getParameterMappings().size());
        assertEquals("id", sql.getParameterMappings().get(0).getProperty());
    }
}
