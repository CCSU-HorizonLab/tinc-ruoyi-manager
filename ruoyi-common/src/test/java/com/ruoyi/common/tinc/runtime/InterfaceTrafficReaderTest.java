package com.ruoyi.common.tinc.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InterfaceTrafficReaderTest {
    @TempDir Path root;

    @Test
    void readsRealCountersIncludingZero() throws Exception {
        Path statistics = Files.createDirectories(root.resolve("tinc0/statistics"));
        Files.write(statistics.resolve("rx_bytes"), "0\n".getBytes(StandardCharsets.US_ASCII));
        Files.write(statistics.resolve("tx_bytes"), "123456\n".getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals(new long[] { 0, 123456 }, new InterfaceTrafficReader(root).read("tinc0"));
    }

    @Test
    void unavailableOrInvalidCounterNeverBecomesSyntheticZero() throws Exception {
        InterfaceTrafficReader reader = new InterfaceTrafficReader(root);
        assertNull(reader.read("../secret"));
        assertNull(reader.read("tinc0"));
        Path statistics = Files.createDirectories(root.resolve("tinc0/statistics"));
        Files.write(statistics.resolve("rx_bytes"), "42\n".getBytes(StandardCharsets.US_ASCII));
        assertNull(reader.read("tinc0"));
        Files.write(statistics.resolve("tx_bytes"), "not-a-counter".getBytes(StandardCharsets.US_ASCII));
        assertNull(reader.read("tinc0"));
        Files.write(statistics.resolve("tx_bytes"),
                "999999999999999999999999999999999".getBytes(StandardCharsets.US_ASCII));
        assertNull(reader.read("tinc0"));
    }
}
