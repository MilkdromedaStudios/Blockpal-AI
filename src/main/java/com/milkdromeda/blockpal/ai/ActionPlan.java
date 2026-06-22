package com.milkdromeda.blockpal.ai;

import java.util.List;
import java.util.Queue;
import java.util.LinkedList;

public class ActionPlan {
    public final String thinking;
    public final String description;
    /** When true, the activity is ongoing — re-plan and keep going after the steps run out. */
    public final boolean loop;
    private final Queue<ActionStep> steps;

    public ActionPlan(String thinking, String description, List<ActionStep> steps) {
        this(thinking, description, steps, false);
    }

    public ActionPlan(String thinking, String description, List<ActionStep> steps, boolean loop) {
        this.thinking = thinking;
        this.description = description;
        this.loop = loop;
        this.steps = new LinkedList<>(steps);
    }

    public ActionStep poll() {
        return steps.poll();
    }

    public ActionStep peek() {
        return steps.peek();
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public int remaining() {
        return steps.size();
    }
}
