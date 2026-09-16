package com.ruoyi.common.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

class LocalTincConfigTransportTest {
    @TempDir Path root;

    @Test
    void atomicallyWritesAndBoundsPathsUnderTincRoot() throws Exception {
        Path hosts = root.resolve("safe_net/hosts");
        Files.createDirectories(hosts);
        LocalTincConfigTransport transport = new LocalTincConfigTransport(root.toString());
        Path target = hosts.resolve("client_a");
        String first = "Subnet = 10.0.0.2/32\nAAA\n";
        String second = "Subnet = 10.0.0.2/32\nBBB\n";
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 12; i++) {
            final String value = i % 2 == 0 ? first : second;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    transport.pushFile("ignored", target.toString(), value, false);
                } catch (Exception e) {
                    failures.add(e);
                }
            });
            thread.start();
            threads.add(thread);
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        assertTrue(failures.isEmpty(), "concurrent atomic writes failed: " + failures);
        String actual = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        assertTrue(first.equals(actual) || second.equals(actual));
        assertThrows(IllegalArgumentException.class,
                () -> transport.pushFile("ignored", root.resolve("../escape").toString(), "x", false));
    }

    @Test
    void rejectsSymbolicLinkThatEscapesConfiguredRoot() throws Exception {
        Path outside = Files.createTempDirectory("tinc-outside-");
        Path link = root.resolve("linked");
        try {
            try {
                Files.createSymbolicLink(link, outside);
            } catch (UnsupportedOperationException | java.io.IOException | SecurityException e) {
                Assumptions.assumeTrue(false, "symbolic links are unavailable on this test host");
            }
            LocalTincConfigTransport transport = new LocalTincConfigTransport(root.toString());
            assertThrows(IllegalArgumentException.class,
                    () -> transport.pushFile("ignored", link.resolve("peer").toString(), "x", false));
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside);
        }
    }
}
