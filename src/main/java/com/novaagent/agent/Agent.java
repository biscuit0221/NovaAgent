package com.novaagent.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaagent.llm.ChatCompletionResult;
import com.novaagent.llm.ChatMessage;
import com.novaagent.llm.LlmClient;
import com.novaagent.llm.ToolSpec;
import com.novaagent.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ReAct agent loop. Repeatedly calls the LLM with the full history and
 * the tool registry until the model returns a stop OR we hit maxSteps.
 */
public final class Agent {

    private static final Logger LOG = LoggerFactory.getLogger(Agent.class);

    private final LlmClient llm;
    private final ToolRegistry tools;
    private final String systemPrompt;
    private final int maxSteps;
    private final int maxToolOutputChars;
    private final ObjectMapper mapper = new ObjectMapper();

    public Agent(LlmClient llm, ToolRegistry tools, String systemPrompt,
                 int maxSteps, int maxToolOutputChars) {
        this.llm = llm;
        this.tools = tools;
        this.systemPrompt = systemPrompt;
        this.maxSteps = maxSteps;
        this.maxToolOutputChars = maxToolOutputChars;
    }

    public List<ChatMessage> run(String userInput, Consumer<String> streamDelta) {
        return run(userInput, null, streamDelta);
    }

    /**
     * Variant of {@link #run(String, Consumer)} that lets the caller prepend
     * a system-prompt override for this single run. Used by
     * {@code com.novaagent.plan.PlanExecutor} so each plan step can carry
     * a "you are executing step N of a plan" prefix on top of the
     * project-wide ReAct system prompt, without mutating the agent's
     * shared state.
     */
    public List<ChatMessage> run(String userInput, String systemPromptOverride, Consumer<String> streamDelta) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("userInput must not be empty");
        }
        String effectiveSystemPrompt = (systemPromptOverride == null || systemPromptOverride.isBlank())
            ? systemPrompt
            : systemPromptOverride + "\n\n" + systemPrompt;
        List<ChatMessage> history = new ArrayList<>();
        history.add(ChatMessage.system(effectiveSystemPrompt));
        history.add(ChatMessage.user(userInput));

        List<ToolSpec> specs = tools.toolSpecs();
        int step = 0;
        while (step < maxSteps) {
            step++;
            ChatCompletionResult result;
            try {
                result = llm.chat(history, specs, true, streamDelta);
            } catch (RuntimeException ex) {
                LOG.error("LLM call failed at step {}", step, ex);
                history.add(ChatMessage.assistant("[novaagent] model call failed: " + ex.getMessage()));
                break;
            }

            if (!result.content().isEmpty() || !result.toolCalls().isEmpty()) {
                if (result.toolCalls().isEmpty()) {
                    history.add(ChatMessage.assistant(result.content()));
                } else {
                    history.add(ChatMessage.assistantWithToolCalls(result.content(), toMapList(result.toolCalls())));
                }
            }
            if (!result.requestedToolCall()) break;

            for (ToolSpec.ToolCall call : result.toolCalls()) {
                String rawArgs = call.function().arguments();
                Map<String, Object> argMap;
                try {
                    argMap = (rawArgs == null || rawArgs.isBlank())
                        ? Map.of()
                        : mapper.readValue(rawArgs, new TypeReference<Map<String, Object>>() {});
                } catch (Exception ex) {
                    argMap = Map.of();
                }
                String toolName = call.function().name();
                String output;
                try {
                    output = tools.invoke(toolName, argMap);
                } catch (Exception ex) {
                    output = "tool " + toolName + " threw an exception: " + ex.getMessage();
                }
                if (output.length() > maxToolOutputChars) {
                    output = output.substring(0, maxToolOutputChars) + "\n... [truncated to " + maxToolOutputChars + " chars]\n";
                }
                history.add(ChatMessage.toolResult(call.id(), toolName, output));
            }
        }
        if (step >= maxSteps && step > 0) {
            history.add(ChatMessage.assistant("[novaagent] reached max steps (" + maxSteps + ") and stopped."));
        }
        return history;
    }

    private List<Map<String, Object>> toMapList(List<ToolSpec.ToolCall> calls) {
        List<Map<String, Object>> out = new ArrayList<>(calls.size());
        for (ToolSpec.ToolCall c : calls) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id());
            m.put("type", c.type());
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", c.function().name());
            fn.put("arguments", c.function().arguments());
            m.put("function", fn);
            out.add(m);
        }
        return out;
    }
}