package com.ruoyi.common.tinc.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class LocalTincRuntimeManagerTest {
    @TempDir Path root;
    private FakeExecutor executor;
    private LocalTincRuntimeManager manager;

    @BeforeEach
    void setUp() throws Exception {
        executor = new FakeExecutor();
        manager = new LocalTincRuntimeManager(executor, root, Duration.ofSeconds(1), Duration.ofMillis(500));
        writeNetwork("network_a", "tinca01", 1600, "10.20.0.1");
    }

    @Test
    void rejectsCommandInjectionIdentifiersAndPorts() {
        assertThrows(IllegalArgumentException.class, () -> manager.inspectNetworkStatus("x;id"));
        assertThrows(IllegalArgumentException.class, () -> manager.inspectNetworkStatus("../x"));
        assertThrows(IllegalArgumentException.class, () -> manager.inspectNetworkStatus("CON"));
        assertThrows(IllegalArgumentException.class, () -> manager.ensureFirewallPort(0));
        assertThrows(IllegalArgumentException.class, () -> manager.ensureFirewallPort(65536));
        assertThrows(IllegalArgumentException.class, () ->
                new LocalTincCommandExecutor().execute(java.util.Arrays.asList("/bin/sh", "-c", "id"), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () ->
                new LocalTincCommandExecutor().execute(
                        java.util.Arrays.asList("/usr/bin/sudo", "-n", "/bin/sh", "-c", "id"),
                        Duration.ofSeconds(1)));
    }

    @Test
    void startsInactiveNetworkAndBecomesReady() {
        TincNetworkStatus status = manager.ensureNetworkReady("network_a");
        assertTrue(status.isReady());
        assertTrue(executor.active);
        assertTrue(executor.enabled);
        assertEquals(1, executor.enableCalls);
    }

    @Test
    void activeEnsureIsIdempotent() {
        executor.active = true;
        executor.enabled = true;
        assertTrue(manager.ensureNetworkReady("network_a").isReady());
        assertTrue(manager.ensureNetworkReady("network_a").isReady());
        assertEquals(0, executor.enableCalls);
    }

    @Test
    void reloadUsesHupAndFailureDoesNotRestart() {
        executor.active = true;
        executor.enabled = true;
        manager.reloadNetwork("network_a");
        assertEquals(1, executor.hupCalls);
        assertEquals(0, executor.enableCalls);

        executor.hupFails = true;
        TincRuntimeException failure = assertThrows(TincRuntimeException.class,
                () -> manager.reloadNetwork("network_a"));
        assertEquals(TincRuntimeErrorCode.TINC_RELOAD_FAILED, failure.getCode());
        assertEquals(0, executor.enableCalls);
    }

    @Test
    void tcpWithoutUdpIsNotReady() {
        executor.active = true;
        executor.enabled = true;
        executor.udp = false;
        TincNetworkStatus status = manager.inspectNetworkStatus("network_a");
        assertFalse(status.isReady());
        assertEquals("TINC_UDP_NOT_LISTENING", status.getFailureCode());
    }

    @Test
    void detectsInterfaceAndPortConflicts() throws Exception {
        writeNetwork("network_b", "tinca01", 1601, "10.21.0.1");
        TincRuntimeException interfaceConflict = assertThrows(TincRuntimeException.class,
                () -> manager.detectInterfaceAndPortConflicts("network_a"));
        assertEquals(TincRuntimeErrorCode.TINC_INTERFACE_CONFLICT, interfaceConflict.getCode());

        deleteNetwork("network_b");
        writeNetwork("network_b", "tincb01", 1600, "10.21.0.1");
        TincRuntimeException portConflict = assertThrows(TincRuntimeException.class,
                () -> manager.detectInterfaceAndPortConflicts("network_a"));
        assertEquals(TincRuntimeErrorCode.TINC_PORT_CONFLICT, portConflict.getCode());
    }

    @Test
    void reportsInvalidConfigPrivateKeyAndScript() throws Exception {
        Files.write(root.resolve("network_a/tinc.conf"), "Name = broken\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("TINC_CONFIG_INVALID", manager.validateNetworkConfig("network_a").getFailureCode());

        writeNetwork("network_a", "tinca01", 1600, "10.20.0.1");
        Files.delete(root.resolve("network_a/rsa_key.priv"));
        assertEquals("TINC_PRIVATE_KEY_MISSING", manager.validateNetworkConfig("network_a").getFailureCode());

        writeNetwork("network_a", "tinca01", 1600, "10.20.0.1");
        Files.createDirectory(root.resolve("network_a/tinc-up"));
        assertEquals("TINC_SCRIPT_INVALID", manager.validateNetworkConfig("network_a").getFailureCode());
    }

    @Test
    void systemctlTimeoutReturnsStartFailure() {
        executor.enableTimesOut = true;
        TincRuntimeException failure = assertThrows(TincRuntimeException.class,
                () -> manager.ensureNetworkReady("network_a"));
        assertEquals(TincRuntimeErrorCode.TINC_SERVICE_START_FAILED, failure.getCode());
    }

    @Test
    void firewallAddsBothProtocolsForRuntimeAndPermanent() {
        executor.firewallRulesPresent = false;
        manager.ensureFirewallPort(1600);
        assertTrue(executor.commands.stream().anyMatch(x -> x.contains("--add-port=1600/tcp")));
        assertTrue(executor.commands.stream().anyMatch(x -> x.contains("--add-port=1600/udp")));
        assertTrue(executor.commands.stream().anyMatch(x -> x.contains("--permanent") && x.contains("--add-port=1600/tcp")));
        assertTrue(executor.commands.stream().anyMatch(x -> x.contains("--permanent") && x.contains("--add-port=1600/udp")));
    }

    @Test
    void concurrentEnsureStartsOnlyOnce() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    assertTrue(manager.ensureNetworkReady("network_a").isReady());
                } catch (Throwable e) {
                    failures.add(e);
                }
            });
            thread.start();
            threads.add(thread);
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        assertTrue(failures.isEmpty(), "concurrent ensure failed: " + failures);
        assertEquals(1, executor.enableCalls);
    }

    @Test
    void verifiesPeerFingerprintWithoutReturningKeyMaterial() throws Exception {
        Path peer = root.resolve("network_a/hosts/client_a");
        byte[] contents = "Subnet = 10.20.0.2/32\nkey-material\n".getBytes(StandardCharsets.UTF_8);
        Files.write(peer, contents);
        String fingerprint = TincRuntimeSupport.sha256Normalized(contents);
        assertDoesNotThrow(() -> manager.verifyPeerApplied("network_a", "client_a", fingerprint));
        TincRuntimeException failure = assertThrows(TincRuntimeException.class,
                () -> manager.verifyPeerApplied("network_a", "client_a",
                        "0000000000000000000000000000000000000000000000000000000000000000"));
        assertEquals(TincRuntimeErrorCode.TINC_RUNTIME_NOT_READY, failure.getCode());
        assertFalse(failure.getMessage().contains("key-material"));
    }

    private void writeNetwork(String name, String iface, int port, String ip) throws Exception {
        deleteNetwork(name);
        Path dir = root.resolve(name);
        Files.createDirectories(dir.resolve("hosts"));
        Files.write(dir.resolve("tinc.conf"), ("Name = server_master\nInterface = " + iface
                + "\nMode = router\nPort = " + port + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("hosts/server_master"), ("Address = 203.0.113.1\nPort = " + port
                + "\nSubnet = " + ip + "/32\n\n-----BEGIN RSA PUBLIC KEY-----\nQUJD\n"
                + "-----END RSA PUBLIC KEY-----\n").getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("rsa_key.priv"), "secret-not-logged".getBytes(StandardCharsets.UTF_8));
    }

    private void deleteNetwork(String name) throws Exception {
        Path dir = root.resolve(name);
        if (!Files.exists(dir)) return;
        List<Path> paths = new ArrayList<>();
        Files.walk(dir).forEach(paths::add);
        paths.sort(java.util.Comparator.reverseOrder());
        for (Path path : paths) Files.delete(path);
    }

    private static final class FakeExecutor implements TincCommandExecutor {
        boolean active;
        boolean enabled;
        boolean tcp = true;
        boolean udp = true;
        boolean hupFails;
        boolean enableTimesOut;
        boolean firewallRulesPresent = true;
        final Set<String> addedFirewallRules = new HashSet<>();
        int enableCalls;
        int hupCalls;
        final List<List<String>> commands = new ArrayList<>();

        @Override
        public synchronized TincCommandResult execute(List<String> command, Duration timeout) {
            commands.add(new ArrayList<>(command));
            String executable = command.get(0);
            if ("/usr/bin/sh".equals(executable)) return ok("");
            if ("/usr/bin/firewall-cmd".equals(executable)) {
                if (command.contains("--state")) return ok("running");
                if (command.stream().anyMatch(x -> x.startsWith("--add-port="))) {
                    String rule = command.stream().filter(x -> x.startsWith("--add-port=")).findFirst().get()
                            .replace("--add-port=", "");
                    addedFirewallRules.add((command.contains("--permanent") ? "permanent:" : "runtime:") + rule);
                    return ok("success");
                }
                if (command.stream().anyMatch(x -> x.startsWith("--query-port="))) {
                    String rule = command.stream().filter(x -> x.startsWith("--query-port="))
                            .findFirst().get().replace("--query-port=", "");
                    boolean added = addedFirewallRules.contains((command.contains("--permanent")
                            ? "permanent:" : "runtime:") + rule);
                    return firewallRulesPresent || added ? ok("yes") : fail("no");
                }
            }
            if ("/usr/bin/systemctl".equals(executable)) {
                if (command.contains("is-active")) return active ? ok("active") : fail("inactive");
                if (command.contains("is-enabled")) return enabled ? ok("enabled") : fail("disabled");
                if (command.contains("show")) return ok(active ? "4321" : "0");
                if (command.contains("enable")) {
                    enableCalls++;
                    if (enableTimesOut) return new TincCommandResult(-1, "", true);
                    active = true;
                    enabled = true;
                    return ok("");
                }
            }
            if ("/usr/sbin/tincd".equals(executable)) {
                hupCalls++;
                return hupFails ? fail("") : ok("");
            }
            if ("/usr/sbin/ip".equals(executable)) {
                if (!active) return fail("");
                if (command.contains("addr")) return ok("7: tinca01 inet 10.20.0.1/24 scope global tinca01");
                return ok("7: tinca01: <UP>");
            }
            if ("/usr/sbin/ss".equals(executable)) {
                if (!active) return ok("");
                boolean requestedUdp = command.contains("-lunp");
                if ((requestedUdp && !udp) || (!requestedUdp && !tcp)) return ok("");
                return ok("users:((\"tincd\",pid=4321,fd=4))");
            }
            return fail("");
        }

        private TincCommandResult ok(String output) { return new TincCommandResult(0, output, false); }
        private TincCommandResult fail(String output) { return new TincCommandResult(1, output, false); }
    }
}
