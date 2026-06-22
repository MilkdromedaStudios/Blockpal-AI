package com.milkdromeda.blockpal.util;

import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.world.entity.player.Player;

/** Builds a friendly, human-readable description of where the assistant is. */
public final class Locator {

    private Locator() {}

    public static String describe(Player player, AiAssistantEntity ai) {
        int x = (int) Math.round(ai.getX());
        int y = (int) Math.round(ai.getY());
        int z = (int) Math.round(ai.getZ());

        double dx = ai.getX() - player.getX();
        double dy = ai.getY() - player.getY();
        double dz = ai.getZ() - player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));

        if (distance <= 2) {
            return "I'm right next to you, at " + x + ", " + y + ", " + z + ".";
        }

        String direction = cardinal(dx, dz);
        String vertical = "";
        if (dy > 3) vertical = " and above you";
        else if (dy < -3) vertical = " and below you";

        return "I'm " + distance + " block" + (distance == 1 ? "" : "s") + " away to the "
                + direction + vertical + ", at " + x + ", " + y + ", " + z + ".";
    }

    /** 8-way compass direction from the player to the assistant (-Z is north). */
    private static String cardinal(double dx, double dz) {
        double threshold = 3.0;
        StringBuilder sb = new StringBuilder();
        if (dz < -threshold) sb.append("north");
        else if (dz > threshold) sb.append("south");
        if (dx > threshold) sb.append("east");
        else if (dx < -threshold) sb.append("west");
        return sb.length() == 0 ? "very close" : sb.toString();
    }
}
