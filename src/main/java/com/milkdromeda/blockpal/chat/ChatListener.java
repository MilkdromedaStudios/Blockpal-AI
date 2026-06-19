package com.milkdromeda.blockpal.chat;

import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.util.Locator;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Watches normal in-game chat so players can command the assistant with plain
 * language — e.g. "Ethan, follow me" or "help me mine this tree" — without
 * having to type /ai. Quick intents (come, follow, stay, stop, locate) are
 * handled instantly with no API call; anything else becomes an AI task.
 *
 * <p>Only the player who spawned the assistant (its owner) can give it orders.
 * Non-owners are politely turned away.
 *
 * <p>With "active analysis" on (the default), messages that aren't obviously
 * addressed are still handed to the language model, which decides whether the
 * player needs the assistant — so no name or exact command words are required.
 */
public final class ChatListener {

    private ChatListener() {}

    private static final Random RAND = new Random();

    /** First words that mark a chat message as a request aimed at the assistant. */
    private static final Set<String> TRIGGER_WORDS = Set.of(
            "help", "come", "follow", "stop", "stay", "wait", "guard", "defend",
            "mine", "dig", "chop", "build", "place", "break", "attack", "kill",
            "fight", "gather", "collect", "get", "bring", "make", "craft", "go",
            "move", "find", "hey", "locate", "where", "harvest", "farm", "plant"
    );

    public static void register() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            try {
                handle(sender, message.signedContent());
            } catch (Exception ignored) {
                // Never let chat parsing break the chat pipeline.
            }
        });
    }

    public static void handle(ServerPlayer sender, String raw) {
        if (raw == null || raw.isBlank()) return;
        if (com.milkdromeda.blockpal.EmergencyState.isDisabled()) return;
        if (!ModConfig.get().chatListening) return;

        AiAssistantEntity ai = AiAssistantEntity.findFor(sender, 128);
        if (ai == null) return; // No assistant around — stay quiet.

        String text = raw.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        String name = ai.getAssistantName().toLowerCase(Locale.ROOT);

        boolean addressedByName = lower.equals(name) || lower.startsWith(name + " ")
                || lower.startsWith(name + ",") || lower.startsWith(name + ":");

        // Owner-only enforcement: only the player who spawned this assistant can command it.
        // We still check if addressed by name so non-owners get a reply rather than silence.
        if (!ai.isOwner(sender)) {
            if (addressedByName) {
                String ownerName = ai.getOwnerPlayer() != null
                        ? ai.getOwnerPlayer().getName().getString() : "someone else";
                String[] dismissals = {
                    "Sorry, I only take orders from " + ownerName + ".",
                    "You're not my boss — " + ownerName + " is.",
                    "I answer to " + ownerName + ", not you.",
                    "I appreciate the enthusiasm, but " + ownerName + " is the one who gives me orders."
                };
                ai.broadcastMessage(dismissals[RAND.nextInt(dismissals.length)]);
            }
            return;
        }

        String body = text;
        if (addressedByName) {
            body = text.substring(name.length()).replaceFirst("^[,:\\s]+", "").trim();
            if (body.isEmpty()) {
                ai.broadcastMessage(pick(
                        "Yeah? What do you need?",
                        "What's up?",
                        "I'm listening.",
                        "You called?"));
                return;
            }
        }

        // Not obviously aimed at the assistant by name or a command word?
        if (!addressedByName && !startsWithTrigger(lower)) {
            // Active mode: let the language model judge whether you need it, so
            // you don't have to address it or use exact words. Quiet otherwise.
            // Skip trivial chatter and far-off players so we don't fire an API
            // call (and the analysis rate-limit) on every little message.
            if (ModConfig.get().activeMode && ModConfig.get().hasApiToken()
                    && text.length() >= 5
                    && ai.distanceToSqr(sender) < 48 * 48) {
                ai.analyzeChat(sender, text);
            }
            return;
        }

        if (handleQuickIntent(sender, ai, body.toLowerCase(Locale.ROOT))) return;

        // Anything else is a real task for the AI planner.
        if (!ModConfig.get().hasApiToken()) {
            sender.sendSystemMessage(Component.literal(
                    ai.getAssistantName() + ": \"I'd love to help, but I need an API token first. Use /ai token <token>.\""));
            return;
        }
        ai.giveTask(body, sender);
    }

    private static boolean startsWithTrigger(String lower) {
        int space = lower.indexOf(' ');
        String firstWord = space == -1 ? lower : lower.substring(0, space);
        return TRIGGER_WORDS.contains(firstWord);
    }

    /** Handles instant, no-API commands. Returns true if the message was consumed. */
    private static boolean handleQuickIntent(ServerPlayer player, AiAssistantEntity ai, String rawBody) {
        // Normalise for matching only: drop punctuation, collapse spaces.
        String body = rawBody.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();

        if (body.equals("help") || body.equals("help me") || body.equals("commands")) {
            player.sendSystemMessage(Component.literal(
                    ai.getAssistantName() + ": \"Sure! Try: \"follow me\", \"come here\", \"stay\", \"stop\", "
                            + "\"where are you\", \"do it yourself\", or just describe a task. Full list: /ai help\""));
            return true;
        }
        if (matches(body, "come", "come here", "come back", "over here", "to me", "here boy")) {
            ai.comeTo(player);
            return true;
        }
        if (matches(body, "follow", "follow me", "stay with me", "come with me")) {
            ai.followPlayer();
            return true;
        }
        if (matches(body, "stop", "stop it", "cancel", "halt", "nevermind", "never mind", "quit")) {
            ai.stopTask();
            return true;
        }
        if (matches(body, "stay", "stay here", "wait", "wait here", "hold", "hold position", "guard", "defend", "stand guard")) {
            ai.stayHere();
            return true;
        }
        if (matches(body, "where are you", "where r u", "where you at", "locate", "your location", "where")) {
            player.sendSystemMessage(Component.literal(ai.getAssistantName() + ": \"" + Locator.describe(player, ai) + "\""));
            return true;
        }
        if (matches(body,
                "do it yourself", "figure it out", "handle it", "handle it yourself",
                "use your judgment", "use your own judgment", "you decide",
                "go ahead", "be autonomous", "act on your own", "do your own thing",
                "you re on your own", "youre on your own", "you are on your own")) {
            ai.enterAutonomousMode();
            return true;
        }
        return false;
    }

    private static boolean matches(String body, String... options) {
        for (String option : options) {
            if (body.equals(option)) return true;
        }
        return false;
    }

    private static String pick(String... options) {
        return options[RAND.nextInt(options.length)];
    }
}
