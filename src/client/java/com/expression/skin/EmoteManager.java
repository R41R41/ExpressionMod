package com.expression.skin;

import com.expression.ExpressionMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * エモートテクスチャを管理するクラス
 * 
 * エモートマーカー（x=1列）:
 * [y=0, x=1] → [24-31, 0-4] エモート1
 * [y=1, x=1] → [32-39, 0-4] エモート2
 * [y=2, x=1] → [56-63, 32-36] エモート3
 * [y=3, x=1] → [56-63, 37-41] エモート4
 * [y=4, x=1] → [56-63, 42-46] エモート5
 */
public class EmoteManager {

    // エモートの数
    public static final int MAX_EMOTES = 5;

    // エモートの持続時間（ミリ秒）
    public static final long EMOTE_DURATION_MS = 2000;

    // シアン色 (#00FFFF) - ARGB形式
    private static final int CYAN_COLOR = 0xFF00FFFF;

    // スキンごとのエモートデータをキャッシュ
    private static final Map<Identifier, EmoteTextureData> emoteCache = new ConcurrentHashMap<>();

    // プレイヤーごとのエモート状態
    private static final Map<UUID, EmoteState> playerEmoteStates = new ConcurrentHashMap<>();

    /**
     * エモートの設定
     */
    public static class EmoteConfig {
        public final int index; // 0-4
        public final int srcX; // ソース領域の開始X
        public final int srcY; // ソース領域の開始Y
        public final boolean valid;

        public EmoteConfig(int index, int srcX, int srcY, boolean valid) {
            this.index = index;
            this.srcX = srcX;
            this.srcY = srcY;
            this.valid = valid;
        }
    }

    /**
     * エモートテクスチャデータ
     */
    public static class EmoteTextureData {
        public final Identifier originalSkin;
        public final Identifier[] emoteTextures; // 最大5つのエモートテクスチャ
        public final boolean[] emoteEnabled; // 各エモートが有効か
        public final EmoteConfig[] emoteConfigs; // 各エモートの設定
        public final int emoteCount; // 有効なエモートの数

        public EmoteTextureData(Identifier originalSkin, Identifier[] emoteTextures,
                boolean[] emoteEnabled, EmoteConfig[] emoteConfigs) {
            this.originalSkin = originalSkin;
            this.emoteTextures = emoteTextures;
            this.emoteEnabled = emoteEnabled;
            this.emoteConfigs = emoteConfigs;

            int count = 0;
            for (boolean enabled : emoteEnabled) {
                if (enabled)
                    count++;
            }
            this.emoteCount = count;
        }

        public boolean hasAnyEmote() {
            return emoteCount > 0;
        }
    }

    /**
     * プレイヤーのエモート状態
     */
    public static class EmoteState {
        public int currentEmote = -1; // -1 = エモートなし、0-4 = エモート中
        public long emoteStartTime = 0;

        public boolean isEmoting() {
            if (currentEmote < 0)
                return false;
            long elapsed = System.currentTimeMillis() - emoteStartTime;
            if (elapsed >= EMOTE_DURATION_MS) {
                currentEmote = -1;
                return false;
            }
            return true;
        }

        public void startEmote(int emoteIndex) {
            currentEmote = emoteIndex;
            emoteStartTime = System.currentTimeMillis();
        }

        public void stopEmote() {
            currentEmote = -1;
        }
    }

