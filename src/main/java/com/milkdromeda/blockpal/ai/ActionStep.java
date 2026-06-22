package com.milkdromeda.blockpal.ai;

import com.google.gson.JsonObject;

public record ActionStep(ActionType type, JsonObject params) {

    public enum ActionType {
        MOVE_TO, PLACE_BLOCK, BREAK_BLOCK, ATTACK_NEAREST,
        FOLLOW_PLAYER, LOOK_AT, CHAT, WAIT, COLLECT_ITEM, STOP,
        // Expanded vocabulary — lets the assistant do almost anything:
        RUN_COMMAND,   // execute a Minecraft command (/setblock, /fill, /give, ...)
        USE_BLOCK,     // activate a lever/button/door/trapdoor/gate (redstone, escape rooms)
        MINE_AREA,     // clear every block in a small box (digging, tunnels)
        JUMP,          // hop (parkour, get unstuck)
        SET_SNEAK      // crouch on/off
    }

    public boolean getBool(String key, boolean fallback) {
        return params.has(key) ? params.get(key).getAsBoolean() : fallback;
    }

    public int getInt(String key, int fallback) {
        return params.has(key) ? params.get(key).getAsInt() : fallback;
    }

    public double getDouble(String key, double fallback) {
        return params.has(key) ? params.get(key).getAsDouble() : fallback;
    }

    public String getString(String key, String fallback) {
        return params.has(key) ? params.get(key).getAsString() : fallback;
    }
}
