package com.expression.skin;

import com.expression.ExpressionMod;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
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

    public static final int MAX_EMOTES = 5;

    public static final long EMOTE_DURATION_MS = 2000;

    private static final int CYAN_COLOR = 0xFF00FFFF;

    private static final Map<Identifier, EmoteTextureData> emoteCache = new ConcurrentHashMap<>();

    private static final Map<UUID, EmoteState> playerEmoteStates = new ConcurrentHashMap<>();

    public static class EmoteConfig {
        public final int index;
        public final int srcX;
        public final int srcY;
        public final boolean valid;

        public EmoteConfig(int index, int srcX, int srcY, boolean valid) {
            this.index = index;
            this.srcX = srcX;
            this.srcY = srcY;
            this.valid = valid;
        }
    }

    public static class EmoteTextureData {
        public final Identifier originalSkin;
        public final Identifier[] emoteTextures;
        public final boolean[] emoteEnabled;
        public final EmoteConfig[] emoteConfigs;
        public final int emoteCount;

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

    public static class EmoteState {
        public int currentEmote = -1;
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

    @Nullable
    public static EmoteTextureData getOrCreate(Identifier skinTexture, NativeImage skinImage) {
        EmoteTextureData cached = emoteCache.get(skinTexture);
        if (cached != null) {
            return cached;
        }

        try {
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
                    Identifier emoteId = Identifier.fromNamespaceAndPath(ExpressionMod.MOD_ID, baseName + "_e" + config.index);
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

    private static List<EmoteConfig> checkEmoteMarkers(NativeImage skin) {
        List<EmoteConfig> configs = new ArrayList<>();

        int[][] emoteMarkers = {
                { 0, 1, 24, 0 },
                { 1, 1, 32, 0 },
                { 2, 1, 56, 32 },
                { 3, 1, 56, 37 },
                { 4, 1, 56, 42 },
        };

        ExpressionMod.LOGGER
                .info("[EmoteManager] Checking emote markers in skin " + skin.getWidth() + "x" + skin.getHeight());

        for (int i = 0; i < emoteMarkers.length; i++) {
            int markerX = emoteMarkers[i][0];
            int markerY = emoteMarkers[i][1];
            int srcX = emoteMarkers[i][2];
            int srcY = emoteMarkers[i][3];

            int markerColor = skin.getPixel(markerX, markerY);
            int alpha = (markerColor >> 24) & 0xFF;
            int red = (markerColor >> 16) & 0xFF;
            int green = (markerColor >> 8) & 0xFF;
            int blue = markerColor & 0xFF;

            ExpressionMod.LOGGER.info("[EmoteManager] Marker " + i + " at [x=" + markerX + ",y=" + markerY +
                    "]: color=0x" + Integer.toHexString(markerColor) + " (A=" + alpha + ",R=" + red + ",G=" + green
                    + ",B=" + blue +
                    "), isCyan=" + isCyan(markerColor));

            if (isCyan(markerColor)) {
                configs.add(new EmoteConfig(i, srcX, srcY, true));
                ExpressionMod.LOGGER.info("[EmoteManager] Found emote marker " + i +
                        " at [x=" + markerX + ",y=" + markerY + "] -> texture [" + srcX + "-" + (srcX + 7) + ", " + srcY
                        + "-" + (srcY + 4) + "]");
            }
        }

        ExpressionMod.LOGGER.info("[EmoteManager] Total emotes found: " + configs.size());
        return configs;
    }

    private static boolean isCyan(int color) {
        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return alpha > 200 && red < 20 && green > 230 && blue > 230;
    }

    @Nullable
    private static NativeImage createEmoteFrame(NativeImage originalSkin, EmoteConfig config) {
        try {
            NativeImage newSkin = new NativeImage(originalSkin.getWidth(), originalSkin.getHeight(), false);
            newSkin.copyFrom(originalSkin);

            int faceX = 8;
            int faceY = 8;

            for (int dx = 0; dx < 8; dx++) {
                for (int dy = 0; dy < 5; dy++) {
                    int srcX = config.srcX + dx;
                    int srcY = config.srcY + dy;
                    int destX = faceX + dx;
                    int destY = faceY + 3 + dy;

                    if (destY < faceY + 8) {
                        int color = originalSkin.getPixel(srcX, srcY);
                        int alpha = (color >> 24) & 0xFF;
                        if (alpha > 0) {
                            newSkin.setPixel(destX, destY, color);
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

    private static void registerTexture(Identifier id, NativeImage image) {
        try {
            DynamicTexture texture = new DynamicTexture(() -> "emote_frame", image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[EmoteManager] Failed to register texture: " + id, e);
            image.close();
        }
    }

    public static EmoteState getPlayerEmoteState(UUID playerId) {
        return playerEmoteStates.computeIfAbsent(playerId, k -> new EmoteState());
    }

    public static void startEmote(UUID playerId, int emoteIndex) {
        EmoteState state = getPlayerEmoteState(playerId);
        state.startEmote(emoteIndex);
        ExpressionMod.LOGGER.info("[EmoteManager] Player " + playerId + " started emote " + emoteIndex);
    }

    public static void stopEmote(UUID playerId) {
        EmoteState state = getPlayerEmoteState(playerId);
        state.stopEmote();
    }

    public static boolean isEmoting(UUID playerId) {
        EmoteState state = playerEmoteStates.get(playerId);
        return state != null && state.isEmoting();
    }

    public static int getCurrentEmote(UUID playerId) {
        EmoteState state = playerEmoteStates.get(playerId);
        if (state != null && state.isEmoting()) {
            return state.currentEmote;
        }
        return -1;
    }

    public static void clearCache() {
        emoteCache.clear();
        playerEmoteStates.clear();
        ExpressionMod.LOGGER.info("[EmoteManager] Cache cleared");
    }

    @Nullable
    public static NativeImage getEmotePreviewImage(NativeImage skinImage, int emoteIndex) {
        List<EmoteConfig> configs = checkEmoteMarkers(skinImage);
        for (EmoteConfig config : configs) {
            if (config.index == emoteIndex) {
                try {
                    NativeImage preview = new NativeImage(8, 5, false);
                    for (int x = 0; x < 8; x++) {
                        for (int y = 0; y < 5; y++) {
                            int color = skinImage.getPixel(config.srcX + x, config.srcY + y);
                            preview.setPixel(x, y, color);
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
