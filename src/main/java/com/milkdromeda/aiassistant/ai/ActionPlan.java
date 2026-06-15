package com.milkdromeda.aiassistant.ai;

import java.util.List;
import java.util.Queue;
import java.util.LinkedList;

public class ActionPlan {
    public final String thinking;
    public final String description;
    private final Queue<ActionStep> steps;

    public ActionPlan(String thinking, String description, List<ActionStep> steps) {
        this.thinking = thinking;
        this.description = description;
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
