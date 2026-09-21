package com.ruoyi.common.tinc.access;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AccessRuntimeValidator {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern RESERVED = Pattern.compile("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])");
    private static final Pattern ADDRESS = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?");
    private static final Pattern RSA = Pattern.compile("(?s)^-----BEGIN RSA PUBLIC KEY-----\\s*([A-Za-z0-9+/=\\s]+)\\s*-----END RSA PUBLIC KEY-----$");

    private AccessRuntimeValidator() { }

    public static String name(String value, String field) {
        if (value == null || !NAME.matcher(value).matches() || RESERVED.matcher(value).matches()) {
            throw new IllegalArgumentException(field + "只能包含英文字母、数字和下划线，长度为 1-64");
        }
        return value;
    }

    public static String peerName(String value) {
        String name = name(value, "节点名称");
        if ("server_master".equals(name)) throw new IllegalArgumentException("节点名称为系统保留名称");
        return name;
    }

    public static String segment(String value) {
        if (value == null) throw new IllegalArgumentException("网段不能为空");
        String[] parts = value.split("\\.", -1);
        if (parts.length != 3) throw new IllegalArgumentException("网段必须为 IPv4 前三段");
        for (String part : parts) octet(part, "网段");
        return value;
    }

    public static int port(int value) {
        if (value < 1 || value > 65535) throw new IllegalArgumentException("端口必须在 1-65535 之间");
        return value;
    }

    public static String address(String value) {
        if (value == null || !ADDRESS.matcher(value).matches() || value.contains("..")) {
            throw new IllegalArgumentException("公网地址格式无效");
        }
        return value;
    }

    public static String subnet32(String value) {
        if (value == null || !value.endsWith("/32")) throw new IllegalArgumentException("Subnet 必须为 IPv4 /32");
        String ip = value.substring(0, value.length() - 3);
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("Subnet 必须为 IPv4 /32");
        for (String part : parts) octet(part, "Subnet");
        return value;
    }

    public static String rsaPublicKey(String value) {
        if (value == null || value.length() > 16384) throw new IllegalArgumentException("RSA 公钥无效");
        Matcher matcher = RSA.matcher(value.trim().replace("\r\n", "\n"));
        if (!matcher.matches()) throw new IllegalArgumentException("只允许 PKCS#1 RSA 公钥");
        try {
            String base64 = matcher.group(1).replaceAll("\\s", "");
            RSAPublicKey key = RSAPublicKey.getInstance(ASN1Primitive.fromByteArray(Base64.getDecoder().decode(base64)));
            int bits = key.getModulus().bitLength();
            if (bits < 2048 || bits > 8192) throw new IllegalArgumentException("RSA 公钥位数必须为 2048-8192");
            return value.trim().replace("\r\n", "\n") + "\n";
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("PKCS#1 RSA 公钥解析失败");
        }
    }

    private static void octet(String value, String field) {
        try {
            if (value.isEmpty() || (value.length() > 1 && value.startsWith("0"))) throw new NumberFormatException();
            int number = Integer.parseInt(value);
            if (number < 0 || number > 255) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + "不是有效 IPv4 地址");
        }
    }
}
