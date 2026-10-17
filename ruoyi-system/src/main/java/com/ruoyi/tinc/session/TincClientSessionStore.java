package com.ruoyi.tinc.session;

import com.ruoyi.common.core.redis.RedisCache;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Redis is the authority for client sessions. All state transitions are single Redis commands. */
@Service
public class TincClientSessionStore {
    private static final String TOKEN = "tinc:client:token:";
    private static final String INDEX = "tinc:client:node-tokens:";
    private static final String REVOKED = "tinc:client:revoked-token:";
    private static final String GENERATION = "tinc:client:node-generation:";
    private static final String BLOCKED = "tinc:client:node-issue-blocked:";
    public static final int TOKEN_TTL_SECONDS = 30 * 60;
    public static final int ROUTE_LEASE_SECONDS = 120;
    private static final SecureRandom RANDOM = new SecureRandom();

    // Existing token values and set members are JSON strings. Script arguments use plain UTF-8
    // explicitly, so numeric arguments never depend on FastJson's WriteClassName behavior.
    private static final DefaultRedisScript<Long> ISSUE = script(
            "if redis.call('EXISTS',KEYS[4]) == 1 then return 0 end\n" +
            "local generation = redis.call('GET',KEYS[3]) or '0'\n" +
            "local value = '\"' .. ARGV[1] .. ':' .. generation .. '\"'\n" +
            "if not redis.call('SET',KEYS[1],value,'EX',ARGV[3],'NX') then return 0 end\n" +
            "redis.call('SADD',KEYS[2],'\"' .. ARGV[2] .. '\"')\n" +
            "redis.call('EXPIRE',KEYS[2],ARGV[3])\n" +
            "return 1");
    private static final String CHECK =
            "if redis.call('EXISTS',KEYS[4]) == 1 or redis.call('EXISTS',KEYS[5]) == 1 then return 0 end\n" +
            "local value = redis.call('GET',KEYS[1])\n" +
            "if not value or redis.call('TTL',KEYS[1]) <= 0 then return 0 end\n" +
            "local generation = redis.call('GET',KEYS[3]) or '0'\n" +
            "local expected = '\"' .. ARGV[1] .. ':' .. generation .. '\"'\n" +
            "if value ~= expected and not (generation == '0' and value == '\"' .. ARGV[1] .. '\"') then return 0 end\n" +
            "if redis.call('SISMEMBER',KEYS[2],'\"' .. ARGV[2] .. '\"') ~= 1 then return 0 end\n";
    private static final DefaultRedisScript<Long> AUTHENTICATE = script(CHECK + "return 1");
    private static final DefaultRedisScript<Long> RENEW = script(CHECK +
            "redis.call('EXPIRE',KEYS[1],ARGV[3])\n" +
            "redis.call('EXPIRE',KEYS[2],ARGV[3])\n" +
            "return 1");
    private static final DefaultRedisScript<Long> REVOKE = script(
            "redis.call('INCR',KEYS[2])\n" +
            "if ARGV[2] == '0' then redis.call('SET',KEYS[3],'1') " +
            "else redis.call('SET',KEYS[3],'1','EX',ARGV[2]) end\n" +
            "local members = redis.call('SMEMBERS',KEYS[1])\n" +
            "for _, member in ipairs(members) do\n" +
            "  if string.len(member) == 66 and string.sub(member,1,1) == '\"' and string.sub(member,-1) == '\"' then\n" +
            "    local hash = string.sub(member,2,-2)\n" +
            "    redis.call('SET',ARGV[1] .. hash,'1','EX',86400)\n" +
            "    redis.call('DEL',ARGV[3] .. hash)\n" +
            "  end\n" +
            "end\n" +
            "redis.call('DEL',KEYS[1])\n" +
            "return #members");

    @Autowired private RedisCache redisCache;

    private static DefaultRedisScript<Long> script(String source) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(source);
        script.setResultType(Long.class);
        return script;
    }

    public String issue(Long nodeId) {
        requireNodeId(nodeId);
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String hash = hash(token);
        Long result = execute(ISSUE, Arrays.asList(TOKEN + hash, INDEX + nodeId,
                GENERATION + nodeId, BLOCKED + nodeId), String.valueOf(nodeId), hash,
                String.valueOf(TOKEN_TTL_SECONDS));
        if (!Long.valueOf(1).equals(result)) {
            throw new IllegalStateException("客户端会话签发被拒绝");
        }
        return token;
    }

    public boolean isValid(Long nodeId, String token) {
        return check(AUTHENTICATE, nodeId, token);
    }

    public boolean renew(Long nodeId, String token) {
        return check(RENEW, nodeId, token);
    }

    private boolean check(DefaultRedisScript<Long> script, Long nodeId, String token) {
        if (nodeId == null || nodeId <= 0 || token == null || token.isEmpty()) return false;
        String hash = hash(token);
        Long result = execute(script, Arrays.asList(TOKEN + hash, INDEX + nodeId,
                GENERATION + nodeId, BLOCKED + nodeId, REVOKED + hash),
                String.valueOf(nodeId), hash, String.valueOf(TOKEN_TTL_SECONDS));
        return Long.valueOf(1).equals(result);
    }

    public boolean isRevoked(String token) {
        return token != null && !token.isEmpty()
                && redisCache.getCacheObject(REVOKED + hash(token)) != null;
    }

    /** Fences all old sessions, including tokens whose index was lost. Issuance is paused during DB changes. */
    public void revokeNode(Long nodeId, boolean deleting) {
        requireNodeId(nodeId);
        Long result = execute(REVOKE, Arrays.asList(INDEX + nodeId, GENERATION + nodeId,
                BLOCKED + nodeId), REVOKED, String.valueOf(deleting ? 0 : 60), TOKEN);
        if (result == null) throw new IllegalStateException("客户端会话撤销未确认");
    }

    protected Long execute(DefaultRedisScript<Long> script, java.util.List<String> keys, Object... args) {
        if (redisCache == null || redisCache.redisTemplate == null) {
            throw new IllegalStateException("客户端会话 Redis 不可用");
        }
        return (Long) redisCache.redisTemplate.execute(script, new StringRedisSerializer(),
                new GenericToStringSerializer<>(Long.class), keys, args);
    }

    private static void requireNodeId(Long nodeId) {
        if (nodeId == null || nodeId <= 0) throw new IllegalArgumentException("节点 ID 无效");
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[digest.length * 2];
            char[] digits = "0123456789abcdef".toCharArray();
            for (int i = 0; i < digest.length; i++) {
                hex[i * 2] = digits[(digest[i] & 0xff) >>> 4];
                hex[i * 2 + 1] = digits[digest[i] & 15];
            }
            return new String(hex);
        } catch (Exception e) {
            throw new IllegalStateException("会话摘要不可用", e);
        }
    }
}
