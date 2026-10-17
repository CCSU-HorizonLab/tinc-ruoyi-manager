package com.ruoyi.web.controller.system;

import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.config.RedisConfig;
import com.ruoyi.tinc.session.TincClientSessionStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in production RedisTemplate/Lettuce path against an isolated Linux loopback Redis. */
class TincClientSessionStoreLettuceIntegrationTest {
    private LettuceConnectionFactory connection;
    private RedisTemplate<Object, Object> redis;
    private TincClientSessionStore sessions;
    private boolean connectionDestroyed;

    @BeforeEach
    void connect() {
        assumeTrue(Boolean.getBoolean("tinclink.test.redis.lettuce"), "isolated Linux Redis not requested");
        connection = new LettuceConnectionFactory("127.0.0.1", 16379);
        connection.afterPropertiesSet();
        redis = new RedisConfig().redisTemplate(connection);
        RedisCache cache = new RedisCache();
        cache.redisTemplate = redis;
        sessions = new TincClientSessionStore();
        ReflectionTestUtils.setField(sessions, "redisCache", cache);
        assertNotNull(redis.getConnectionFactory().getConnection().ping());
    }

    @AfterEach
    void close() {
        if (connection != null && !connectionDestroyed) connection.destroy();
    }

    @Test
    void realTemplateIssueRenewExpiryRevokeAndTombstone() throws Exception {
        long nodeId = System.currentTimeMillis() + 11001L;
        String token = sessions.issue(nodeId);
        assertTrue(sessions.isValid(nodeId, token));
        assertTrue(sessions.renew(nodeId, token));
        Long indexTtl = redis.getExpire("tinc:client:node-tokens:" + nodeId, TimeUnit.SECONDS);
        assertNotNull(indexTtl);
        assertTrue(indexTtl > TincClientSessionStore.ROUTE_LEASE_SECONDS && indexTtl <= 1800);
        sessions.revokeNode(nodeId, true);
        assertTrue(sessions.isRevoked(token));
        assertFalse(sessions.isValid(nodeId, token));
        assertFalse(sessions.renew(nodeId, token));
        assertThrows(IllegalStateException.class, () -> sessions.issue(nodeId));

        long expiringId = nodeId + 1L;
        String expiring = sessions.issue(expiringId);
        String hash = sha256(expiring);
        assertTrue(redis.expire("tinc:client:token:" + hash, 1, TimeUnit.SECONDS));
        Thread.sleep(1300);
        assertFalse(sessions.renew(expiringId, expiring));
    }

    @Test
    void realTemplateConcurrentRevokeFencesRenewAndRotation() throws Exception {
        long nodeId = System.currentTimeMillis() + 12001L;
        String old = sessions.issue(nodeId);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> renewals = new ArrayList<>();
        try {
            for (int i = 0; i < 12; i++) {
                renewals.add(pool.submit(() -> {
                    start.await();
                    sessions.renew(nodeId, old);
                    return null;
                }));
            }
            start.countDown();
            sessions.revokeNode(nodeId, false);
            for (Future<?> future : renewals) future.get(15, TimeUnit.SECONDS);
            assertTrue(sessions.isRevoked(old));
            assertFalse(sessions.renew(nodeId, old));
            assertThrows(IllegalStateException.class, () -> sessions.issue(nodeId));
            assertTrue(redis.delete("tinc:client:node-issue-blocked:" + nodeId));
            String fresh = sessions.issue(nodeId);
            assertTrue(sessions.renew(nodeId, fresh));
            assertFalse(sessions.renew(nodeId, old));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void prePhase1aRedisSessionRemainsValidUntilExpiryOrRevocation() throws Exception {
        long nodeId = System.currentTimeMillis() + 12501L;
        String token = "isolated-legacy-token-" + nodeId;
        String hash = sha256(token);
        redis.opsForValue().set("tinc:client:token:" + hash, String.valueOf(nodeId), 1800, TimeUnit.SECONDS);
        redis.opsForSet().add("tinc:client:node-tokens:" + nodeId, hash);
        assertTrue(redis.expire("tinc:client:node-tokens:" + nodeId, 1800, TimeUnit.SECONDS));
        assertTrue(sessions.isValid(nodeId, token));
        assertTrue(sessions.renew(nodeId, token));
        sessions.revokeNode(nodeId, true);
        assertTrue(sessions.isRevoked(token));
        assertFalse(sessions.renew(nodeId, token));
    }

    @Test
    void connectionFailureDoesNotReturnAuthorizedSession() {
        long nodeId = System.currentTimeMillis() + 13001L;
        String token = sessions.issue(nodeId);
        connection.destroy();
        connectionDestroyed = true;
        assertThrows(Exception.class, () -> sessions.renew(nodeId, token));
    }

    private static String sha256(String token) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b & 255));
        return hex.toString();
    }
}
