package com.ruoyi.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TincNodePasswordCodecTest {
    @Test
    void hashesNewPasswordAndVerifiesBothHashAndLegacyValue() {
        String encoded = TincNodePasswordCodec.encode("client-password");
        assertNotEquals("client-password", encoded);
        assertTrue(TincNodePasswordCodec.isEncoded(encoded));
        assertTrue(TincNodePasswordCodec.matches("client-password", encoded));
        assertFalse(TincNodePasswordCodec.matches("wrong", encoded));
        assertTrue(TincNodePasswordCodec.matches("legacy-password", "legacy-password"));
        assertFalse(TincNodePasswordCodec.matches("wrong", "legacy-password"));
        assertEquals(64, TincNodePasswordCodec.legacyFingerprint("legacy-password").length());
        assertFalse(TincNodePasswordCodec.legacyFingerprint("legacy-password").contains("legacy-password"));
    }

    @Test
    void refusesKeyMaterialInPasswordField() {
        assertThrows(IllegalArgumentException.class,
                () -> TincNodePasswordCodec.encode("-----BEGIN RSA PUBLIC KEY-----\nabc\n-----END RSA PUBLIC KEY-----"));
    }
}
