package com.ruoyi.common.tinc.access;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessRuntimeValidatorTest {
    @Test
    void acceptsConsistentRuntimeNamesAndRejectsPathTraversal() {
        assertEquals("new_network", AccessRuntimeValidator.name("new_network", "网络名称"));
        assertEquals("DEV_A", AccessRuntimeValidator.name("DEV_A", "网络名称"));
        assertThrows(IllegalArgumentException.class,
                () -> AccessRuntimeValidator.name("../network", "网络名称"));
    }
}
