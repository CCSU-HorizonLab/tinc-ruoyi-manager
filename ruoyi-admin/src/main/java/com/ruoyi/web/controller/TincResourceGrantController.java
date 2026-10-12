package com.ruoyi.web.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.tinc.security.*;
import com.ruoyi.tinc.security.mapper.TincResourceGrantMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/tinc/resource-grants")
@PreAuthorize("@tincAccess.isSuperAdmin()")
public class TincResourceGrantController {
    private final TincResourceAccess access;
    private final TincResourceGrantMapper grants;
    public TincResourceGrantController(TincResourceAccess access, TincResourceGrantMapper grants) {
        this.access = access; this.grants = grants;
    }
    @GetMapping("/options")
    public AjaxResult options() {
        access.requireAdmin();
        Map<String, Object> options = new HashMap<>();
        options.put("servers", grants.serverOptions());
        options.put("networks", grants.networkOptions());
        return AjaxResult.success(options);
    }
    @GetMapping("/{userId}")
    public AjaxResult get(@PathVariable Long userId) { return AjaxResult.success(access.read(userId)); }

    @PutMapping("/{userId}")
    @Log(title = "Tinc资源授权", businessType = BusinessType.GRANT)
    public AjaxResult replace(@PathVariable Long userId, @Valid @RequestBody ResourceGrants request) {
        access.replace(userId, request);
        return AjaxResult.success("资源授权已更新，后续管理请求立即按新范围检查");
    }
}
