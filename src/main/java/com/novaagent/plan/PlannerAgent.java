package com.novaagent.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaagent.llm.ChatCompletionResult;
import com.novaagent.llm.ChatMessage;
import com.novaagent.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Decomposes a free-form user task into a {@link Plan} by asking the LLM
 * exactly once, in a planning-only role (no tools are passed).
 *
 * <p>Design notes for Phase 2:</p>
 * <ul>
 *   <li><b>No tools.</b> The planner LLM call is a thinking step, not an
 *       acting step. Passing the tool registry would tempt the model to
 *       skip planning and start doing the work inline.</li>
 *   <li><b>No streaming.</b> Streaming adds no value for a JSON payload
 *       that has to be fully parsed before validation can run.</li>
 *   <li><b>JSON extraction is defensive.</b> Even with a strict system
 *       prompt the model occasionally wraps the payload in a ```json fence
 *       or prefixes it with prose. We try the cleanest parse first, then
 *       fall back to stripping fences, then to brace-matching the first
 *       balanced top-level object.</li>
 * </ul>
 */
public final class PlannerAgent {

    private static final Logger LOG = LoggerFactory.getLogger(PlannerAgent.class);

    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlannerAgent(LlmClient llm) {
        if (llm == null) throw new IllegalArgumentException("llm is required");
        this.llm = llm;
    }

    /**
     * Plans the given task. Throws {@link PlanParseException} if the LLM
     * response cannot be turned into a valid plan.
     */
    public Plan plan(String task) {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("task must not be blank");
        }
        List<ChatMessage> messages = List.of(
            ChatMessage.system(PlanPrompts.PLANNER_SYSTEM),
            ChatMessage.user(PlanPrompts.plannerUserMessage(task))
        );
        ChatCompletionResult result;
        try {
            // tools=null: planner is thinking-only. stream=false: no value in streaming a JSON blob.
            result = llm.chat(messages, null, false, null);
        } catch (RuntimeException ex) {
            throw new PlanParseException("planner LLM call failed: " + ex.getMessage(), ex);
        }
        String content = result.content() == null ? "" : result.content().trim();
        if (content.isEmpty()) {
            throw new PlanParseException("planner LLM returned an empty response");
        }
        Plan plan = parse(content, task);
        plan.validate();
        return plan;
    }

    /** Visible for tests. Parses raw model output into a Plan. */
    Plan parse(String content, String task) {
        JsonNode root;
        try {
            root = tryParse(content);
        } catch (Exception ex) {
            throw new PlanParseException("planner output is not valid JSON: " + ex.getMessage(), ex);
        }
        JsonNode steps = root.path("steps");
        if (!steps.isArray() || steps.isEmpty()) {
            throw new PlanParseException("planner JSON has no 'steps' array");
        }
        List<Step> parsed = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            JsonNode n = steps.get(i);
            String id = textOrNull(n, "id");
            String title = textOrNull(n, "title");
            String goal = textOrNull(n, "goal");
            if (id == null) throw new PlanParseException("step #" + (i + 1) + " has no id");
            List<String> deps = new ArrayList<>();
            JsonNode depNode = n.path("depends_on");
            if (depNode.isArray()) {
                for (JsonNode d : depNode) {
                    String s = d.asText(null);
                    if (s != null && !s.isBlank()) deps.add(s);
                }
            } else if (depNode.isTextual()) {
                // tolerate the model occasionally writing "s1, s2" as a string
                for (String s : depNode.asText().split(",")) {
                    s = s.trim();
                    if (!s.isEmpty()) deps.add(s);
                }
            }
            parsed.add(new Step(id, title == null ? "" : title, goal == null ? "" : goal, deps));
        }
        return new Plan(task, parsed);
    }

    private JsonNode tryParse(String content) throws Exception {
        // 1) clean parse
        try { return mapper.readTree(content); }
        catch (Exception ignored) { LOG.debug("planner clean parse failed; trying fence strip"); }
        // 2) strip ```json ... ``` fences
        String stripped = stripFences(content);
        if (!stripped.equals(content)) {
            try { return mapper.readTree(stripped); }
            catch (Exception ignored) { LOG.debug("planner fence-stripped parse failed; trying brace match"); }
        } else {
            stripped = content;
        }
        // 3) first balanced top-level {...} object
        String balanced = firstBalancedObject(stripped);
        if (balanced != null) {
            return mapper.readTree(balanced);
        }
        // give up - re-throw the stripped parse error to the caller via the original
        return mapper.readTree(stripped);
    }

    private static String stripFences(String s) {
        // Match the first ```...``` block (any language) and keep the body.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "```(?:json|JSON|js|javascript)?\\s*\\n?(.*?)\\n?```",
            java.util.regex.Pattern.DOTALL).matcher(s);
        if (m.find()) return m.group(1).trim();
        return s;
    }

    private static String firstBalancedObject(String s) {
        int start = s.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return s.substring(start, i + 1);
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode parent, String field) {
        JsonNode n = parent.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        String s = n.asText();
        return s == null ? null : s;
    }

    /** Thrown when planner output cannot be turned into a valid {@link Plan}. */
    public static final class PlanParseException extends RuntimeException {
        public PlanParseException(String message) { super(message); }
        public PlanParseException(String message, Throwable cause) { super(message, cause); }
    }
}
