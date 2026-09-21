package com.ruoyi.tinc.validation;

import java.util.Locale;
import java.util.regex.Pattern;

/** 第一阶段数据模型的服务端权威校验。历史记录不被自动改名。 */
public final class TincModelValidator {
    private static final Pattern NEW_RUNTIME_NAME = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern WINDOWS_RESERVED = Pattern.compile("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])");

    private TincModelValidator() {
    }

    public static String requireNewRuntimeName(String value, String field) {
        if (value == null || !NEW_RUNTIME_NAME.matcher(value).matches()
                || WINDOWS_RESERVED.matcher(value).matches()) {
            throw new IllegalArgumentException(field + "只能包含英文字母、数字和下划线，长度为 1-64");
        }
        return value;
    }

    public static String requireNodeName(String value) {
        String name = requireNewRuntimeName(value, "节点名称");
        if ("server_master".equals(name.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("节点名称 server_master 为系统保留名称");
        }
        return name;
    }

    public static String requireSegment(String value) {
        if (value == null) {
            throw new IllegalArgumentException("网段不能为空");
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("网段必须为 IPv4 的前三段，例如 10.0.11");
        }
        for (String part : parts) {
            requireIpv4Octet(part, "网段");
        }
        return value;
    }

    public static String requireNodeIp(String value, String segment) {
        if (value == null || value.contains("/")) {
            throw new IllegalArgumentException("节点地址必须为不带掩码的 IPv4 地址");
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("节点地址不是有效 IPv4 地址");
        }
        for (String part : parts) {
            requireIpv4Octet(part, "节点地址");
        }
        String expectedPrefix = requireSegment(segment) + ".";
        if (!value.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("节点地址不属于所选网络网段");
        }
        int host = Integer.parseInt(parts[3]);
        if (host == 0 || host == 1 || host == 255) {
            throw new IllegalArgumentException("节点地址不能使用网络地址、网关地址或广播地址");
        }
        return value;
    }

    public static String requirePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
            return String.valueOf(port);
        } catch (Exception e) {
            throw new IllegalArgumentException("Tinc 端口必须在 1-65535 之间");
        }
    }

    private static void requireIpv4Octet(String value, String field) {
        try {
            if (value.isEmpty() || (value.length() > 1 && value.startsWith("0"))) {
                throw new NumberFormatException();
            }
            int octet = Integer.parseInt(value);
            if (octet < 0 || octet > 255) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + "不是有效 IPv4 地址");
        }
    }
}
