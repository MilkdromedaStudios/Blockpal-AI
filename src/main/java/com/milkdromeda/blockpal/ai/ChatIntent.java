package com.milkdromeda.blockpal.ai;

/**
 * The assistant's interpretation of a single chat message when "active
 * analysis" is on. Produced by the language model in
 * {@link HuggingFaceClient#classifyMessage}.
 *
 * @param directed whether the message was plausibly aimed at the assistant
 * @param action   one of: come, follow, stay, stop, locate, task, none
 * @param task     a cleaned, imperative task when {@code action == "task"}
 */
public record ChatIntent(boolean directed, String action, String task) {

    /** A neutral "do nothing" result, used for errors or unrelated chatter. */
    public static ChatIntent none() {
        return new ChatIntent(false, "none", "");
    }
}
