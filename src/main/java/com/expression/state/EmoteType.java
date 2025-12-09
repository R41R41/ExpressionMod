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
    SHY("shy", "恥じらい", 6);

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
