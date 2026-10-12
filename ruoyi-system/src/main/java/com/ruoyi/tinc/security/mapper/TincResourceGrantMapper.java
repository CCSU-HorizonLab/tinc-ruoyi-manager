package com.ruoyi.tinc.security.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface TincResourceGrantMapper {
    int schemaReady();
    int activeUser(@Param("userId") Long userId);
    Long lockUser(@Param("userId") Long userId);
    int canServer(@Param("userId") Long userId, @Param("id") Long id);
    int canNetwork(@Param("userId") Long userId, @Param("id") Long id);
    int canNode(@Param("userId") Long userId, @Param("id") Long id);
    List<Long> serverIds(@Param("userId") Long userId);
    List<Long> networkIds(@Param("userId") Long userId);
    List<Map<String, Object>> serverOptions();
    List<Map<String, Object>> networkOptions();
    int serverExists(@Param("id") Long id);
    int networkExists(@Param("id") Long id);
    int deleteForUser(@Param("userId") Long userId);
    int insert(@Param("userId") Long userId, @Param("type") String type,
               @Param("id") Long id, @Param("grantedBy") Long grantedBy);
}
