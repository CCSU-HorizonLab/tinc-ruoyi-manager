package com.ruoyi.common.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ManagedResourceIdTest {
    @Test
    void sameNumericIdInDifferentEnginesOrResourceTypesIsDistinct() {
        ManagedResourceId tinc = ManagedResourceId.of("tinc", "overlay-network", 8L);
        ManagedResourceId anotherTinc = ManagedResourceId.of("tinc", "overlay-network", 8L);
        assertEquals(tinc, anotherTinc);
        assertEquals(tinc.hashCode(), anotherTinc.hashCode());
        assertNotEquals(tinc, ManagedResourceId.of("wireguard", "overlay-network", 8L));
        assertNotEquals(tinc, ManagedResourceId.of("tinc", "endpoint", 8L));
    }

    @Test
    void rejectsInvalidAndUntrustedIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> ManagedResourceId.of("tinc;rm", "overlay-network", 8L));
        assertThrows(IllegalArgumentException.class,
                () -> ManagedResourceId.of("tinc", "overlay/network", 8L));
        assertThrows(IllegalArgumentException.class,
                () -> ManagedResourceId.of("tinc", "overlay-network", 0L));
        assertThrows(IllegalArgumentException.class,
                () -> ManagedResourceId.of("tinc", "overlay-network", null));
    }
}
