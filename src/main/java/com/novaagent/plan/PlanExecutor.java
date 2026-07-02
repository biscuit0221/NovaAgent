package com.novaagent.plan;

import com.novaagent.agent.Agent;
import com.novaagent.llm.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Runs a {@link Plan} step by step in topological order.
 *
 * <p>Each step is executed by spinning up a <b>fresh</b> ReAct loop on the
 * shared {@link Agent} instance. The history is intentionally per-step
 * rather than shared: downstream steps receive the upstream outputs as a
 * compact text block inside their user message, not as a growing message
 * log. This is the "context isolation" property we want from plan-and-
 * execute: a misbehaving step cannot poison its successors' context.</p>
 *
 * <p>Failure policy for Phase 2: <b>fast-fail</b>. If a step's ReAct loop
 * returns an empty final answer (the agent did not produce any assistant
 * text), or throws, we mark the step {@link Step.Status#FAILED} and stop.
 * Subsequent steps are not run. A future phase can revisit this with
 * retry / skip-and-continue policies.</p>
 */
public final class PlanExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(PlanExecutor.class);

    private final Agent agent;

    public PlanExecutor(Agent agent) {
        if (agent == null) throw new IllegalArgumentException("agent is required");
        this.agent = agent;
    }

    /**
     * Executes the plan. Returns a per-step output map in execution order.
     * Steps that did not run (because an earlier step failed) are absent
     * from the map.
     */
    public Map<String, String> execute(Plan plan, Consumer<StepEvent> onEvent) {
        if (plan == null) throw new IllegalArgumentException("plan is required");
        Map<String, String> outputs = new LinkedHashMap<>();
        List<Step> ordered = plan.topoOrder();
        for (Step step : ordered) {
            step.markRunning();
            emit(onEvent, new StepEvent(StepEvent.Type.START, step, null));
            Map<String, String> upstream = new LinkedHashMap<>();
            for (String dep : step.dependsOn) {
                String v = outputs.get(dep);
                if (v != null) upstream.put(dep, v);
            }
            String userMsg = PlanPrompts.executorUserMessage(step, upstream);
            String finalAnswer;
            try {
                StringBuilder streamed = new StringBuilder();
                List<ChatMessage> history = agent.run(
                    userMsg,
                    PlanPrompts.EXECUTOR_STEP_PREFIX,
                    delta -> {
                        streamed.append(delta);
                        emit(onEvent, new StepEvent(StepEvent.Type.DELTA, step, delta));
                    });
                finalAnswer = extractFinalAnswer(history, streamed);
            } catch (RuntimeException ex) {
                LOG.error("step {} threw", step.id, ex);
                step.markFailed();
                emit(onEvent, new StepEvent(StepEvent.Type.FAIL, step, ex.getMessage()));
                return outputs;
            }
            if (finalAnswer.isBlank()) {
                step.markFailed();
                emit(onEvent, new StepEvent(StepEvent.Type.FAIL, step, "empty final answer"));
                return outputs;
            }
            step.markDone();
            outputs.put(step.id, finalAnswer);
            emit(onEvent, new StepEvent(StepEvent.Type.DONE, step, finalAnswer));
        }
        return outputs;
    }

    private static String extractFinalAnswer(List<ChatMessage> history, StringBuilder streamed) {
        StringBuilder finalAnswer = new StringBuilder();
        for (ChatMessage m : history) {
            if (ChatMessage.ROLE_ASSISTANT.equals(m.getRole())
                && m.getContent() instanceof String s
                && !s.isBlank()) {
                finalAnswer.append(s).append('\n');
            }
        }
        if (finalAnswer.length() == 0 && streamed.length() > 0) {
            return streamed.toString();
        }
        return finalAnswer.toString().trim();
    }

    private static void emit(Consumer<StepEvent> sink, StepEvent ev) {
        if (sink == null) return;
        try { sink.accept(ev); } catch (RuntimeException ignored) {}
    }

    /**
     * Progress event emitted while a plan is being executed. The CLI layer
     * is responsible for translating these into colored terminal output;
     * the executor itself does not depend on any UI library.
     */
    public static final class StepEvent {
        public enum Type { START, DELTA, DONE, FAIL }
        public final Type type;
        public final Step step;
        public final String payload; // delta text for DELTA, final answer for DONE, error message for FAIL
        public StepEvent(Type type, Step step, String payload) {
            this.type = type; this.step = step; this.payload = payload;
        }
    }
}
