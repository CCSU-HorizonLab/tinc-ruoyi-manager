package com.ruoyi.tinc.session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import static org.junit.jupiter.api.Assertions.*;

class TincClientSessionStoreTest {
    @Test
    void issueUsesAtomicScriptAndRejectsMissingAcknowledgment() {
        StubStore store = new StubStore();
        store.results.add(1L);
        store.results.add(0L);
        String token = store.issue(17L);
        assertEquals(43, token.length());
        assertThrows(IllegalStateException.class, () -> store.issue(17L));
        assertEquals(4, store.keys.get(0).size());
        assertEquals("tinc:client:node-tokens:17", store.keys.get(0).get(1));
        assertEquals("1800", store.args.get(0)[2]);
        assertTrue(store.scripts.get(0).getScriptAsString().contains("'NX'"));
    }

    @Test
    void missingOrExpiredSessionNeverRenewsAndRedisFailureFailsClosed() {
        StubStore store = new StubStore();
        store.results.add(0L);
        store.results.add(0L);
        assertFalse(store.renew(17L, "old-token"));
        assertFalse(store.isValid(17L, "old-token"));
        assertTrue(store.scripts.get(0).getScriptAsString().contains("'TTL'"));
        assertTrue(store.scripts.get(0).getScriptAsString().contains("'SISMEMBER'"));
        assertEquals("1800", store.args.get(0)[2]);
        assertThrows(IllegalStateException.class, () -> store.renew(17L, "old-token"));
    }

    @Test
    void revokeFencesGenerationAndAllIndexedTokensAtomically() {
        StubStore store = new StubStore();
        store.results.add(null);
        store.results.add(2L);
        assertThrows(IllegalStateException.class, () -> store.revokeNode(17L, true));
        store.revokeNode(17L, true);
        assertEquals(Arrays.asList("tinc:client:node-tokens:17",
                "tinc:client:node-generation:17", "tinc:client:node-issue-blocked:17"), store.keys.get(0));
        assertTrue(store.scripts.get(0).getScriptAsString().contains("'INCR'"));
        assertTrue(store.scripts.get(0).getScriptAsString().contains("'SMEMBERS'"));
        assertEquals("0", store.args.get(0)[1]);
    }

    private static final class StubStore extends TincClientSessionStore {
        final List<Long> results = new ArrayList<>();
        final List<List<String>> keys = new ArrayList<>();
        final List<Object[]> args = new ArrayList<>();
        final List<DefaultRedisScript<Long>> scripts = new ArrayList<>();

        @Override
        protected Long execute(DefaultRedisScript<Long> script, List<String> scriptKeys, Object... scriptArgs) {
            scripts.add(script);
            keys.add(scriptKeys);
            args.add(scriptArgs);
            if (results.isEmpty()) throw new IllegalStateException("Redis offline");
            return results.remove(0);
        }
    }
}
