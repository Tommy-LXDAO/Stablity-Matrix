package com.stability.martrix.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void resetAnalysisArtifacts_shouldClearDerivedState() {
        SessionContext context = new SessionContext("session-2");
        context.setCrashInfo(new CrashInfo());
        context.setPatternMatchResult(PatternMatchResult.builder().confidence(0.8).result("hit").build());
        context.setTopCodeLocation(new CodeLocation("foo.cpp", 42, "main"));
        context.setRootCauseInsight(new RootCauseInsight());
        context.setProgrammingAdvice(new ProgrammingAdvice());
        context.addProcessLog("log-1");
        context.setSuccess(true);

        context.resetAnalysisArtifacts();

        assertNull(context.getCrashInfo());
        assertNull(context.getTombstone());
        assertNull(context.getPatternMatchResult());
        assertNull(context.getTopCodeLocation());
        assertNull(context.getRootCauseInsight());
        assertNull(context.getProgrammingAdvice());
        assertNotNull(context.getProcessLogs());
        assertEquals(0, context.getProcessLogs().size());
        assertFalse(context.isSuccess());
    }
}
