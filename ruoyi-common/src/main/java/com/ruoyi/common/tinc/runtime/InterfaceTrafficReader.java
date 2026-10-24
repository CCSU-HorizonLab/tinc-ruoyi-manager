package com.ruoyi.common.tinc.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/** Reads Linux interface counters without shell commands or synthetic fallback values. */
final class InterfaceTrafficReader {
    private static final Pattern INTERFACE = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,14}");
    private static final Pattern COUNTER = Pattern.compile("[0-9]{1,20}");
    private final Path root;

    InterfaceTrafficReader() {
        this(Paths.get("/sys/class/net"));
    }

    InterfaceTrafficReader(Path root) {
        this.root = root;
    }

    long[] read(String interfaceName) {
        if (interfaceName == null || !INTERFACE.matcher(interfaceName).matches()) return null;
        Path statistics = root.resolve(interfaceName).resolve("statistics");
        Long received = counter(statistics.resolve("rx_bytes"));
        Long transmitted = counter(statistics.resolve("tx_bytes"));
        return received == null || transmitted == null ? null : new long[] { received, transmitted };
    }

    private Long counter(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            // sysfs reports a synthetic file size, so bound the actual read instead.
            byte[] bytes = new byte[33];
            int length = 0;
            while (length < bytes.length) {
                int count = input.read(bytes, length, bytes.length - length);
                if (count < 0) break;
                length += count;
            }
            if (length == 0 || length > 32) return null;
            String value = new String(bytes, 0, length, StandardCharsets.US_ASCII).trim();
            if (!COUNTER.matcher(value).matches()) return null;
            return Long.parseLong(value);
        } catch (IOException | NumberFormatException | SecurityException e) {
            return null;
        }
    }
}
