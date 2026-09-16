package com.ruoyi.common.tinc.runtime;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalTincCommandExecutorTest {

    @Test
    void acceptsOnlyExactRuntimeCommandShapes() {
        assertDoesNotThrow(() -> LocalTincCommandExecutor.validateCommand(Arrays.asList(
                "/usr/bin/systemctl", "enable", "--now", "tinc@network1.service")));
        assertDoesNotThrow(() -> LocalTincCommandExecutor.validateCommand(Arrays.asList(
                "/usr/sbin/tincd", "-n", "network1", "-kHUP")));
        assertDoesNotThrow(() -> LocalTincCommandExecutor.validateCommand(Arrays.asList(
                "/usr/sbin/ss", "-H", "-lunp", "sport", "=", ":655")));
        assertDoesNotThrow(() -> LocalTincCommandExecutor.validateCommand(Arrays.asList(
                "/usr/bin/firewall-cmd", "--permanent", "--query-port=655/udp")));
        assertDoesNotThrow(() -> LocalTincCommandExecutor.validateCommand(Arrays.asList(
                "/usr/bin/sudo", "-n", "/usr/local/sbin/ruoyi-tinc-runtime",
                "systemctl", "is-active", "tinc@network1.service")));
    }

    @Test
    void rejectsShellRestartTraversalAndOutOfRangePorts() {
        assertThrows(IllegalArgumentException.class, () -> LocalTincCommandExecutor.validateCommand(Arrays.asList(
                "/usr/bin/sh", "-c", "id")));
        assertThrows(IllegalArgumentException.class, () -> LocalTincCommandExecutor.validateCommand(Arrays.asList(
                "/usr/bin/systemctl", "restart", "tinc@network1.service")));
        assertThrows(IllegalArgumentException.class, () -> LocalTincCommandExecutor.validateCommand(Arrays.asList(
                "/usr/sbin/tincd", "-n", "../../etc", "-kHUP")));
        assertThrows(IllegalArgumentException.class, () -> LocalTincCommandExecutor.validateCommand(Arrays.asList(
                "/usr/bin/firewall-cmd", "--add-port=65536/udp")));
        assertThrows(IllegalArgumentException.class, () -> LocalTincCommandExecutor.validateCommand(Arrays.asList(
                "/usr/bin/sudo", "-n", "/usr/local/sbin/ruoyi-tinc-runtime",
                "systemctl", "stop", "tinc@network1.service")));
    }

    @Test
    void rejectsTimeoutAboveSixtySecondsBeforeProcessLaunch() {
        LocalTincCommandExecutor executor = new LocalTincCommandExecutor();
        assertThrows(IllegalArgumentException.class, () -> executor.execute(Arrays.asList(
                "/usr/bin/systemctl", "is-active", "tinc@network1.service"),
                Duration.ofMillis(60_001)));
    }
}
