package com.ruoyi.web.controller.system;

import com.ruoyi.tinc.session.TincClientSessionStore;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real Lua integration against a disposable loopback Redis; never connects to production. */
class TincClientSessionStoreRedisIntegrationTest {
    private static String redisCli;

    @BeforeAll
    static void requireIsolatedRedis() {
        redisCli = System.getProperty("tinclink.test.redis.cli");
        assumeTrue(redisCli != null && !redisCli.trim().isEmpty(), "isolated Redis CLI was not requested");
        assertEquals("PONG", command("PING"), "only a disposable Redis on 127.0.0.1:16379 is permitted");
    }

    @Test
    void tokenRenewalRealExpiryTombstoneAndPermanentRevocation() throws Exception {
        CliSessionStore store = new CliSessionStore(16379);
        long nodeId = System.currentTimeMillis() + 1001L;
        String token = store.issue(nodeId);
        String hash = sha256(token);
        assertTrue(store.isValid(nodeId, token));
        assertFalse(store.isValid(nodeId + 1, token));
        assertTrue(store.renew(nodeId, token));
        long tokenTtl = number(command("TTL", "tinc:client:token:" + hash));
        long indexTtl = number(command("TTL", "tinc:client:node-tokens:" + nodeId));
        assertTrue(tokenTtl > TincClientSessionStore.ROUTE_LEASE_SECONDS && tokenTtl <= 1800);
        assertTrue(indexTtl > TincClientSessionStore.ROUTE_LEASE_SECONDS && indexTtl <= 1800);
        assertEquals("\"" + hash + "\"", command("SMEMBERS", "tinc:client:node-tokens:" + nodeId));

        store.revokeNode(nodeId, true);
        assertTrue(store.isRevoked(token));
        long tombstoneTtl = number(command("TTL", "tinc:client:revoked-token:" + hash));
        assertTrue(tombstoneTtl > 0 && tombstoneTtl <= 86400);
        assertFalse(store.isValid(nodeId, token));
        assertFalse(store.renew(nodeId, token));
        assertThrows(IllegalStateException.class, () -> store.issue(nodeId));

        long expiredId = nodeId + 1L;
        String expired = store.issue(expiredId);
        assertEquals("1", command("EXPIRE", "tinc:client:token:" + sha256(expired), "1"));
        Thread.sleep(1300);
        assertEquals("0", command("EXISTS", "tinc:client:token:" + sha256(expired)));
        assertFalse(store.renew(expiredId, expired));
    }

    @Test
    void credentialRotationInvalidatesOldSessionWithoutResurrection() {
        CliSessionStore store = new CliSessionStore(16379);
        long nodeId = System.currentTimeMillis() + 2001L;
        String old = store.issue(nodeId);
        store.revokeNode(nodeId, false);
        assertTrue(store.isRevoked(old));
        assertFalse(store.renew(nodeId, old));
        assertThrows(IllegalStateException.class, () -> store.issue(nodeId));
        // Simulate completion of the short issuance fence without altering the generation.
        assertEquals("1", command("DEL", "tinc:client:node-issue-blocked:" + nodeId));
        String fresh = store.issue(nodeId);
        assertTrue(store.renew(nodeId, fresh));
        assertFalse(store.renew(nodeId, old));
    }

    @Test
    void concurrentRenewCannotReactivateRevokedToken() throws Exception {
        CliSessionStore store = new CliSessionStore(16379);
        long nodeId = System.currentTimeMillis() + 3001L;
        String token = store.issue(nodeId);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 12; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    store.renew(nodeId, token);
                    return null;
                }));
            }
            start.countDown();
            store.revokeNode(nodeId, true);
            for (Future<?> future : futures) future.get(15, TimeUnit.SECONDS);
            assertFalse(store.isValid(nodeId, token));
            assertFalse(store.renew(nodeId, token));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void redisFailureFailsClosed() {
        CliSessionStore unreachable = new CliSessionStore(16380);
        long nodeId = System.currentTimeMillis() + 4001L;
        assertThrows(IllegalStateException.class, () -> unreachable.issue(nodeId));
        assertThrows(IllegalStateException.class, () -> unreachable.renew(nodeId, "old-token"));
    }

    private static long number(String text) {
        return Long.parseLong(text.trim());
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest) out.append(String.format("%02x", b & 255));
        return out.toString();
    }

    private static String command(String... args) {
        return run(16379, args);
    }

    private static String run(int port, String... args) {
        List<String> argv = new ArrayList<>(Arrays.asList(redisCli, "--raw", "-h", "127.0.0.1",
                "-p", String.valueOf(port)));
        argv.addAll(Arrays.asList(args));
        try {
            Process process = new ProcessBuilder(argv).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("isolated Redis command timed out");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (out.size() + read > 65536) throw new IllegalStateException("Redis response too large");
                    out.write(buffer, 0, read);
                }
            }
            if (process.exitValue() != 0) throw new IllegalStateException("isolated Redis unavailable");
            String result = new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
            if (result.startsWith("ERR ")) throw new IllegalStateException("isolated Redis Lua error: " + result);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("isolated Redis command failed", e);
        }
    }

    private static final class CliSessionStore extends TincClientSessionStore {
        private final int port;

        CliSessionStore(int port) { this.port = port; }

        @Override
        public boolean isRevoked(String token) {
            try {
                return "1".equals(run(port, "EXISTS", "tinc:client:revoked-token:" + sha256(token)));
            } catch (Exception e) {
                throw new IllegalStateException("isolated Redis unavailable", e);
            }
        }

        @Override
        public Long execute(DefaultRedisScript<Long> script, List<String> keys, Object... args) {
            List<String> argv = new ArrayList<>();
            argv.add("--eval");
            Path scriptFile;
            try {
                scriptFile = Files.createTempFile("tinclink-isolated-redis-", ".lua");
                Files.write(scriptFile, script.getScriptAsString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException("cannot prepare isolated Redis Lua", e);
            }
            argv.add(scriptFile.toAbsolutePath().toString());
            argv.addAll(keys);
            argv.add(",");
            for (Object arg : args) {
                if (!(arg instanceof String)) throw new IllegalStateException("Lua argument is not a string");
                argv.add((String) arg);
            }
            try {
                return number(run(port, argv.toArray(new String[0])));
            } finally {
                try { Files.deleteIfExists(scriptFile); } catch (Exception ignored) { }
            }
        }
    }
}
