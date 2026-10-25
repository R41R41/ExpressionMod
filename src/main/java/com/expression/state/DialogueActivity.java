package com.expression.state;

/** Body-local conversation events, independent of the mind's emotion. */
public enum DialogueActivity {
    IDLE("idle"), LISTENING("listening"), THINKING("thinking"), SPEAKING("speaking");
    private final String id;
    DialogueActivity(String id) { this.id = id; }
    public String id() { return id; }
    public static DialogueActivity fromId(String id) {
        for (var value : values()) if (value.id.equals(id)) return value;
        return IDLE;
    }
}
