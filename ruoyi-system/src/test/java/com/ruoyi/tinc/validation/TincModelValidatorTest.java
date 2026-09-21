package com.ruoyi.tinc.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TincModelValidatorTest {
    @Test
    void acceptsCanonicalNamesAndRejectsUnsafeOrReservedNames() {
        assertEquals("new_network", TincModelValidator.requireNewRuntimeName("new_network", "网络名称"));
        assertEquals("DEV_A", TincModelValidator.requireNewRuntimeName("DEV_A", "网络名称"));
        assertThrows(IllegalArgumentException.class,
                () -> TincModelValidator.requireNewRuntimeName("../network", "网络名称"));
        assertThrows(IllegalArgumentException.class,
                () -> TincModelValidator.requireNodeName("server_master"));
    }

    @Test
    void nodeIpMustBelongToNetworkAndCannotUseReservedHostAddresses() {
        assertEquals("10.0.11.211", TincModelValidator.requireNodeIp("10.0.11.211", "10.0.11"));
        assertThrows(IllegalArgumentException.class,
                () -> TincModelValidator.requireNodeIp("10.0.12.211", "10.0.11"));
        assertThrows(IllegalArgumentException.class,
                () -> TincModelValidator.requireNodeIp("10.0.11.1", "10.0.11"));
        assertThrows(IllegalArgumentException.class,
                () -> TincModelValidator.requireNodeIp("10.0.11.211/32", "10.0.11"));
    }
}
