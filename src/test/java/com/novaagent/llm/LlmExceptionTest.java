package com.novaagent.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmExceptionTest {

    @Test
    void messageAndCauseAreStored() {
        Throwable cause = new RuntimeException("boom");
        LlmException e = new LlmException("LLM failed", cause);
        assert e.getMessage().equals("LLM failed");
        assert e.getCause() == cause;
    }

    @Test
    void builderRejectsBlankApiKey() {
        assertThrows(IllegalStateException.class,
            () -> OpenAiCompatibleClient.newBuilder().apiKey("").build());
    }
}