package com.novaagent.llm;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions message struct.
 */
public final class ChatMessage {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    private final String role;
    private final Object content;
    private final String name;
    private final String toolCallId;
    private final List<Map<String, Object>> toolCalls;

    private ChatMessage(Builder b) {
        this.role = b.role;
        this.content = b.content;
        this.name = b.name;
        this.toolCallId = b.toolCallId;
        this.toolCalls = b.toolCalls;
    }

    public static Builder builder() { return new Builder(); }

    public static ChatMessage system(String text) {
        return builder().role(ROLE_SYSTEM).content(text).build();
    }
    public static ChatMessage user(String text) {
        return builder().role(ROLE_USER).content(text).build();
    }
    public static ChatMessage assistant(String text) {
        return builder().role(ROLE_ASSISTANT).content(text).build();
    }
    public static ChatMessage assistantWithToolCalls(String text, List<Map<String, Object>> toolCalls) {
        return builder().role(ROLE_ASSISTANT).content(text).toolCalls(toolCalls).build();
    }
    public static ChatMessage toolResult(String toolCallId, String name, String content) {
        return builder().role(ROLE_TOOL).content(content).name(name).toolCallId(toolCallId).build();
    }

    public String getRole() { return role; }
    public Object getContent() { return content; }
    public String getName() { return name; }
    public String getToolCallId() { return toolCallId; }
    public List<Map<String, Object>> getToolCalls() { return toolCalls; }

    public Map<String, Object> toMap() { return ChatMessageSerializer.toMap(this); }

    public static final class Builder {
        private String role;
        private Object content;
        private String name;
        private String toolCallId;
        private List<Map<String, Object>> toolCalls;

        public Builder role(String role) { this.role = role; return this; }
        public Builder content(Object content) { this.content = content; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder toolCallId(String id) { this.toolCallId = id; return this; }
        public Builder toolCalls(List<Map<String, Object>> calls) { this.toolCalls = calls; return this; }

        public ChatMessage build() { return new ChatMessage(this); }
    }
}