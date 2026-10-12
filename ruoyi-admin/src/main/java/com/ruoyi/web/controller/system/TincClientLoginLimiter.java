package com.ruoyi.web.controller.system;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Distributed, atomic budget for the anonymous Tinc client login endpoint. */
@Service
public class TincClientLoginLimiter {
    private static final String PREFIX = "tinc:client:login-limit:";
    private static final int SOURCE_ATTEMPTS = 120;
    private static final int SOURCE_WINDOW_SECONDS = 60;
    private static final int CREDENTIAL_ATTEMPTS = 8;
    private static final int CREDENTIAL_WINDOW_SECONDS = 300;
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>();

    static {
        CONSUME.setResultType(Long.class);
        CONSUME.setScriptText(
                "local source = redis.call('INCR', KEYS[1])\n"
                + "if source == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2])) end\n"
                + "if source > tonumber(ARGV[1]) then return math.max(redis.call('TTL', KEYS[1]), 1) end\n"
                + "local target = redis.call('INCR', KEYS[2])\n"
                + "if target == 1 then redis.call('EXPIRE', KEYS[2], tonumber(ARGV[4])) end\n"
                + "if target > tonumber(ARGV[3]) then return math.max(redis.call('TTL', KEYS[2]), 1) end\n"
                + "return 0");
    }

    private final RedisTemplate<Object, Object> redis;

    public TincClientLoginLimiter(RedisTemplate<Object, Object> redis) {
        this.redis = redis;
    }

    /** Returns seconds to retry; zero means this request may attempt authentication. */
    public long consume(String target) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes == null ? null : attributes.getRequest();
        // Forwarding headers can be supplied by the caller; only the actual socket peer is trusted.
        String source = request == null ? "unknown" : request.getRemoteAddr();
        try {
            Long seconds = redis.execute(CONSUME, Arrays.asList(sourceKey(source), targetKey(target)),
                    SOURCE_ATTEMPTS, SOURCE_WINDOW_SECONDS, CREDENTIAL_ATTEMPTS, CREDENTIAL_WINDOW_SECONDS);
            if (seconds == null || seconds < 0) throw new IllegalStateException("Redis limiter returned no result");
            return seconds;
        } catch (RuntimeException failure) {
            throw new LimiterUnavailableException(failure);
        }
    }

    /** A successful authentication releases only its credential budget, not the source budget. */
    public void clearCredential(String target) {
        try {
            redis.delete(targetKey(target));
        } catch (RuntimeException failure) {
            throw new LimiterUnavailableException(failure);
        }
    }

    private String sourceKey(String source) { return PREFIX + "source:" + sha256(source); }
    private String targetKey(String target) { return PREFIX + "credential:" + sha256(target); }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(Character.forDigit((b >> 4) & 0xf, 16))
                    .append(Character.forDigit(b & 0xf, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public static class LimiterUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        LimiterUnavailableException(Throwable cause) { super("Client login limiter unavailable", cause); }
    }
}
