package com.novaagent.llm;

import java.util.List;

public final class ChatCompletionResult {

    public static final String STOP = "stop";
    public static final String TOOL_CALLS = "tool_calls";
    public static final String LENGTH = "length";
    public static final String CONTENT_FILTER = "content_filter";
    public static final String ERROR = "error";

    private final String content;
    private final List<ToolSpec.ToolCall> toolCalls;
    private final String finishReason;
    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;

    public ChatCompletionResult(String content,
                                List<ToolSpec.ToolCall> toolCalls,
                                String finishReason,
                                int promptTokens,
                                int completionTokens,
                                int totalTokens) {
        this.content = content == null ? "" : content;
        this.toolCalls = toolCalls == null ? List.of() : toolCalls;
        this.finishReason = finishReason;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    public String content() { return content; }
    public List<ToolSpec.ToolCall> toolCalls() { return toolCalls; }
    public String finishReason() { return finishReason; }
    public int promptTokens() { return promptTokens; }
    public int completionTokens() { return completionTokens; }
    public int totalTokens() { return totalTokens; }

    public boolean requestedToolCall() {
        return !toolCalls.isEmpty() || TOOL_CALLS.equals(finishReason);
    }
}