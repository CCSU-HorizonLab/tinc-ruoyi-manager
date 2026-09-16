package com.ruoyi.common.tinc.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class TincRuntimeSupport {
    private TincRuntimeSupport() { }

    public static String sha256Normalized(byte[] bytes) {
        String normalized = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
