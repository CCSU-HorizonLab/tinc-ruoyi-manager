package com.ruoyi.tinc.security;

import com.ruoyi.common.core.domain.BaseEntity;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.tinc.security.mapper.TincResourceGrantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Management authorization only. Node-token and Agent-token flows do not use this boundary. */
@Service("tincAccess")
public class TincResourceAccess {
    private final TincResourceGrantMapper grants;
    public TincResourceAccess(TincResourceGrantMapper grants) { this.grants = grants; }

    @javax.annotation.PostConstruct
    public void verifySchema() {
        try { grants.schemaReady(); }
        catch (RuntimeException failure) {
            throw new IllegalStateException("资源授权表不可用，请先审查并执行 20261011_tinc_resource_grants.sql；禁止跳过授权检查", failure);
        }
    }

    public boolean isSuperAdmin() { return SecurityUtils.isAdmin(SecurityUtils.getUserId()); }
    public void requireAdmin() { if (!isSuperAdmin()) throw new ResourceAccessDeniedException(); }

    private Long currentUser() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null || (!SecurityUtils.isAdmin(userId) && grants.activeUser(userId) != 1)) {
            throw new ResourceAccessDeniedException();
        }
        return userId;
    }

    /** Always overwrite client-supplied scope BEFORE startPage / database access. */
    public void scope(BaseEntity query) {
        Long userId = currentUser();
        query.getParams().put("tincScopeUserId", SecurityUtils.isAdmin(userId) ? null : userId);
        query.getParams().put("tincIncludeParentServers", false);
    }

    /** For a minimal selector only; must never expose parent-server settings or secrets. */
    public void scopeServerOptions(BaseEntity query) {
        scope(query);
        query.getParams().put("tincIncludeParentServers", true);
    }

    public void requireServer(Long id) {
        Long userId = currentUser();
        if (id == null || id <= 0 || (!SecurityUtils.isAdmin(userId) && grants.canServer(userId, id) == 0)) {
            throw new ResourceAccessDeniedException();
        }
    }
    /** Allocation ranges are only available when the account can manage the whole server. */
    public boolean canAllocateOnServer(Long id) {
        Long userId = currentUser();
        return id != null && id > 0 && (SecurityUtils.isAdmin(userId) || grants.canServer(userId, id) > 0);
    }
    public void requireNetwork(Long id) {
        Long userId = currentUser();
        if (id == null || id <= 0 || (!SecurityUtils.isAdmin(userId) && grants.canNetwork(userId, id) == 0)) {
            throw new ResourceAccessDeniedException();
        }
    }
    public void requireNode(Long id) {
        Long userId = currentUser();
        if (id == null || id <= 0 || (!SecurityUtils.isAdmin(userId) && grants.canNode(userId, id) == 0)) {
            throw new ResourceAccessDeniedException();
        }
    }
    public void requireServers(Long[] ids) { checkBatch(ids); for (Long id : ids) requireServer(id); }
    public void requireNetworks(Long[] ids) { checkBatch(ids); for (Long id : ids) requireNetwork(id); }
    public void requireNodes(Long[] ids) { checkBatch(ids); for (Long id : ids) requireNode(id); }
    private void checkBatch(Long[] ids) {
        if (ids == null || ids.length == 0 || ids.length > 500) throw new ResourceAccessDeniedException();
    }

    public ResourceGrants read(Long userId) {
        requireAdmin();
        if (userId == null || userId <= 0) throw new IllegalArgumentException("无效用户 ID");
        ResourceGrants result = new ResourceGrants();
        result.setServerIds(grants.serverIds(userId));
        result.setNetworkIds(grants.networkIds(userId));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void replace(Long userId, ResourceGrants request) {
        requireAdmin();
        if (userId == null || userId <= 0 || SecurityUtils.isAdmin(userId)) {
            throw new IllegalArgumentException("不能修改超级管理员或无效用户的资源范围");
        }
        Set<Long> servers = ids(request == null ? null : request.getServerIds());
        Set<Long> networks = ids(request.getNetworkIds());
        // Lock the target user: concurrent complete replacements cannot merge grants accidentally.
        if (grants.lockUser(userId) == null) throw new IllegalArgumentException("目标用户不存在");
        for (Long id : servers) if (grants.serverExists(id) != 1) throw new IllegalArgumentException("服务器 ID 无效");
        for (Long id : networks) if (grants.networkExists(id) != 1) throw new IllegalArgumentException("网络 ID 或服务器关联无效");
        grants.deleteForUser(userId);
        Long actor = SecurityUtils.getUserId();
        for (Long id : servers) if (grants.insert(userId, "SERVER", id, actor) != 1) throw new ServiceException("授权保存失败");
        for (Long id : networks) if (grants.insert(userId, "NETWORK", id, actor) != 1) throw new ServiceException("授权保存失败");
    }
    private Set<Long> ids(List<Long> values) {
        if (values == null || values.size() > 500) throw new IllegalArgumentException("请完整提交资源授权，单类资源最多 500 项");
        Set<Long> ids = new LinkedHashSet<>(values);
        for (Long id : ids) if (id == null || id <= 0) throw new IllegalArgumentException("资源 ID 必须为正整数");
        return ids;
    }
}
