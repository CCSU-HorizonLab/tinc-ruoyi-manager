package com.ruoyi.web.controller.system;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.tinc_network.domain.TincNetworkMange;
import com.ruoyi.tinc_network.service.ITincNetworkMangeService;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.TincConfigUtils;
import com.ruoyi.common.tinc.runtime.TincRuntimeException;
import com.ruoyi.common.tinc.runtime.TincRuntimeManager;
import com.ruoyi.tinc_server.domain.MangeServer;
import com.ruoyi.tinc_server.service.IMangeServerService;
import com.ruoyi.tinc_node.domain.TincNodeMange;
import com.ruoyi.tinc_node.service.ITincNodeMangeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.regex.Pattern;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;

/**
 * Tinc C++ Qt 客户端专用 RESTful API 接口
 * 
 * @author Antigravity
 * @date 2026-07-03
 */
@Anonymous
@RestController
@RequestMapping("/api/tinc/client")
public class TincClientApiController {

    private static final Logger log = LoggerFactory.getLogger(TincClientApiController.class);

    private static final String RSA_PUBLIC_KEY_BEGIN = "-----BEGIN RSA PUBLIC KEY-----";
    private static final String RSA_PUBLIC_KEY_END = "-----END RSA PUBLIC KEY-----";
    private static final Pattern PEM_BASE64_LINE = Pattern.compile("[A-Za-z0-9+/=]+");
    private static final Pattern HOST_ADDRESS = Pattern.compile("[A-Za-z0-9.-]{1,253}");
    private static final Pattern TINC_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern WINDOWS_RESERVED_NAME = Pattern.compile(
            "(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])");
    private static final String CLIENT_TOKEN_PREFIX = "tinc:client:token:";
    private static final int CLIENT_TOKEN_EXPIRATION_MINUTES = 30;
    private static final int MAX_KEY_UPLOAD_BODY_BYTES = 64 * 1024;
    private static final SecureRandom CLIENT_TOKEN_RANDOM = new SecureRandom();

    @Autowired
    private IMangeServerService mangeServerService;

    @Autowired
    private ITincNetworkMangeService networkMangeService;

    @Autowired
    private ITincNodeMangeService nodeMangeService;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private TincRuntimeManager tincRuntimeManager;

    /**
     * 客户端登录验证，获取内网分配的虚拟 IP 和网络名称
     */
    @PostMapping("/login")
    public JSONObject login(@RequestBody Map<String, String> params,
                            javax.servlet.http.HttpServletResponse response) {
        log.info("========== 客户端登录接口被调用 ==========");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        String username = params.get("sid");
        String password = params.get("password");

        log.info("客户端登录请求: sid={}, 密码长度={}", username, password != null ? password.length() : 0);

        JSONObject result = new JSONObject();

        try {
            if (!isValidTincIdentifier(username) || StringUtils.isEmpty(password)) {
                log.warn("用户名或密码为空，登录失败");
                result.put("status", 0);
                result.put("msg", "sid 或密码无效");
                return result;
            }

            TincNodeMange node = findPasswordBoundNode(username, password);
            if (node == null) {
                log.warn("客户端登录认证失败: sid={}", username);
                result.put("status", 0);
                result.put("msg", "sid 或密码无效");
                return result;
            }

            if (!isValidIpv4(node.getNetworkIp())) {
                log.warn("节点未配置有效的 IPv4 地址: sid={}", username);
                result.put("status", 0);
                result.put("msg", "节点网络配置不可用");
                return result;
            }

            TincNetworkMange network = findExactNetwork(node.getNetworkName());
            if (network == null || !isValidTincIdentifier(network.getNetworkName())) {
                log.warn("网络不存在: {}", node.getNetworkName());
                result.put("status", 0);
                result.put("msg", "节点网络配置不可用");
                return result;
            }

            String clientToken = createClientToken(node);
            result.put("sid", username);
            result.put("status", 1);
            result.put("token", clientToken);
            result.put("net_name", network.getNetworkName());
            result.put("msg", "登录成功");
            result.put("node_ip", node.getNetworkIp());

            log.info("登录成功，用户: {}, 网络: {}", username, network.getNetworkName());
        } catch (Exception e) {
            log.error("登录接口异常，错误类型={}", e.getClass().getSimpleName());
            result.put("status", 0);
            result.put("msg", "系统暂时不可用");
        }

        return result;
    }

