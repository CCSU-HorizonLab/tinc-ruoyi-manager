package com.ruoyi.tinc.security;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.tinc.security.mapper.TincResourceGrantMapper;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TincResourceAccessTest {
    private final TincResourceGrantMapper mapper = mock(TincResourceGrantMapper.class);
    private final TincResourceAccess access = new TincResourceAccess(mapper);

    @BeforeEach void user() {
        authenticate(20L);
        when(mapper.activeUser(20L)).thenReturn(1);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void authenticate(Long id) {
        SysUser user = new SysUser(); user.setUserId(id); user.setUserName("user" + id);
        LoginUser principal = new LoginUser(user, Collections.singleton("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
    }

    @Test void functionalWildcardDoesNotGrantResourcesOrAllowDelegation() {
        assertThrows(ResourceAccessDeniedException.class, () -> access.requireServer(3L));
        assertThrows(ResourceAccessDeniedException.class, () -> access.requireNetwork(8L));
        assertThrows(ResourceAccessDeniedException.class, () -> access.requireNode(5L));
        assertThrows(ResourceAccessDeniedException.class, access::requireAdmin);
        assertThrows(ResourceAccessDeniedException.class, () -> access.replace(21L, grants(Collections.emptyList(), Collections.emptyList())));
        verify(mapper, never()).deleteForUser(anyLong());
    }

    @Test void queryScopeCannotBeForgedByTheCaller() {
        TincNodeMange query = new TincNodeMange();
        query.getParams().put("tincScopeUserId", 1L);
        query.getParams().put("tincIncludeParentServers", true);
        access.scope(query);
        assertEquals(20L, query.getParams().get("tincScopeUserId"));
        assertEquals(false, query.getParams().get("tincIncludeParentServers"));
        authenticate(1L);
        access.scope(query);
        assertNull(query.getParams().get("tincScopeUserId"));
    }

    @Test void revocationIsReadFromDatabaseOnEveryRequest() {
        when(mapper.canNetwork(20L, 8L)).thenReturn(1, 0);
        access.requireNetwork(8L);
        assertThrows(ResourceAccessDeniedException.class, () -> access.requireNetwork(8L));
        when(mapper.activeUser(20L)).thenReturn(0);
        assertThrows(ResourceAccessDeniedException.class, () -> access.scope(new TincNodeMange()));
    }

    @Test void missingAuthenticationAndMissingSchemaNeverFailOpen() {
        SecurityContextHolder.clearContext();
        assertThrows(RuntimeException.class, () -> access.requireNetwork(8L));
        when(mapper.schemaReady()).thenThrow(new IllegalStateException("missing table"));
        assertThrows(IllegalStateException.class, access::verifySchema);
    }

    @Test void adminReplacementValidatesEverythingBeforeDeletingAndDeduplicatesIds() {
        authenticate(1L);
        when(mapper.lockUser(20L)).thenReturn(20L);
        when(mapper.serverExists(3L)).thenReturn(1);
        ResourceGrants request = grants(Arrays.asList(3L, 3L), Collections.singletonList(8L));
        assertThrows(IllegalArgumentException.class, () -> access.replace(20L, request));
        verify(mapper, never()).deleteForUser(anyLong());
        when(mapper.networkExists(8L)).thenReturn(1);
        when(mapper.insert(anyLong(), anyString(), anyLong(), anyLong())).thenReturn(1);
        access.replace(20L, request);
        verify(mapper).deleteForUser(20L);
        verify(mapper).insert(20L, "SERVER", 3L, 1L);
        verify(mapper).insert(20L, "NETWORK", 8L, 1L);
    }

    @Test void nullPayloadIsNotARevokeAllAndEmptyListsAreExplicitRevocation() {
        authenticate(1L);
        when(mapper.lockUser(20L)).thenReturn(20L);
        assertThrows(IllegalArgumentException.class, () -> access.replace(20L, null));
        assertThrows(IllegalArgumentException.class, () -> access.replace(20L, new ResourceGrants()));
        verify(mapper, never()).deleteForUser(anyLong());
        access.replace(20L, grants(Collections.emptyList(), Collections.emptyList()));
        verify(mapper).deleteForUser(20L);
        verify(mapper, never()).insert(anyLong(), anyString(), anyLong(), anyLong());
    }

    private ResourceGrants grants(java.util.List<Long> servers, java.util.List<Long> networks) {
        ResourceGrants result = new ResourceGrants(); result.setServerIds(servers); result.setNetworkIds(networks); return result;
    }
}
