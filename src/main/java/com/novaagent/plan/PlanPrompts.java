package com.novaagent.plan;

/**
 * Prompt templates for the Phase 2 plan-and-execute pipeline.
 *
 * <p>Centralised here so that future tuning (or a /plan-system command in a
 * later phase) can replace them without touching {@link PlannerAgent} or
 * {@link PlanExecutor}. Both prompts are written in English with a few
 * Chinese shorthands because that matches the working language of the
 * project; the planner accepts the user's task verbatim in any language.</p>
 */
final class PlanPrompts {

    private PlanPrompts() {}

    /**
     * System prompt for the planner pass. The model is told to behave as a
     * planning specialist, output raw JSON only, and obey strict
     * dependency rules. We deliberately do NOT pass tools to this LLM call
     * (see PlannerAgent): planning is a thinking step, not an acting step.
     */
    static final String PLANNER_SYSTEM = """
        You are a planning specialist. Your only job is to decompose a user
        task into 2-8 ordered steps that a downstream ReAct agent can run
        independently. You do not call any tools and you do not produce a
        final answer to the user.

        Output format: a single raw JSON object. No markdown fence, no
        comments, no trailing explanation, no preamble.

        Schema (MUST match exactly):
        {
          "steps": [
            {
              "id": "s1",
              "title": "short verb phrase, <= 60 chars",
              "goal": "one or two sentences telling the executor exactly what to do, including which tools/files to touch",
              "depends_on": []
            }
          ]
        }

        Hard rules:
        1. Step ids are "s1", "s2", ... in the order you want them executed.
        2. Step count is between 2 and 8 inclusive. If the task is trivial
           (a single read or write), still return 2 steps: one for the work
           and one for "report what was done".
        3. depends_on is an array of earlier step ids. The first step MUST
           have an empty array. A step may depend on multiple earlier steps.
        4. No cycles. A step may only depend on steps that appear before it.
        5. Each step's goal must be self-contained: the executor will only
           see the goal plus the outputs of its dependencies, never the
           original task. Mention concrete file paths or tool names when
           known.
        6. Do not invent tools. Use the verbs "read", "write", "list",
           "search", "run command" etc.; the executor maps them onto the
           ReAct tool registry.
        """;

    /**
     * Builds the user message sent to the planner. Kept separate from the
     * system prompt so future code can swap in a project-specific wrapper
     * (e.g. inject repository conventions) without re-stating the rules.
     */
    static String plannerUserMessage(String task) {
        return "Decompose this task into a step-by-step plan:\n\n" + task;
    }

    /**
     * System prompt prefix for an executor pass. The executor still uses the
     * ReAct loop, so this string is prepended to the regular ReAct system
     * prompt at call time; it is NOT a replacement.
     */
    static final String EXECUTOR_STEP_PREFIX = """
        You are executing ONE step of a multi-step plan. Focus only on the
        step goal below. Do not start new work outside it. When you finish,
        end with a concise summary suitable for a human reader.
        """;

    /**
     * Builds the user message for an executor pass. Includes the step goal
     * plus the concatenated outputs of every dependency, so the executor
     * has the context it needs without seeing the full plan.
     */
    static String executorUserMessage(Step step, java.util.Map<String, String> upstreamOutputs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Step goal: ").append(step.goal).append('\n');
        if (!upstreamOutputs.isEmpty()) {
            sb.append("\n--- Upstream results ---\n");
            for (java.util.Map.Entry<String, String> e : upstreamOutputs.entrySet()) {
                sb.append("[").append(e.getKey()).append("]\n");
                sb.append(e.getValue()).append("\n\n");
            }
        }
        return sb.toString();
    }
}
