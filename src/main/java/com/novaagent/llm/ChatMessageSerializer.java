package com.novaagent.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ChatMessageSerializer {
    private ChatMessageSerializer() {}

    static Map<String, Object> toMap(ChatMessage m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", m.getRole());
        Object content = m.getContent();
        if (content == null) {
            if (ChatMessage.ROLE_ASSISTANT.equals(m.getRole())) {
                map.put("content", null);
            } else {
                map.put("content", "");
            }
        } else {
            map.put("content", content);
        }
        if (m.getName() != null && !m.getName().isBlank()) map.put("name", m.getName());
        if (m.getToolCallId() != null) map.put("tool_call_id", m.getToolCallId());
        if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
            map.put("tool_calls", new ArrayList<>(m.getToolCalls()));
        }
        return map;
    }

    static List<Map<String, Object>> toMessageList(List<ChatMessage> messages) {
        List<Map<String, Object>> list = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) list.add(toMap(m));
        return list;
    }
}