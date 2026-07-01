package com.novaagent.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI tools[] spec + tool_call result structs.
 */
public final class ToolSpec {

    public static final String TYPE_FUNCTION = "function";

    private final String type;
    private final FunctionSpec function;

    public ToolSpec(FunctionSpec function) {
        this.type = TYPE_FUNCTION;
        this.function = function;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("function", function.toMap());
        return m;
    }

    public FunctionSpec function() { return function; }

    public static final class FunctionSpec {
        private final String name;
        private final String description;
        private final Map<String, Object> parameters;
        private final boolean strict;

        public FunctionSpec(String name, String description, Map<String, Object> parameters) {
            this(name, description, parameters, false);
        }
        public FunctionSpec(String name, String description, Map<String, Object> parameters, boolean strict) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
            this.strict = strict;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("description", description);
            m.put("parameters", parameters);
            m.put("strict", strict);
            return m;
        }
        public String name() { return name; }
        public String description() { return description; }
        public Map<String, Object> parameters() { return parameters; }
    }

    public static final class ToolCall {
        private final String id;
        private final String type;
        private final FunctionCall function;

        public ToolCall(String id, String type, FunctionCall function) {
            this.id = id; this.type = type; this.function = function;
        }
        public String id() { return id; }
        public String type() { return type; }
        public FunctionCall function() { return function; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("type", type);
            m.put("function", function.toMap());
            return m;
        }

        public static ToolCall fromMap(Map<String, Object> map) {
            Object id = map.get("id");
            Object type = map.get("type");
            Object fn = map.get("function");
            if (!(fn instanceof Map<?, ?> fnMap)) {
                throw new IllegalArgumentException("tool_call.function missing or invalid");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> fnTyped = (Map<String, Object>) fnMap;
            Object name = fnTyped.get("name");
            Object arguments = fnTyped.get("arguments");
            String argumentsText = (arguments == null) ? "" : (arguments instanceof String s ? s : arguments.toString());
            return new ToolCall(
                id == null ? "" : id.toString(),
                type == null ? TYPE_FUNCTION : type.toString(),
                new FunctionCall(name == null ? "" : name.toString(), argumentsText));
        }
    }

    public static final class FunctionCall {
        private final String name;
        private final String arguments;

        public FunctionCall(String name, String arguments) {
            this.name = name;
            this.arguments = arguments == null ? "" : arguments;
        }
        public String name() { return name; }
        public String arguments() { return arguments; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("arguments", arguments);
            return m;
        }
    }
}