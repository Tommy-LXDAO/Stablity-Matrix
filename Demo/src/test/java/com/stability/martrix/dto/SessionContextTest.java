package com.stability.martrix.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SessionContextTest {

    @Test
    void addChatMessage_shouldAppendMessageHistory() {
        SessionContext context = new SessionContext("session-1");

        context.addChatMessage("user", "first question");
        context.addChatMessage("assistant", "first answer");

        assertNotNull(context.getChatMessages());
        assertEquals(2, context.getChatMessages().size());
        assertEquals("user", context.getChatMessages().get(0).getRole());
        assertEquals("first answer", context.getChatMessages().get(1).getContent());
        assertNotNull(context.getChatMessages().get(0).getTimestamp());
    }
}