    /**
     * 下载该节点的初始化 Tinc 配置文件压缩包 (ZIP)
     */
    @PostMapping("/config/download")
    public void downloadConfig(@RequestBody Map<String, String> params,
                               javax.servlet.http.HttpServletResponse response) throws IOException {
        log.info("========== 客户端下载配置接口被调用 ==========");

        String nodeName = params.get("sid");
        String token = params.get("token");

        log.info("客户端配置下载请求: sid={}", nodeName);

        try {
            if (!isValidTincIdentifier(nodeName)) {
                writeClientApiError(response, HttpStatus.BAD_REQUEST, "CLIENT_SID_INVALID", "节点名称无效");
                return;
            }
            List<TincNodeMange> exactNodes = findExactNodes(nodeName);
            if (exactNodes.isEmpty()) {
                log.error("节点不存在: {}", nodeName);
                writeClientApiError(response, HttpStatus.NOT_FOUND, "CLIENT_NODE_NOT_FOUND", "节点不存在");
                return;
            }

            TincNodeMange node;
            try {
                node = findTokenBoundNode(exactNodes, token);
                if (node == null) {
                    writeClientApiError(response, HttpStatus.UNAUTHORIZED, "CLIENT_TOKEN_INVALID", "客户端会话无效或已过期");
                    return;
                }
            } catch (Exception e) {
                log.error("客户端配置下载会话校验失败，错误类型={}", e.getClass().getSimpleName());
                writeClientApiError(response, HttpStatus.SERVICE_UNAVAILABLE, "CLIENT_SESSION_UNAVAILABLE", "客户端会话服务不可用");
                return;
            }

            TincNetworkMange network = findExactNetwork(node.getNetworkName());
            if (network == null || !isValidTincIdentifier(network.getNetworkName())) {
                log.error("网络不存在: {}", node.getNetworkName());
                writeClientApiError(response, HttpStatus.NOT_FOUND, "CLIENT_NETWORK_NOT_FOUND", "网络不存在或名称无效");
                return;
            }

            // A successful download means that the server data plane is already connectable.
            tincRuntimeManager.ensureNetworkReady(network.getNetworkName());

            String serverHostContent = TincConfigUtils.readHostFile(network.getNetworkName(), "server_master");
            if (StringUtils.isEmpty(serverHostContent)) {
                writeClientApiError(response, HttpStatus.SERVICE_UNAVAILABLE, "GATEWAY_HOST_UNAVAILABLE",
                        "网关主机配置缺失");
                return;
            }
            String validatedServerHostContent;
            try {
                validatedServerHostContent = validateServerMasterHost(serverHostContent);
            } catch (Exception e) {
                log.error("配置下载的网关主机配置校验失败，错误类型={}", e.getClass().getSimpleName());
                writeClientApiError(response, HttpStatus.SERVICE_UNAVAILABLE, "GATEWAY_HOST_UNAVAILABLE",
                        "网关主机配置不可用");
                return;
            }

            byte[] zipBody;
            try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                 ZipOutputStream zos = new ZipOutputStream(output)) {

                String tincConf = "Name = " + nodeName + "\n" +
                        "Mode = router\n" +
                        "AddressFamily = ipv4\n" +
                        "ConnectTo = server_master\n";
                addToZip(zos, network.getNetworkName() + "/tinc.conf", tincConf);
                log.info("已添加 {}/tinc.conf", network.getNetworkName());

                addToZip(zos, network.getNetworkName() + "/hosts/server_master",
                        validatedServerHostContent);
                log.info("已添加 {}/hosts/server_master", network.getNetworkName());

                zos.flush();
                zos.finish();
                zipBody = output.toByteArray();
                log.info("配置包生成完成");
            }

            // CharacterEncodingFilter 已预设 UTF-8；二进制响应发送前必须 reset，避免得到
            // application/zip;charset=UTF-8，保持客户端已验证的精确契约。
            response.reset();
            response.setHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"config.zip\"");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setContentLength(zipBody.length);
            response.getOutputStream().write(zipBody);
            response.flushBuffer();
        } catch (TincRuntimeException e) {
            log.warn("配置下载前网络未就绪: netCode={}", e.getCode());
            if (!response.isCommitted()) {
                writeClientApiError(response, HttpStatus.SERVICE_UNAVAILABLE, e.getCode().name(), e.getMessage());
            }
        } catch (Exception e) {
            log.error("生成配置包异常，错误类型={}", e.getClass().getSimpleName());
            if (!response.isCommitted()) {
                writeClientApiError(response, HttpStatus.INTERNAL_SERVER_ERROR, "CLIENT_CONFIG_GENERATION_FAILED",
                        "客户端配置包生成失败");
            }
        }
    }

    /**
     * 客户端生成公钥后上传至服务端，建立双向互信
     */
    @PostMapping("/key/upload")
    public ResponseEntity<?> uploadKey(
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestBody(required = false) String requestBody) {
        log.info("========== 客户端公钥上传接口被调用 ==========");

        MediaType requestMediaType;
        try {
            requestMediaType = MediaType.parseMediaType(contentType == null ? "" : contentType);
        } catch (Exception e) {
            return clientApiError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "CLIENT_CONTENT_TYPE_INVALID",
                    "Content-Type 必须为 application/json");
        }
        if (!MediaType.APPLICATION_JSON.isCompatibleWith(requestMediaType)) {
            return clientApiError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "CLIENT_CONTENT_TYPE_INVALID",
                    "Content-Type 必须为 application/json");
        }
        if (requestBody == null || requestBody.getBytes(StandardCharsets.UTF_8).length > MAX_KEY_UPLOAD_BODY_BYTES) {
            return clientApiError(HttpStatus.PAYLOAD_TOO_LARGE, "CLIENT_REQUEST_TOO_LARGE",
                    "公钥上传请求体超过限制");
        }

        JSONObject request;
        try {
            request = JSONObject.parseObject(requestBody);
            if (request == null) {
                throw new IllegalArgumentException("请求体为空");
            }
        } catch (Exception e) {
            return clientApiError(HttpStatus.BAD_REQUEST, "CLIENT_REQUEST_INVALID", "请求体必须为 JSON 对象");
        }

        String nodeName = requestString(request, "sid");
        String token = requestString(request, "token");
        String publicKeyContent = requestString(request, "content");
        String action = requestString(request, "action");

        log.info("客户端公钥上传请求: sid={}, action={}, 公钥内容长度={}", nodeName, action,
                publicKeyContent != null ? publicKeyContent.length() : 0);

        try {
            if (!"exchangeFile".equals(action)) {
                return clientApiError(HttpStatus.BAD_REQUEST, "CLIENT_ACTION_INVALID", "不支持的公钥上传操作");
            }
            if (!isValidTincIdentifier(nodeName)) {
                return clientApiError(HttpStatus.BAD_REQUEST, "CLIENT_SID_INVALID", "节点名称无效");
            }
            List<TincNodeMange> exactNodes = findExactNodes(nodeName);
            if (exactNodes.isEmpty()) {
                log.error("节点不存在: {}", nodeName);
                return clientApiError(HttpStatus.NOT_FOUND, "CLIENT_NODE_NOT_FOUND", "节点不存在");
            }

            TincNodeMange node;
            try {
                node = findTokenBoundNode(exactNodes, token);
                if (node == null) {
                    return clientApiError(HttpStatus.UNAUTHORIZED, "CLIENT_TOKEN_INVALID", "客户端会话无效或已过期");
                }
            } catch (Exception e) {
                log.error("客户端公钥上传会话校验失败，错误类型={}", e.getClass().getSimpleName());
                return clientApiError(HttpStatus.SERVICE_UNAVAILABLE, "CLIENT_SESSION_UNAVAILABLE", "客户端会话服务不可用");
            }

            if (!isValidIpv4(node.getNetworkIp())) {
                return clientApiError(HttpStatus.BAD_REQUEST, "CLIENT_PUBLIC_KEY_INVALID", "节点 IPv4 配置无效");
            }

            TincNetworkMange network = findExactNetwork(node.getNetworkName());
            if (network == null || !isValidTincIdentifier(network.getNetworkName())) {
                log.error("网络不存在: {}", node.getNetworkName());
                return clientApiError(HttpStatus.NOT_FOUND, "CLIENT_NETWORK_NOT_FOUND", "网络不存在或名称无效");
            }

            String mainHostContent = TincConfigUtils.readHostFile(network.getNetworkName(), "server_master");
            if (StringUtils.isEmpty(mainHostContent)) {
                return clientApiError(HttpStatus.SERVICE_UNAVAILABLE, "GATEWAY_HOST_UNAVAILABLE", "网关主机配置缺失");
            }
            String validatedMainHost;
            try {
                validatedMainHost = validateServerMasterHost(mainHostContent);
            } catch (Exception e) {
                log.error("网关主机配置校验失败，错误类型={}", e.getClass().getSimpleName());
                return clientApiError(HttpStatus.SERVICE_UNAVAILABLE, "GATEWAY_HOST_UNAVAILABLE", "网关主机配置不可用");
            }

            MangeServer server = findExactServer(network.getServerName());
            if (server == null || StringUtils.isEmpty(server.getServerIp())) {
                log.error("网络未关联可用网关服务器");
                return clientApiError(HttpStatus.SERVICE_UNAVAILABLE, "GATEWAY_UNAVAILABLE", "网关服务器不可用");
            }
            String serverIp = server.getServerIp();

            String cleanPubKey;
            try {
                cleanPubKey = validateClientPublicKey(publicKeyContent, node.getNetworkIp());
            } catch (Exception e) {
                return clientApiError(HttpStatus.BAD_REQUEST, "CLIENT_PUBLIC_KEY_INVALID", "公钥格式或节点子网无效");
            }
            // 工具层按节点串行执行“回执校验 → 远程原子写入 → HUP → 本地提交”，
            // 防止并发重试重复推送，也不把没有远程成功回执的历史本地文件当作幂等成功。
            boolean changed = TincConfigUtils.applyClientHostFileIfChanged(serverIp,
                    network.getNetworkName(), nodeName, node.getNetworkIp() + "/32", cleanPubKey);

            if (changed) {
                node.setStatus("已配置");
                try {
                    int updated = nodeMangeService.updateTincNodeMange(node);
                    if (updated != 1) {
                        log.warn("节点 [{}] 公钥已应用，但业务状态更新行数={}", nodeName, updated);
                    }
                } catch (Exception e) {
                    // 远程公钥与本地回执均已提交，业务状态字段失败不能反向伪装成网关失败。
                    log.error("节点 [{}] 公钥已应用，但业务状态更新失败，错误类型={}",
                            nodeName, e.getClass().getSimpleName());
                }
                log.info("节点 [{}] 公钥已推送至对应网关的 hosts 目录", nodeName);
            } else {
                log.info("节点 [{}] 上传的公钥未变化，不重复推送或重载网关", nodeName);
            }

            log.info("服务器主机配置已返回，公钥变更={}", changed);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(validatedMainHost);
        } catch (TincRuntimeException e) {
            log.warn("公钥上传后网络未就绪: netCode={}", e.getCode());
            return clientApiError(HttpStatus.SERVICE_UNAVAILABLE, e.getCode().name(), e.getMessage());
        } catch (Exception e) {
            log.error("公钥上传接口异常，错误类型={}", e.getClass().getSimpleName());
            return clientApiError(HttpStatus.BAD_GATEWAY, "GATEWAY_KEY_APPLY_FAILED", "网关公钥应用失败");
        }
    }

    /**
     * 客户端上报自身配置启动结果或在线状态
     */
    @PostMapping("/status/update")
    public JSONObject editAddInfo(@RequestBody Map<String, String> params,
                                  javax.servlet.http.HttpServletResponse response) {
        log.info("========== 客户端状态上报接口被调用 ==========");

        String nodeName = params.get("sid");
        String token = params.get("token");
        String type = params.get("type");
        String result = params.get("result");
        String details = params.get("details");

        log.info("状态上报: sid={}, type={}, result={}, details长度={}", nodeName, type, result,
                details == null ? 0 : details.length());

        try {
            if (!isValidTincIdentifier(nodeName)
                    || !("add".equals(type) || "edit".equals(type))
                    || !("success".equals(result) || "fail".equals(result))
                    || (details != null && details.length() > 2048)) {
                return clientStateError(response, HttpStatus.BAD_REQUEST,
                        "CLIENT_STATUS_INVALID", "状态上报参数无效");
            }

            TincNodeMange node = findTokenBoundNode(findExactNodes(nodeName), token);
            if (node == null) {
                return clientStateError(response, HttpStatus.UNAUTHORIZED,
                        "CLIENT_TOKEN_INVALID", "客户端会话无效或已过期");
            }

            // 只更新状态列，禁止把查询得到的 password 等敏感字段重新提交给 Mapper。
            TincNodeMange statusUpdate = new TincNodeMange();
            statusUpdate.setId(node.getId());
            if ("success".equals(result)) {
                statusUpdate.setStatus("配置成功");
                statusUpdate.setNodeStatus("在线");
            } else {
                statusUpdate.setStatus("配置失败");
                statusUpdate.setNodeStatus("离线");
            }

            nodeMangeService.updateTincNodeMange(statusUpdate);
            log.info("节点状态已更新: {}, 状态: {}", node.getNodeName(), statusUpdate.getStatus());

            JSONObject resp = new JSONObject();
            resp.put("status", "success");
            return resp;
        } catch (Exception e) {
            log.error("客户端状态上报接口异常，错误类型={}", e.getClass().getSimpleName());
            return clientStateError(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "CLIENT_STATUS_UPDATE_FAILED", "状态上报服务暂时不可用");
        }
    }

    /**
     * 客户端心跳接口，维持节点在线状态
     */
    @PostMapping("/keepalive")
    public JSONObject keepAlive(@RequestBody Map<String, String> params,
                                javax.servlet.http.HttpServletResponse response) {
        log.info("========== 客户端心跳接口被调用 ==========");

        String nodeName = params.get("sid");
        String token = params.get("token");
        JSONObject result = new JSONObject();

        try {
            if (!isValidTincIdentifier(nodeName)) {
                return clientStateError(response, HttpStatus.BAD_REQUEST,
                        "CLIENT_SID_INVALID", "节点名称无效");
            }

            TincNodeMange node = findTokenBoundNode(findExactNodes(nodeName), token);
            if (node == null) {
                return clientStateError(response, HttpStatus.UNAUTHORIZED,
                        "CLIENT_TOKEN_INVALID", "客户端会话无效或已过期");
            }

            TincNodeMange heartbeatUpdate = new TincNodeMange();
            heartbeatUpdate.setId(node.getId());
            heartbeatUpdate.setNodeStatus("在线");
            nodeMangeService.updateTincNodeMange(heartbeatUpdate);

            result.put("status", "success");
            result.put("msg", "心跳成功");
            log.info("节点 [{}] 在线状态已刷新", nodeName);
        } catch (Exception e) {
            log.error("心跳接口异常，错误类型={}", e.getClass().getSimpleName());
            return clientStateError(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "CLIENT_KEEPALIVE_FAILED", "心跳服务暂时不可用");
        }

        return result;
    }

    private void addToZip(ZipOutputStream zos, String fileName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    /**
     * 客户端仅接收网关连接所需的 host 指令和 PKCS#1 RSA 公钥，不能把网关目录中的
     * 任意 Tinc 扩展指令或脚本透传给客户端。
     */
    private String validateServerMasterHost(String content) throws IOException {
        boolean inPublicKey = false;
        boolean hasPublicKey = false;
        boolean hasPublicKeyEnd = false;
        boolean hasAddress = false;
        boolean hasPort = false;
        boolean hasSubnet = false;
        StringBuilder publicKeyBase64 = new StringBuilder();

        for (String line : content.replace("\r\n", "\n").split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            if (hasPublicKeyEnd) {
                throw new IOException("网关 hosts/server_master 的 RSA 公钥后包含额外内容");
            }
            if (RSA_PUBLIC_KEY_BEGIN.equals(line)) {
                if (inPublicKey || hasPublicKey) {
                    throw new IOException("网关 hosts/server_master 包含重复或嵌套的 RSA 公钥块");
                }
                inPublicKey = true;
                hasPublicKey = true;
                continue;
            }
            if (RSA_PUBLIC_KEY_END.equals(line)) {
                if (!inPublicKey) {
                    throw new IOException("网关 hosts/server_master 的 RSA 公钥结束标记无效");
                }
                inPublicKey = false;
                hasPublicKeyEnd = true;
                continue;
            }
            if (inPublicKey) {
                if (!PEM_BASE64_LINE.matcher(line).matches()) {
                    throw new IOException("网关 hosts/server_master 包含无效的 RSA 公钥内容");
                }
                publicKeyBase64.append(line);
                continue;
            }
            if (line.startsWith("Address = ")) {
                String address = line.substring("Address = ".length());
                if (hasAddress || !isValidHostAddress(address)) {
                    throw new IOException("网关 hosts/server_master 的 Address 无效或重复");
                }
                hasAddress = true;
            } else if (line.startsWith("Port = ")) {
                if (hasPort || !isValidPort(line.substring("Port = ".length()))) {
                    throw new IOException("网关 hosts/server_master 的 Port 无效或重复");
                }
                hasPort = true;
            } else if (line.startsWith("Subnet = ")) {
                String subnet = line.substring("Subnet = ".length());
                if (hasSubnet || !subnet.endsWith("/32")
                        || !isValidIpv4(subnet.substring(0, subnet.length() - 3))) {
                    throw new IOException("网关 hosts/server_master 的 Subnet 无效或重复");
                }
                hasSubnet = true;
            } else {
                throw new IOException("网关 hosts/server_master 包含不允许的指令");
            }
        }
        if (!hasAddress || !hasSubnet || !hasPublicKey || publicKeyBase64.length() == 0
                || !hasPublicKeyEnd || inPublicKey) {
            throw new IOException("网关 hosts/server_master 缺少必要的 Address、Subnet 或 PKCS#1 RSA 公钥");
        }
        try {
            RSAPublicKey publicKey = RSAPublicKey.getInstance(
                    ASN1Primitive.fromByteArray(Base64.getDecoder().decode(publicKeyBase64.toString())));
            if (!isValidRsaPublicKey(publicKey)) {
                throw new IOException("网关 hosts/server_master 的 RSA 公钥位数无效");
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("网关 hosts/server_master 不是有效的 PKCS#1 RSA 公钥", e);
        }
        return content.replace("\r\n", "\n");
    }

    private boolean isValidHostAddress(String address) {
        if (!HOST_ADDRESS.matcher(address).matches() || address.startsWith(".") || address.endsWith(".")
                || address.contains("..")) {
            return false;
        }
        for (String label : address.split("\\.")) {
            if (label.length() > 63 || !Character.isLetterOrDigit(label.charAt(0))
                    || !Character.isLetterOrDigit(label.charAt(label.length() - 1))) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidPort(String port) {
        try {
            int value = Integer.parseInt(port);
            return value >= 1 && value <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidIpv4(String address) {
        if (address == null) {
            return false;
        }
        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            try {
                if (part.isEmpty() || (part.length() > 1 && part.startsWith("0"))
                        || Integer.parseInt(part) > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidTincIdentifier(String value) {
        return value != null && TINC_IDENTIFIER.matcher(value).matches()
                && !WINDOWS_RESERVED_NAME.matcher(value).matches();
    }

    private List<TincNodeMange> findExactNodes(String nodeName) {
        TincNodeMange query = new TincNodeMange();
        query.setNodeName(nodeName);
        List<TincNodeMange> candidates = nodeMangeService.selectTincNodeMangeList(query);
        List<TincNodeMange> matches = new java.util.ArrayList<>();
        if (candidates != null) {
            for (TincNodeMange candidate : candidates) {
                if (nodeName.equals(candidate.getNodeName())) {
                    matches.add(candidate);
                }
            }
        }
        return matches;
    }

    private TincNodeMange findPasswordBoundNode(String nodeName, String password) {
        TincNodeMange match = null;
        for (TincNodeMange candidate : findExactNodes(nodeName)) {
            if (password.equals(candidate.getPassword())) {
                if (match != null) {
                    throw new IllegalStateException("sid 与密码匹配到多条节点记录");
                }
                match = candidate;
            }
        }
        return match;
    }

    private TincNodeMange findTokenBoundNode(List<TincNodeMange> candidates, String token) throws Exception {
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        Object authenticatedNodeId = redisCache.getCacheObject(CLIENT_TOKEN_PREFIX + sha256Hex(token));
        if (authenticatedNodeId == null) {
            return null;
        }
        String expectedId = String.valueOf(authenticatedNodeId);
        for (TincNodeMange candidate : candidates) {
            if (candidate.getId() != null && expectedId.equals(String.valueOf(candidate.getId()))) {
                return candidate;
            }
        }
        return null;
    }

    private TincNetworkMange findExactNetwork(String networkName) {
        if (!isValidTincIdentifier(networkName)) {
            return null;
        }
        TincNetworkMange query = new TincNetworkMange();
        query.setNetworkName(networkName);
        List<TincNetworkMange> candidates = networkMangeService.selectTincNetworkMangeList(query);
        TincNetworkMange match = null;
        if (candidates != null) {
            for (TincNetworkMange candidate : candidates) {
                if (networkName.equals(candidate.getNetworkName())) {
                    if (match != null) {
                        throw new IllegalStateException("网络名称存在重复记录");
                    }
                    match = candidate;
                }
            }
        }
        return match;
    }

    private MangeServer findExactServer(String serverName) {
        if (StringUtils.isEmpty(serverName)) {
            return null;
        }
        MangeServer query = new MangeServer();
        query.setServerName(serverName);
        List<MangeServer> candidates = mangeServerService.selectMangeServerList(query);
        MangeServer match = null;
        if (candidates != null) {
            for (MangeServer candidate : candidates) {
                if (serverName.equals(candidate.getServerName())) {
                    if (match != null) {
                        throw new IllegalStateException("网关服务器名称存在重复记录");
                    }
                    match = candidate;
                }
            }
        }
        return match;
    }

    private String createClientToken(TincNodeMange node) throws Exception {
        byte[] random = new byte[32];
        CLIENT_TOKEN_RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        redisCache.setCacheObject(CLIENT_TOKEN_PREFIX + sha256Hex(token), String.valueOf(node.getId()),
                CLIENT_TOKEN_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        return token;
    }

    private String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private String validateClientPublicKey(String content, String networkIp) throws Exception {
        if (StringUtils.isEmpty(content) || StringUtils.isEmpty(networkIp)) {
            throw new IOException("公钥或节点地址不能为空");
        }
        String normalized = content.replace("\r\n", "\n").trim();
        String expectedSubnet = "Subnet = " + networkIp + "/32";
        int begin = normalized.indexOf(RSA_PUBLIC_KEY_BEGIN);
        int end = normalized.indexOf(RSA_PUBLIC_KEY_END);
        if (begin <= 0 || end <= begin || !expectedSubnet.equals(normalized.substring(0, begin).trim())
                || !normalized.substring(end + RSA_PUBLIC_KEY_END.length()).trim().isEmpty()) {
            throw new IOException("公钥上传内容必须为当前节点的 Subnet 与 PKCS#1 RSA 公钥");
        }
        String pem = normalized.substring(begin, end + RSA_PUBLIC_KEY_END.length());
        String base64 = pem.substring(RSA_PUBLIC_KEY_BEGIN.length(), pem.length() - RSA_PUBLIC_KEY_END.length())
                .replaceAll("\\s", "");
        if (!PEM_BASE64_LINE.matcher(base64).matches()) {
            throw new IOException("RSA 公钥 Base64 内容无效");
        }
        RSAPublicKey publicKey = RSAPublicKey.getInstance(ASN1Primitive.fromByteArray(Base64.getDecoder().decode(base64)));
        if (!isValidRsaPublicKey(publicKey)) {
            throw new IOException("RSA 公钥位数必须在 2048 到 8192 之间");
        }
        return RSA_PUBLIC_KEY_BEGIN + "\n" + wrapBase64(base64) + "\n" + RSA_PUBLIC_KEY_END + "\n";
    }

    private boolean isValidRsaPublicKey(RSAPublicKey publicKey) {
        int bits = publicKey.getModulus().bitLength();
        return bits >= 2048 && bits <= 8192
                && publicKey.getModulus().signum() > 0
                && publicKey.getModulus().testBit(0)
                && publicKey.getPublicExponent().compareTo(java.math.BigInteger.valueOf(3)) >= 0
                && publicKey.getPublicExponent().testBit(0);
    }

    private String wrapBase64(String base64) {
        StringBuilder result = new StringBuilder(base64.length() + base64.length() / 64 + 1);
        for (int start = 0; start < base64.length(); start += 64) {
            result.append(base64, start, Math.min(start + 64, base64.length())).append('\n');
        }
        return result.toString().trim();
    }

    private String requestString(JSONObject request, String field) {
        Object value = request.get(field);
        return value instanceof String ? (String) value : null;
    }

    private ResponseEntity<byte[]> clientApiError(HttpStatus status, String code, String message) {
        JSONObject result = new JSONObject();
        result.put("code", code);
        result.put("msg", message);
        byte[] body = result.toJSONString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentLength(body.length)
                .body(body);
    }

    private JSONObject clientStateError(javax.servlet.http.HttpServletResponse response, HttpStatus status,
                                        String code, String message) {
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        JSONObject result = new JSONObject();
        result.put("status", "error");
        result.put("code", code);
        result.put("msg", message);
        return result;
    }

    private void writeClientApiError(javax.servlet.http.HttpServletResponse response, HttpStatus status,
                                     String code, String message) throws IOException {
        response.reset();
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        JSONObject result = new JSONObject();
        result.put("code", code);
        result.put("msg", message);
        byte[] body = result.toJSONString().getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.flushBuffer();
    }

}
