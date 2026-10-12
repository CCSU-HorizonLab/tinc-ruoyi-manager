package com.ruoyi.tinc.security;

import org.springframework.security.access.AccessDeniedException;

public class ResourceAccessDeniedException extends AccessDeniedException {
    public ResourceAccessDeniedException() {
        super("无权访问该资源，或资源不存在，请联系管理员分配服务器/网络权限");
    }
}
