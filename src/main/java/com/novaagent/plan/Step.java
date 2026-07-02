package com.novaagent.plan;

import java.util.List;

/**
 * A single unit of work inside a {@link Plan}.
 *
 * <p>Steps form a DAG: a step may depend on zero or more earlier steps via
 * {@link #dependsOn}. The plan executor walks the graph in topological order
 * and runs each step through a fresh ReAct loop. The execution context for
 * a step is built by concatenating the final outputs of all steps it depends
 * on, so downstream steps can refer to upstream results by description.</p>
 *
 * <p>This class is immutable. {@link #status} is mutable only because the
 * executor updates it as it runs; everything else is final.</p>
 */
public final class Step {

    public enum Status { PENDING, RUNNING, DONE, FAILED }

    public final String id;
    public final String title;
    public final String goal;
    public final List<String> dependsOn;

    private Status status;

    public Step(String id, String title, String goal, List<String> dependsOn) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("step id must not be blank");
        if (title == null) title = "";
        if (goal == null) goal = "";
        if (dependsOn == null) dependsOn = List.of();
        this.id = id;
        this.title = title;
        this.goal = goal;
        this.dependsOn = List.copyOf(dependsOn);
        this.status = Status.PENDING;
    }

    public Status status() { return status; }

    public void markRunning() { this.status = Status.RUNNING; }
    public void markDone() { this.status = Status.DONE; }
    public void markFailed() { this.status = Status.FAILED; }
}
