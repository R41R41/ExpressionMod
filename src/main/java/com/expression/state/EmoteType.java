package com.expression.state;

/**
 * エモート（表情）の種類を定義するEnum
 */
public enum EmoteType {
    NEUTRAL("neutral", "通常顔", 0),
    SMILE("smile", "笑顔", 1),
    ANGRY("angry", "怒り顔", 2),
    SAD("sad", "悲しい顔", 3),
    SURPRISED("surprised", "驚き", 4),
    THINK("think", "考え込み顔", 5),
    SHY("shy", "恥じらい", 6),
    EXCITED("excited", "わくわく", 7),
    AMUSED("amused", "いたずら笑い", 8),
    LAUGHING("laughing", "大笑い", 9),
    BASHFUL("bashful", "はにかみ", 10),
    RELIEVED("relieved", "ほっとした顔", 11),
    CURIOUS("curious", "興味津々", 12),
    CONFUSED("confused", "戸惑い", 13),
    DETERMINED("determined", "決意", 14),
    TENDER("tender", "やさしい顔", 15),
    CALM("calm", "穏やか", 16),
    JEALOUS("jealous", "やきもち", 17),
    WRY("wry", "苦笑い", 18),
    EXASPERATED("exasperated", "あきれ顔", 19),
    CONCERNED("concerned", "心配", 20),
    SLEEPY("sleepy", "眠そう", 21);

    private final String id;
    private final String displayName;
    private final int textureIndex;

    EmoteType(String id, String displayName, int textureIndex) {
        this.id = id;
        this.displayName = displayName;
        this.textureIndex = textureIndex;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTextureIndex() {
        return textureIndex;
    }

    /**
     * IDからEmoteTypeを取得
     */
    public static EmoteType fromId(String id) {
        // The current body's mood.expression names. Keep all original packet IDs valid.
        if (id == null) return NEUTRAL;
        id = switch (id) {
            case "joyful" -> "smile";
            case "annoyed" -> "angry";
            case "thinking" -> "think";
            case "embarrassed" -> "shy";
            default -> id;
        };
        for (EmoteType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return NEUTRAL;
    }

    /**
     * インデックスからEmoteTypeを取得
     */
    public static EmoteType fromIndex(int index) {
        for (EmoteType type : values()) {
            if (type.textureIndex == index) {
                return type;
            }
        }
        return NEUTRAL;
    }
}
