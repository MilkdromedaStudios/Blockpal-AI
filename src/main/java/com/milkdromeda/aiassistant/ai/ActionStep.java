package com.milkdromeda.aiassistant.ai;

import com.google.gson.JsonObject;

public record ActionStep(ActionType type, JsonObject params) {

    public enum ActionType {
        MOVE_TO,
        PLACE_BLOCK,
        BREAK_BLOCK,
        ATTACK_NEAREST,
        FOLLOW_PLAYER,
        LOOK_AT,
        CHAT,
        WAIT,
        COLLECT_ITEM,
        STOP
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
