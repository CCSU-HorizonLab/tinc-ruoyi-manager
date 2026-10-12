package com.ruoyi.web.controller.system;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TincClientLoginLimiterTest {
    @AfterEach void clearRequest() { RequestContextHolder.resetRequestAttributes(); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void keysDoNotExposeCredentialsOrTrustForwardedHeaders() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any())).thenReturn(0L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.77");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TincClientLoginLimiter limiter = new TincClientLoginLimiter(redis);

        assertEquals(0, limiter.consume("access:TL-7K9M-2Q4F"));
        MockHttpServletRequest forgedHeader = new MockHttpServletRequest();
        forgedHeader.setRemoteAddr("203.0.113.9");
        forgedHeader.addHeader("X-Forwarded-For", "192.0.2.88");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(forgedHeader));
        assertEquals(0, limiter.consume("access:TL-7K9M-2Q4F"));
        MockHttpServletRequest differentPeer = new MockHttpServletRequest();
        differentPeer.setRemoteAddr("203.0.113.10");
        differentPeer.addHeader("X-Forwarded-For", "198.51.100.77");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(differentPeer));
        assertEquals(0, limiter.consume("access:TL-7K9M-2Q4F"));
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(redis, times(3)).execute(any(RedisScript.class), keys.capture(), eq(120), eq(60), eq(8), eq(300));
        assertEquals(2, keys.getValue().size());
        assertEquals(keys.getAllValues().get(0).get(0), keys.getAllValues().get(1).get(0));
        assertNotEquals(keys.getAllValues().get(0).get(0), keys.getAllValues().get(2).get(0));
        assertEquals(keys.getAllValues().get(0).get(1), keys.getAllValues().get(2).get(1));
        assertTrue(String.valueOf(keys.getValue().get(0)).startsWith("tinc:client:login-limit:source:"));
        assertTrue(String.valueOf(keys.getValue().get(1)).startsWith("tinc:client:login-limit:credential:"));
        for (Object key : keys.getValue()) {
            assertFalse(String.valueOf(key).contains("TL-7K9M-2Q4F"));
            assertFalse(String.valueOf(key).contains("203.0.113.9"));
            assertFalse(String.valueOf(key).contains("198.51.100.77"));
        }
        limiter.clearCredential("access:TL-7K9M-2Q4F");
        verify(redis).delete(keys.getValue().get(1));
        verify(redis, never()).delete(keys.getValue().get(0));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void deniedOrUnavailableRedisResultCannotBecomeAnAllowedLogin() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        TincClientLoginLimiter limiter = new TincClientLoginLimiter(redis);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(23L, null);
        assertEquals(23, limiter.consume("sid:legacy_node"));
        assertThrows(TincClientLoginLimiter.LimiterUnavailableException.class,
                () -> limiter.consume("sid:legacy_node"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void sourceAndCredentialCountersAreUpdatedByOneAtomicRedisScript() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any())).thenReturn(0L);
        new TincClientLoginLimiter(redis).consume("access:TEST");
        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(script.capture(), anyList(), any(), any(), any(), any());
        String lua = script.getValue().getScriptAsString();
        assertTrue(lua.contains("INCR', KEYS[1]"));
        assertTrue(lua.contains("INCR', KEYS[2]"));
        assertTrue(lua.contains("EXPIRE', KEYS[1]"));
        assertTrue(lua.contains("EXPIRE', KEYS[2]"));
    }
}