    /**
     * エモートテクスチャデータを取得または作成
     */
    @Nullable
    public static EmoteTextureData getOrCreate(Identifier skinTexture, NativeImage skinImage) {
        // キャッシュチェック
        EmoteTextureData cached = emoteCache.get(skinTexture);
        if (cached != null) {
            return cached;
        }

        try {
            // エモートマーカーをチェック
            List<EmoteConfig> configs = checkEmoteMarkers(skinImage);

            if (configs.isEmpty()) {
                EmoteTextureData data = new EmoteTextureData(skinTexture,
                        new Identifier[MAX_EMOTES], new boolean[MAX_EMOTES], new EmoteConfig[MAX_EMOTES]);
                emoteCache.put(skinTexture, data);
                return data;
            }

            Identifier[] emoteTextures = new Identifier[MAX_EMOTES];
            boolean[] emoteEnabled = new boolean[MAX_EMOTES];
            EmoteConfig[] emoteConfigArray = new EmoteConfig[MAX_EMOTES];

            String baseName = "emote_" + skinTexture.getPath().hashCode() + "_" + System.currentTimeMillis() % 10000;

            for (EmoteConfig config : configs) {
                NativeImage emoteFrame = createEmoteFrame(skinImage, config);
                if (emoteFrame != null) {
                    Identifier emoteId = Identifier.of(ExpressionMod.MOD_ID, baseName + "_e" + config.index);
                    registerTexture(emoteId, emoteFrame);
                    emoteTextures[config.index] = emoteId;
                    emoteEnabled[config.index] = true;
                    emoteConfigArray[config.index] = config;
                    ExpressionMod.LOGGER.info("[EmoteManager] Created emote " + config.index + ": " + emoteId);
                }
            }

            EmoteTextureData data = new EmoteTextureData(skinTexture, emoteTextures, emoteEnabled, emoteConfigArray);
            emoteCache.put(skinTexture, data);
            return data;

        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[EmoteManager] Error creating emote textures", e);
            return null;
        }
    }

    /**
     * エモートマーカーをチェック
     */
    private static List<EmoteConfig> checkEmoteMarkers(NativeImage skin) {
        List<EmoteConfig> configs = new ArrayList<>();

        // エモートマーカーの定義
        // [y=0, x=1] → [24-31, 0-4]
        // [y=1, x=1] → [32-39, 0-4]
        // [y=2, x=1] → [56-63, 32-36]
        // [y=3, x=1] → [56-63, 37-41]
        // [y=4, x=1] → [56-63, 42-46]
        int[][] emoteMarkers = {
                { 0, 1, 24, 0 }, // エモート0: マーカー[y=0,x=1], テクスチャ[24-31, 0-4]
                { 1, 1, 32, 0 }, // エモート1: マーカー[y=1,x=1], テクスチャ[32-39, 0-4]
                { 2, 1, 56, 32 }, // エモート2: マーカー[y=2,x=1], テクスチャ[56-63, 32-36]
                { 3, 1, 56, 37 }, // エモート3: マーカー[y=3,x=1], テクスチャ[56-63, 37-41]
                { 4, 1, 56, 42 }, // エモート4: マーカー[y=4,x=1], テクスチャ[56-63, 42-46]
        };

        ExpressionMod.LOGGER
                .info("[EmoteManager] Checking emote markers in skin " + skin.getWidth() + "x" + skin.getHeight());

        for (int i = 0; i < emoteMarkers.length; i++) {
            int markerY = emoteMarkers[i][0];
            int markerX = emoteMarkers[i][1];
            int srcX = emoteMarkers[i][2];
            int srcY = emoteMarkers[i][3];

            int markerColor = skin.getColorArgb(markerX, markerY);
            int alpha = (markerColor >> 24) & 0xFF;
            int red = (markerColor >> 16) & 0xFF;
            int green = (markerColor >> 8) & 0xFF;
            int blue = markerColor & 0xFF;

            ExpressionMod.LOGGER.info("[EmoteManager] Marker " + i + " at [y=" + markerY + ",x=" + markerX +
                    "]: color=0x" + Integer.toHexString(markerColor) + " (A=" + alpha + ",R=" + red + ",G=" + green
                    + ",B=" + blue +
                    "), isCyan=" + isCyan(markerColor));

            if (isCyan(markerColor)) {
                configs.add(new EmoteConfig(i, srcX, srcY, true));
                ExpressionMod.LOGGER.info("[EmoteManager] Found emote marker " + i +
                        " at [y=" + markerY + ",x=" + markerX + "] -> texture [" + srcX + "-" + (srcX + 7) + ", " + srcY
                        + "-" + (srcY + 4) + "]");
            }
        }

        ExpressionMod.LOGGER.info("[EmoteManager] Total emotes found: " + configs.size());
        return configs;
    }

    /**
     * シアン色かどうか判定
     */
    private static boolean isCyan(int color) {
        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return alpha > 200 && red < 20 && green > 230 && blue > 230;
    }

