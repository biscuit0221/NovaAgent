package com.novaagent.tool;

import com.novaagent.llm.ToolSpec;
import java.util.Map;

/**
 * A NovaAgent tool: a unit of capability the LLM can invoke.
 *
 * Implementations should be small, idempotent, and return text. Errors should be
 * returned as strings rather than thrown so the LLM can see them and self-correct.
 */
public interface Tool {
    ToolSpec.FunctionSpec spec();
    String invoke(Map<String, Object> args);
}