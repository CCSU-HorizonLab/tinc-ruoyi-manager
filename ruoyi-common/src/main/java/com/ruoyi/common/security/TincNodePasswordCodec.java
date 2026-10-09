package com.ruoyi.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Node login credentials are independent of Tinc peer key material. */
public final class TincNodePasswordCodec {
    // Prehash avoids BCrypt's 72-byte truncation for UTF-8 passwords.
    private static final String PREFIX = "{bcrypt-sha256}";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private TincNodePasswordCodec() {
    }

    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty() || rawPassword.length() > 255
                || rawPassword.indexOf('\n') >= 0 || rawPassword.indexOf('\r') >= 0
                || rawPassword.contains("-----BEGIN") || rawPassword.contains("-----END")) {
            throw new IllegalArgumentException("节点密码无效；公钥请通过独立的密钥上传接口提交");
        }
        return PREFIX + ENCODER.encode(prehash(rawPassword));
    }

    public static boolean isEncoded(String storedPassword) {
        return storedPassword != null && storedPassword.startsWith(PREFIX);
    }

    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || rawPassword.isEmpty()) {
            return false;
        }
        if (isEncoded(storedPassword)) {
            return ENCODER.matches(prehash(rawPassword), storedPassword.substring(PREFIX.length()));
        }
        // Legacy rows remain usable until their first successful login upgrades the stored value.
        return MessageDigest.isEqual(rawPassword.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8));
    }

    /** Compare-and-swap fingerprint; never pass a legacy plaintext value into SQL logging. */
    public static String legacyFingerprint(String storedPassword) {
        if (storedPassword == null || isEncoded(storedPassword)) {
            throw new IllegalArgumentException("仅旧版凭据可生成迁移指纹");
        }
        byte[] digest = sha256(storedPassword);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            hex.append(Character.forDigit(value & 0x0f, 16));
        }
        return hex.toString();
    }

    private static String prehash(String rawPassword) {
        return Base64.getEncoder().encodeToString(sha256(rawPassword));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