    /**
     * エモートフレームを作成
     * 8x5ピクセルの領域を顔に適用
     */
    @Nullable
    private static NativeImage createEmoteFrame(NativeImage originalSkin, EmoteConfig config) {
        try {
            NativeImage newSkin = new NativeImage(originalSkin.getWidth(), originalSkin.getHeight(), false);
            newSkin.copyFrom(originalSkin);

            // エモート領域（8x5）を顔のベースレイヤー（8,8）-(15,15）の目の位置に適用
            // 顔の上部5ピクセル（y=8-12）に適用
            int faceX = 8;
            int faceY = 8;

            // エモート領域の目の位置（x=1-2: 左目, x=5-6: 右目）のみを置換
            int[] eyeXPositions = { 1, 2, 5, 6 };

            for (int x : eyeXPositions) {
                for (int dy = 0; dy < 5; dy++) {
                    int srcX = config.srcX + x;
                    int srcY = config.srcY + dy;
                    int destX = faceX + x;
                    int destY = faceY + 3 + dy; // Y=3から開始（目の位置）

                    if (destY < faceY + 8) { // 顔の範囲内
                        int color = originalSkin.getColorArgb(srcX, srcY);
                        int alpha = (color >> 24) & 0xFF;
                        if (alpha > 0) {
                            newSkin.setColorArgb(destX, destY, color);
                        }
                    }
                }
            }

            return newSkin;
        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[EmoteManager] Error creating emote frame", e);
            return null;
        }
    }

    /**
     * テクスチャを登録
     */
    private static void registerTexture(Identifier id, NativeImage image) {
        try {
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[EmoteManager] Failed to register texture: " + id, e);
            image.close();
        }
    }

    /**
     * プレイヤーのエモート状態を取得
     */
    public static EmoteState getPlayerEmoteState(UUID playerId) {
        return playerEmoteStates.computeIfAbsent(playerId, k -> new EmoteState());
    }

    /**
     * エモートを開始
     */
    public static void startEmote(UUID playerId, int emoteIndex) {
        EmoteState state = getPlayerEmoteState(playerId);
        state.startEmote(emoteIndex);
        ExpressionMod.LOGGER.info("[EmoteManager] Player " + playerId + " started emote " + emoteIndex);
    }

    /**
     * エモートを停止
     */
    public static void stopEmote(UUID playerId) {
        EmoteState state = getPlayerEmoteState(playerId);
        state.stopEmote();
    }

    /**
     * プレイヤーがエモート中かどうか
     */
    public static boolean isEmoting(UUID playerId) {
        EmoteState state = playerEmoteStates.get(playerId);
        return state != null && state.isEmoting();
    }

    /**
     * 現在のエモートインデックスを取得（-1 = エモートなし）
     */
    public static int getCurrentEmote(UUID playerId) {
        EmoteState state = playerEmoteStates.get(playerId);
        if (state != null && state.isEmoting()) {
            return state.currentEmote;
        }
        return -1;
    }

    /**
     * キャッシュをクリア
     */
    public static void clearCache() {
        emoteCache.clear();
        playerEmoteStates.clear();
        ExpressionMod.LOGGER.info("[EmoteManager] Cache cleared");
    }

    /**
     * エモートプレビュー用の画像を取得
     */
    @Nullable
    public static NativeImage getEmotePreviewImage(NativeImage skinImage, int emoteIndex) {
        List<EmoteConfig> configs = checkEmoteMarkers(skinImage);
        for (EmoteConfig config : configs) {
            if (config.index == emoteIndex) {
                try {
                    // 8x5の領域を抽出
                    NativeImage preview = new NativeImage(8, 5, false);
                    for (int x = 0; x < 8; x++) {
                        for (int y = 0; y < 5; y++) {
                            int color = skinImage.getColorArgb(config.srcX + x, config.srcY + y);
                            preview.setColorArgb(x, y, color);
                        }
                    }
                    return preview;
                } catch (Exception e) {
                    ExpressionMod.LOGGER.error("[EmoteManager] Error getting emote preview", e);
                }
            }
        }
        return null;
    }
}
