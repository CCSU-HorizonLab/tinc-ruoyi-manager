package com.ruoyi.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TincConfigUtilsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void identicalAtomicWritePreservesMtimeAndChangedContentReplacesFile() throws Exception {
        Path target = temporaryDirectory.resolve("hosts").resolve("demo_client");
        String first = "Subnet = 10.0.10.11/32\r\n\r\npublic-key-placeholder\r\n";

        assertTrue(TincConfigUtils.atomicWriteIfChanged(target, first, false));
        assertEquals("Subnet = 10.0.10.11/32\n\npublic-key-placeholder\n",
                new String(Files.readAllBytes(target), StandardCharsets.UTF_8));

        FileTime fixedTime = FileTime.from(Instant.parse("2026-09-16T00:00:00Z"));
        Files.setLastModifiedTime(target, fixedTime);
        assertFalse(TincConfigUtils.atomicWriteIfChanged(target,
                first.replace("\r\n", "\n"), false));
        assertEquals(fixedTime, Files.getLastModifiedTime(target));

        assertTrue(TincConfigUtils.atomicWriteIfChanged(target,
                "Subnet = 10.0.10.12/32\n\npublic-key-placeholder\n", false));
        assertEquals("Subnet = 10.0.10.12/32\n\npublic-key-placeholder\n",
                new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
    }
}
