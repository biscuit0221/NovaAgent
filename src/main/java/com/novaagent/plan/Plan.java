package com.novaagent.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Plan is an ordered list of {@link Step}s with explicit dependency edges.
 *
 * <p>Phase 2 treats a Plan as a DAG: each step declares which earlier step ids
 * it depends on. The executor relies on {@link #topoOrder()} to derive a runnable
 * sequence. For 95% of user tasks the dependency graph is a simple chain
 * (s1 -> s2 -> s3), but the structure supports a fan-out / fan-in DAG so we
 * don't have to re-shape it in a future "parallel steps" phase.</p>
 *
 * <p>Invariants enforced by {@link #validate()}:</p>
 * <ul>
 *   <li>Steps are not empty.</li>
 *   <li>Every id is unique.</li>
 *   <li>Every dependsOn entry points at an existing step id.</li>
 *   <li>No cycle exists. Root steps (no deps) must exist.</li>
 *   <li>Step ids are referenced by their literal string in
 *       {@link Step#dependsOn}; we don't try to be smart about ordering
 *       numerically ("s2" depends on "s10" is allowed).</li>
 * </ul>
 */
public final class Plan {

    public final String task;
    public final List<Step> steps;
    private final Map<String, Step> byId;

    public Plan(String task, List<Step> steps) {
        this.task = task == null ? "" : task;
        this.steps = steps == null ? List.of() : List.copyOf(steps);
        this.byId = new LinkedHashMap<>();
        for (Step s : this.steps) byId.put(s.id, s);
    }

    public Step step(String id) { return byId.get(id); }

    public Map<String, Step> index() { return Collections.unmodifiableMap(byId); }

    /**
     * Returns true if the dependency graph is a "real" DAG in the sense that
     * the dependency structure is not a strict linear chain. Specifically,
     * we say it's a DAG when at least one of the following holds:
     * <ul>
     *   <li>any step declares more than one dependency (fan-in), or</li>
     *   <li>any step's dependency is not its immediate predecessor in
     *       {@link #steps} (i.e. it skips over a step), or</li>
     *   <li>two steps share a common descendant (fan-out).</li>
     * </ul>
     * A pure chain (s1 -> s2 -> s3 -> ...) returns false. This is purely
     * informational, used for log messages; the executor does not care.
     */
    public boolean isDag() {
        if (steps.size() < 2) return false;
        Map<String, Integer> position = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) position.put(steps.get(i).id, i);
        for (Step s : steps) {
            if (s.dependsOn.size() != 1) return true;
            int depPos = position.getOrDefault(s.dependsOn.get(0), -1);
            int myPos = position.get(s.id);
            // a single dep that is NOT the immediate previous step => DAG
            if (depPos != myPos - 1) return true;
        }
        return false;
    }

    /**
     * Validates the plan against the invariants listed in the class javadoc.
     * Throws {@link IllegalArgumentException} with a human-readable message
     * on the first violation.
     */
    public void validate() {
        if (steps.isEmpty()) throw new IllegalArgumentException("plan has no steps");
        if (byId.size() != steps.size()) {
            throw new IllegalArgumentException("plan has duplicate step ids");
        }
        Set<String> ids = byId.keySet();
        for (Step s : steps) {
            for (String dep : s.dependsOn) {
                if (!ids.contains(dep)) {
                    throw new IllegalArgumentException(
                        "step " + s.id + " depends on unknown step '" + dep + "'");
                }
            }
        }
        // Cycle detection (Kahn-style: count in-degree after stripping unknown deps).
        Map<String, Integer> inDeg = new HashMap<>();
        Map<String, List<String>> out = new HashMap<>();
        for (Step s : steps) { inDeg.put(s.id, 0); out.put(s.id, new ArrayList<>()); }
        for (Step s : steps) {
            for (String dep : s.dependsOn) {
                inDeg.merge(s.id, 1, Integer::sum);
                out.get(dep).add(s.id);
            }
        }
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        for (Map.Entry<String, Integer> e : inDeg.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }
        if (queue.isEmpty()) {
            throw new IllegalArgumentException("plan has no root step (every step has dependencies)");
        }
        int visited = 0;
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            visited++;
            for (String nxt : out.get(id)) {
                int d = inDeg.merge(nxt, -1, Integer::sum);
                if (d == 0) queue.add(nxt);
            }
        }
        if (visited != steps.size()) {
            throw new IllegalArgumentException("plan contains a cycle");
        }
    }

    /**
     * Returns steps in topological order (roots first, dependents after their
     * dependencies). Uses Kahn's algorithm with a {@link LinkedHashSet}-backed
     * queue so steps sharing the same in-degree-drained moment are visited in
     * the order they appear in {@link #steps}, which keeps the output stable
     * for tests and for the executor's print log.
     */
    public List<Step> topoOrder() {
        validate();
        Map<String, Integer> inDeg = new HashMap<>();
        Map<String, List<String>> out = new HashMap<>();
        for (Step s : steps) { inDeg.put(s.id, 0); out.put(s.id, new ArrayList<>()); }
        for (Step s : steps) {
            for (String dep : s.dependsOn) {
                inDeg.merge(s.id, 1, Integer::sum);
                out.get(dep).add(s.id);
            }
        }
        java.util.ArrayDeque<String> ready = new java.util.ArrayDeque<>();
        for (Step s : steps) if (inDeg.get(s.id) == 0) ready.add(s.id);
        List<Step> ordered = new ArrayList<>(steps.size());
        Set<String> seen = new HashSet<>();
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            if (!seen.add(id)) continue;
            ordered.add(byId.get(id));
            for (String nxt : out.get(id)) {
                int d = inDeg.merge(nxt, -1, Integer::sum);
                if (d == 0) ready.add(nxt);
            }
        }
        return ordered;
    }

}
