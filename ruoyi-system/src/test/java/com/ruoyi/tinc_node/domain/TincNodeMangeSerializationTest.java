package com.ruoyi.tinc_node.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TincNodeMangeSerializationTest {
    @Test
    void passwordCanBeSubmittedButNeverReturned() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        TincNodeMange node = new TincNodeMange();
        node.setId(7L);
        node.setPassword("client-password");
        String response = mapper.writeValueAsString(node);
        assertFalse(response.contains("client-password"));
        assertFalse(response.contains("\"password\""));

        TincNodeMange submitted = mapper.readValue("{\"password\":\"new-password\"}", TincNodeMange.class);
        assertEquals("new-password", submitted.getPassword());
    }
}
