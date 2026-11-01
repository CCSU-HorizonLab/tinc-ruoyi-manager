package com.ruoyi.platform;

import com.ruoyi.common.platform.NetworkEngineDescriptor;
import com.ruoyi.common.platform.NetworkEngineProvider;
import com.ruoyi.tinc.platform.TincNetworkEngineProvider;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class NetworkEngineRegistryTest {
    private NetworkEngineProvider provider(String id) {
        return () -> new NetworkEngineDescriptor(id, id, Collections.singleton("runtime-status"));
    }

    @Test
    void discoversProvidersInStableOrderAndRejectsUnknownEngine() {
        NetworkEngineRegistry registry = new NetworkEngineRegistry(
                Arrays.asList(provider("wireguard"), provider("tinc")));
        assertEquals("tinc", registry.descriptors().get(0).getEngineId());
        assertEquals("wireguard", registry.descriptors().get(1).getEngineId());
        assertTrue(registry.find("tinc").isPresent());
        assertFalse(registry.find("frp").isPresent());
        assertFalse(registry.find(null).isPresent());
        assertThrows(IllegalArgumentException.class, () -> registry.require("frp"));
        assertThrows(UnsupportedOperationException.class, () -> registry.descriptors().clear());
    }

    @Test
    void rejectsDuplicateEngineIdsAtStartup() {
        assertThrows(IllegalStateException.class,
                () -> new NetworkEngineRegistry(Arrays.asList(provider("tinc"), provider("tinc"))));
    }

    @Test
    void registersExistingTincCapabilitiesWithoutClaimingPlacementAsEngine() {
        NetworkEngineRegistry registry = new NetworkEngineRegistry(
                Collections.singletonList(new TincNetworkEngineProvider()));
        NetworkEngineDescriptor descriptor = registry.require("tinc").descriptor();
        assertTrue(descriptor.getCapabilities().contains("overlay-network"));
        assertTrue(descriptor.getCapabilities().contains("peer-key-exchange"));
        assertFalse(descriptor.getCapabilities().contains("agent"));
        assertFalse(descriptor.getCapabilities().contains("local"));
    }

    @Test
    void springDiscoversTincProvider() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(NetworkEngineRegistry.class, TincNetworkEngineProvider.class);
            context.refresh();
            assertTrue(context.getBean(NetworkEngineRegistry.class).find("tinc").isPresent());
        }
    }
}
