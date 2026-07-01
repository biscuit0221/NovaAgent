package com.novaagent.tool;

import com.novaagent.llm.ToolSpec;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the named tools visible to the agent. Preserves insertion order for stable listings.
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        if (tool == null) throw new IllegalArgumentException("tool must not be null");
        String name = tool.spec().name();
        if (tools.containsKey(name)) throw new IllegalArgumentException("duplicate tool name: " + name);
        tools.put(name, tool);
        return this;
    }

    public Tool get(String name) { return tools.get(name); }
    public boolean contains(String name) { return tools.containsKey(name); }
    public Collection<Tool> all() { return tools.values(); }
    public List<String> names() { return List.copyOf(tools.keySet()); }

    public List<ToolSpec> toolSpecs() {
        return tools.values().stream().map(t -> new ToolSpec(t.spec())).toList();
    }

    public String invoke(String name, Map<String, Object> args) {
        Tool tool = tools.get(name);
        if (tool == null) throw new IllegalArgumentException("Unknown tool: " + name);
        return tool.invoke(args == null ? Map.of() : args);
    }
}