package com.ruoyi.common.utils;

import com.ruoyi.common.tinc.runtime.TincRuntimeManager;
import com.ruoyi.common.transport.TincConfigTransport;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TincConfigUtilsTest {

    @TempDir
    Path temporaryDirectory;

    private final String originalBasePath = TincConfigUtils.getBasePath();

    @AfterEach
    void resetStaticDependencies() {
        TincConfigUtils.setTransport(null);
        TincConfigUtils.setRuntimeManager(null);
        TincConfigUtils.setBasePath(originalBasePath);
    }

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

    @Test
    void revokeClientHostDeletesReloadsAndChecksReadiness() {
        TincConfigTransport transport = mock(TincConfigTransport.class);
        TincRuntimeManager runtime = mock(TincRuntimeManager.class);
        String path = "/etc/tinc/network_a/hosts/alice";
        when(transport.readFile(anyString(), eq(path))).thenReturn("old-host", (String) null);
        TincConfigUtils.setTransport(transport);
        TincConfigUtils.setRuntimeManager(runtime);

        TincConfigUtils.revokeClientHost("203.0.113.10", "network_a", "alice");

        verify(transport).deleteNode("203.0.113.10", "network_a", "alice");
        verify(runtime).reloadNetwork("network_a");
        verify(runtime).ensureNetworkReady("network_a");
    }

    @Test
    void revokeFailureRestoresPreviousHost() {
        TincConfigTransport transport = mock(TincConfigTransport.class);
        TincRuntimeManager runtime = mock(TincRuntimeManager.class);
        String path = "/etc/tinc/network_a/hosts/alice";
        when(transport.readFile(anyString(), eq(path))).thenReturn("old-host", (String) null);
        doThrow(new IllegalStateException("HUP failed")).doNothing()
                .when(runtime).reloadNetwork("network_a");
        TincConfigUtils.setTransport(transport);
        TincConfigUtils.setRuntimeManager(runtime);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> TincConfigUtils.revokeClientHost("203.0.113.10", "network_a", "alice"));

        verify(transport).pushFile("203.0.113.10", path, "old-host", false);
        verify(runtime, times(2)).reloadNetwork("network_a");
    }

    @Test
    void linuxScriptsUseFixedIpExecutableInsteadOfLegacyIfconfig() throws Exception {
        TincConfigUtils.setBasePath(temporaryDirectory.toString());

        TincConfigUtils.createTincUpAndDown(null, "network_a", "10.0.11.1");

        String up = new String(Files.readAllBytes(
                temporaryDirectory.resolve("network_a").resolve("tinc-up")), StandardCharsets.UTF_8);
        String down = new String(Files.readAllBytes(
                temporaryDirectory.resolve("network_a").resolve("tinc-down")), StandardCharsets.UTF_8);
        assertTrue(up.contains("/usr/sbin/ip address replace 10.0.11.1/24 dev \"$INTERFACE\""));
        assertTrue(up.contains("/usr/sbin/ip link set dev \"$INTERFACE\" up"));
        assertTrue(down.contains("/usr/sbin/ip link set dev \"$INTERFACE\" down"));
        assertFalse(up.contains("ifconfig"));
        assertFalse(down.contains("ifconfig"));
    }
}
