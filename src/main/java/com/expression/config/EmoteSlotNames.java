package com.expression.config;

/**
 * Player-chosen names for the five skin-authored expression slots.
 * The slot order (and the wire IDs behind it) never changes; only the label shown to this player does,
 * because each skin artist paints a different face into each slot.
 */
public final class EmoteSlotNames {
    public static final int SLOTS = 5;
    public static final int MAX_LENGTH = 24;
    private static final String[] DEFAULTS = {"笑顔", "怒り", "悲しみ", "驚き", "考え中"};

    public static String[] defaults() { return DEFAULTS.clone(); }

    public static String defaultName(int slot) {
        return slot >= 0 && slot < SLOTS ? DEFAULTS[slot] : fallback(slot);
    }

    /** The label for one slot; a blank or missing entry falls back to "表情 N". */
    public static String name(String[] names, int slot) {
        if (names == null || slot < 0 || slot >= names.length || names[slot] == null) return fallback(slot);
        String trimmed = clean(names[slot]);
        return trimmed.isEmpty() ? fallback(slot) : trimmed;
    }

    /** Exactly five entries, trimmed and length-bounded; an old or hand-edited config may hold fewer or more. */
    public static String[] normalize(String[] names) {
        String[] out = new String[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++)
            out[slot] = names != null && slot < names.length && names[slot] != null ? clean(names[slot]) : DEFAULTS[slot];
        return out;
    }

    public static String clean(String value) {
        String trimmed = value == null ? "" : value.strip().replaceAll("\\p{Cntrl}", "");
        return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
    }

    private static String fallback(int slot) { return "表情 " + (slot + 1); }

    private EmoteSlotNames() {}
}
