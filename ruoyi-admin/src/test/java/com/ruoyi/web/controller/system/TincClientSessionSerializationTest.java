package com.ruoyi.web.controller.system;

import com.ruoyi.framework.config.FastJson2JsonRedisSerializer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Checks the legacy Redis value format expected by the session Lua scripts. */
class TincClientSessionSerializationTest {
    @Test
    void existingValuesAndSetMembersAreQuotedJsonStrings() {
        FastJson2JsonRedisSerializer<Object> serializer = new FastJson2JsonRedisSerializer<>(Object.class);
        StringBuilder hashBuilder = new StringBuilder();
        for (int i = 0; i < 64; i++) hashBuilder.append('a');
        String hash = hashBuilder.toString();
        assertEquals("\"17\"", new String(serializer.serialize("17"), StandardCharsets.UTF_8));
        assertEquals("\"" + hash + "\"", new String(serializer.serialize(hash), StandardCharsets.UTF_8));
    }

    @Test
    void luaNumericArgumentsArePlainUtf8Strings() {
        StringRedisSerializer serializer = new StringRedisSerializer();
        assertEquals("1800", new String(serializer.serialize("1800"), StandardCharsets.UTF_8));
        assertEquals("0", new String(serializer.serialize("0"), StandardCharsets.UTF_8));
    }
}
