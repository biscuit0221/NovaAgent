package com.novaagent.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM provider abstraction (Phase 1: GLM by default, OpenAI-compatible everywhere).
 */
public interface LlmClient {

    ChatCompletionResult chat(List<ChatMessage> messages,
                              List<ToolSpec> tools,
                              boolean stream,
                              Consumer<String> onDelta);

    String name();
    String model();
}