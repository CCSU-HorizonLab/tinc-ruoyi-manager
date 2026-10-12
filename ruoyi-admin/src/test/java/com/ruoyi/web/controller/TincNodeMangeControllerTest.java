package com.ruoyi.web.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.service.ITincNodeMangeService;
import com.ruoyi.tinc.security.TincResourceAccess;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.domain.entity.SysUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class TincNodeMangeControllerTest {
    @Mock
    private ITincNodeMangeService nodeService;

    private TincNodeMangeController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new TincNodeMangeController();
        ReflectionTestUtils.setField(controller, "tincNodeMangeService", nodeService);
        ReflectionTestUtils.setField(controller, "resourceAccess", mock(TincResourceAccess.class));
        SysUser user = new SysUser(); user.setUserId(1L); user.setUserName("admin");
        LoginUser principal = new LoginUser(user, java.util.Collections.emptySet());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
    }

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void addNodeReturnsGeneratedAccessCodeInAjaxResult() {
        TincNodeMange input = new TincNodeMange();
        input.setNetworkId(10L);
        input.setNodeName("node_test");
        input.setNetworkIp("10.0.10.2");

        when(nodeService.insertTincNodeMange(any(TincNodeMange.class))).thenAnswer(invocation -> {
            TincNodeMange node = invocation.getArgument(0);
            node.setId(99L);
            node.setAccessCode("TL-7K9M-2Q4F");
            return 1;
        });

        AjaxResult result = controller.add(input);

        assertEquals(200, result.get("code"));
        assertEquals("TL-7K9M-2Q4F", result.get("accessCode"));
        assertEquals(99L, result.get("nodeId"));
        verify(nodeService).insertTincNodeMange(any(TincNodeMange.class));
    }

    @Test
    void rotateAccessCodeReturnsNewCode() {
        when(nodeService.rotateAccessCode(99L)).thenReturn("TL-NEWW-CODE");

        AjaxResult result = controller.rotateAccessCode(99L);

        assertEquals(200, result.get("code"));
        assertEquals("TL-NEWW-CODE", result.get("accessCode"));
        verify(nodeService).rotateAccessCode(99L);
    }
}
