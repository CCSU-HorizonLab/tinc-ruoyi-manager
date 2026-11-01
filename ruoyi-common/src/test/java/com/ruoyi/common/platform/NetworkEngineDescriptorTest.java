package com.ruoyi.common.platform;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetworkEngineDescriptorTest {
    @Test
    void validatesIdentifiersAndProtectsCapabilitiesFromMutation() {
        Set<String> supplied = new LinkedHashSet<>(Arrays.asList("overlay-network", "runtime-status"));
        NetworkEngineDescriptor descriptor = new NetworkEngineDescriptor("tinc", "Tinc", supplied);
        supplied.clear();
        assertEquals(2, descriptor.getCapabilities().size());
        assertThrows(UnsupportedOperationException.class,
                () -> descriptor.getCapabilities().add("proxy-service"));
        assertThrows(IllegalArgumentException.class,
                () -> new NetworkEngineDescriptor("tinc;rm", "Tinc", supplied));
        assertThrows(IllegalArgumentException.class,
                () -> new NetworkEngineDescriptor("tinc", "Tinc", new LinkedHashSet<>(Arrays.asList("runtime/status"))));
    }
}
