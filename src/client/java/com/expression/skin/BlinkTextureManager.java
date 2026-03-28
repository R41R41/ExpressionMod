package com.expression.skin;

import com.expression.ExpressionMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * まばたき・アイトラッキング用のスキンテクスチャを管理するクラス
 * ETFと同じ方式でスキンテクスチャ自体を切り替える
 * 
 * テクスチャフレーム構成:
 * - 目の状態: NORMAL(通常), HALF(半目), CLOSED(閉じ), WIDE(見開き)
 * - 目の位置: CENTER(中央), LEFT(左), RIGHT(右)
 * 
 * 組み合わせ:
 * f0: NORMAL + CENTER
 * f1: NORMAL + LEFT
 * f2: NORMAL + RIGHT
 * f3: HALF + CENTER
 * f4: HALF + LEFT
 * f5: HALF + RIGHT
 * f6: CLOSED + CENTER (睡眠/盲目用)
 * f7: WIDE + CENTER
 * f8: WIDE + LEFT
 * f9: WIDE + RIGHT
 */
public class BlinkTextureManager {

    private static final Map<Identifier, BlinkTextureData> skinCache = new ConcurrentHashMap<>();

    private static AbstractClientPlayer currentRenderingPlayer = null;

    private static final Map<UUID, PlayerBlinkState> playerBlinkStates = new ConcurrentHashMap<>();

    private static final float PITCH_DOWN_THRESHOLD = 25.0f;
    private static final float PITCH_UP_THRESHOLD = -20.0f;
    private static final float YAW_THRESHOLD = 5.0f;
    private static final float BODY_YAW_SPEED = 0.1f;

    public static class PlayerBlinkState {
        public long lastBlinkTime = 0;
        public long nextBlinkInterval = 3000;
        public boolean isBlinking = false;
        public long blinkStartTime = 0;
        public float trackedBodyYaw = 0;
        public boolean bodyYawInitialized = false;

        public PlayerBlinkState() {
            lastBlinkTime = System.currentTimeMillis();
            scheduleNextBlink();
        }

        public void scheduleNextBlink() {
            nextBlinkInterval = 3000 + (long) (Math.random() * 3000);
        }
    }

    public static void setCurrentRenderingPlayer(AbstractClientPlayer player) {
        currentRenderingPlayer = player;
    }

    public static void clearCurrentRenderingPlayer() {
        currentRenderingPlayer = null;
    }

    public static AbstractClientPlayer getCurrentRenderingPlayer() {
        return currentRenderingPlayer;
    }

    public enum EyeState {
        NORMAL(0),
        HALF(1),
        CLOSED(2),
        WIDE(3);

        public final int value;

        EyeState(int value) {
            this.value = value;
        }
    }

    public enum EyePosition {
        CENTER(0),
        LEFT(1),
        RIGHT(2);

        public final int value;

        EyePosition(int value) {
            this.value = value;
        }
    }

    public static int getFrameIndex(EyeState state, EyePosition position) {
        switch (state) {
            case NORMAL:
                return position.value;
            case HALF:
                return 3 + position.value;
            case CLOSED:
                return 6;
            case WIDE:
                return 7 + position.value;
            default:
                return 0;
        }
    }

    public static final int TOTAL_FRAMES = 10;

    private static final int CYAN_COLOR = 0xFF00FFFF;

    public static class EyeConfig {
        public final int srcX;
        public final int srcY;
        public final int eyeYInFace;
        public final boolean valid;
        public final int eyeWidth;
        public final boolean separateDirectionEyelash;

        public EyeConfig(int srcX, int srcY, int eyeYInFace, boolean valid) {
            this(srcX, srcY, eyeYInFace, valid, 2, false);
        }

        public EyeConfig(int srcX, int srcY, int eyeYInFace, boolean valid, int eyeWidth,
                boolean separateDirectionEyelash) {
            this.srcX = srcX;
            this.srcY = srcY;
            this.eyeYInFace = eyeYInFace;
            this.valid = valid;
            this.eyeWidth = eyeWidth;
            this.separateDirectionEyelash = separateDirectionEyelash;
        }

        public static EyeConfig invalid() {
            return new EyeConfig(0, 0, 0, false);
        }
    }

    public static class BlinkTextureData {
        public final Identifier originalSkin;
        public final Identifier[] frames;
        public final boolean hasBlinkFeature;
        public final EmoteManager.EmoteTextureData emoteData;

        public BlinkTextureData(Identifier originalSkin, Identifier[] frames, boolean hasBlinkFeature) {
            this(originalSkin, frames, hasBlinkFeature, null);
        }

        public BlinkTextureData(Identifier originalSkin, Identifier[] frames, boolean hasBlinkFeature,
                EmoteManager.EmoteTextureData emoteData) {
            this.originalSkin = originalSkin;
            this.frames = frames;
            this.hasBlinkFeature = hasBlinkFeature;
            this.emoteData = emoteData;
        }

        public boolean hasAnyEmote() {
            return emoteData != null && emoteData.hasAnyEmote();
        }
    }

    @Nullable
    public static BlinkTextureData getOrCreate(Identifier skinTexture) {
        BlinkTextureData cached = skinCache.get(skinTexture);
        if (cached != null) {
            return cached;
        }

        try {
            NativeImage skinImage = loadSkinImage(skinTexture);
            if (skinImage == null) {
                ExpressionMod.LOGGER.debug("[BlinkTextureManager] Could not load skin: " + skinTexture);
                return createNoFeature(skinTexture);
            }

            EyeConfig eyeConfig = checkMarkers(skinImage);

            if (!eyeConfig.valid) {
                skinImage.close();
                ExpressionMod.LOGGER.debug("[BlinkTextureManager] No valid markers in skin: " + skinTexture);
                return createNoFeature(skinTexture);
            }

            ExpressionMod.LOGGER.info("[BlinkTextureManager] Creating eye textures for: " + skinTexture +
                    " (src=" + eyeConfig.srcX + "," + eyeConfig.srcY + ", eyeY=" + eyeConfig.eyeYInFace + ")");

            Identifier[] frames = new Identifier[TOTAL_FRAMES];
            String baseName = "eye_" + skinTexture.getPath().hashCode() + "_" + System.currentTimeMillis() % 10000;

            for (EyeState state : EyeState.values()) {
                if (state == EyeState.CLOSED) {
                    int frameIndex = getFrameIndex(state, EyePosition.CENTER);
                    NativeImage frameSkin = createEyeFrame(skinImage, state, EyePosition.CENTER, eyeConfig);
                    if (frameSkin != null) {
                        Identifier frameId = Identifier.fromNamespaceAndPath(ExpressionMod.MOD_ID, baseName + "_f" + frameIndex);
                        registerTexture(frameId, frameSkin);
                        frames[frameIndex] = frameId;
                        ExpressionMod.LOGGER.info("[BlinkTextureManager] Created " + state + "_CENTER: " + frameId);
                    }
                } else {
                    for (EyePosition position : EyePosition.values()) {
                        int frameIndex = getFrameIndex(state, position);
                        NativeImage frameSkin = createEyeFrame(skinImage, state, position, eyeConfig);
                        if (frameSkin != null) {
                            Identifier frameId = Identifier.fromNamespaceAndPath(ExpressionMod.MOD_ID, baseName + "_f" + frameIndex);
                            registerTexture(frameId, frameSkin);
                            frames[frameIndex] = frameId;
                            ExpressionMod.LOGGER
                                    .info("[BlinkTextureManager] Created " + state + "_" + position + ": " + frameId);
                        }
                    }
                }
            }

            EmoteManager.EmoteTextureData emoteData = EmoteManager.getOrCreate(skinTexture, skinImage);

            skinImage.close();

            BlinkTextureData data = new BlinkTextureData(skinTexture, frames, true, emoteData);
            skinCache.put(skinTexture, data);
            return data;

        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[BlinkTextureManager] Error creating eye textures", e);
            return createNoFeature(skinTexture);
        }
    }

    private static BlinkTextureData createNoFeature(Identifier skinTexture) {
        BlinkTextureData data = new BlinkTextureData(skinTexture, null, false);
        skinCache.put(skinTexture, data);
        return data;
    }

    /**
     * マーカーをチェックして目の設定を取得
     * getPixel(x, y) - マーカーはy=0行に横並び
     */
    private static EyeConfig checkMarkers(NativeImage skin) {
        if (skin.getHeight() < 64 || skin.getWidth() < 64) {
            return EyeConfig.invalid();
        }

        int marker0 = skin.getPixel(0, 0);
        if (isCyan(marker0)) {
            ExpressionMod.LOGGER
                    .info("[BlinkTextureManager] Found marker at [0,0] - standard format (tex=56-63,0-7, eyeWidth=2)");
            return new EyeConfig(56, 0, 4, true, 2, false);
        }

        int marker1 = skin.getPixel(1, 0);
        if (isCyan(marker1)) {
            ExpressionMod.LOGGER
                    .info("[BlinkTextureManager] Found marker at [1,0] - alternate format (tex=56-63,0-7, eyeWidth=2, eyeY=3)");
            return new EyeConfig(56, 0, 3, true, 2, false);
        }

        int marker2 = skin.getPixel(2, 0);
        if (isCyan(marker2)) {
            ExpressionMod.LOGGER
                    .info("[BlinkTextureManager] Found marker at [2,0] - single-width eye format (tex=56-63,16-25, eyeWidth=1)");
            return new EyeConfig(56, 16, 4, true, 1, true);
        }

        ExpressionMod.LOGGER
                .debug("[BlinkTextureManager] No cyan marker found at [0,0], [1,0], or [2,0]");
        return EyeConfig.invalid();
    }

    private static boolean isCyan(int color) {
        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        return alpha > 200 && red < 20 && green > 230 && blue > 230;
    }

    @Nullable
    private static NativeImage createEyeFrame(NativeImage originalSkin, EyeState state, EyePosition position,
            EyeConfig config) {
        try {
            NativeImage newSkin = new NativeImage(originalSkin.getWidth(), originalSkin.getHeight(), false);
            newSkin.copyFrom(originalSkin);

            applyEyeToFace(newSkin, originalSkin, 8, 8, config.eyeYInFace, state, position, config);

            return newSkin;
        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[BlinkTextureManager] Error creating eye frame", e);
            return null;
        }
    }

    private static void applyEyeToFace(NativeImage dest, NativeImage src,
            int faceX, int faceY, int eyeY, EyeState state, EyePosition position, EyeConfig config) {

        int srcBaseX = config.srcX;
        int srcBaseY = config.srcY;

        boolean isSingleWidth = (config.eyeWidth == 1);

        int eyeSrcY;
        int eyelashSrcY;

        if (isSingleWidth) {
            switch (position) {
                case RIGHT:
                    eyelashSrcY = srcBaseY + 4;
                    eyeSrcY = srcBaseY + 5;
                    break;
                case LEFT:
                    eyelashSrcY = srcBaseY + 7;
                    eyeSrcY = srcBaseY + 8;
                    break;
                default:
                    eyelashSrcY = srcBaseY;
                    eyeSrcY = srcBaseY + 1;
                    break;
            }
        } else {
            eyelashSrcY = srcBaseY;
            switch (position) {
                case RIGHT:
                    eyeSrcY = srcBaseY + 4;
                    break;
                case LEFT:
                    eyeSrcY = srcBaseY + 6;
                    break;
                default:
                    eyeSrcY = srcBaseY + 1;
                    break;
            }
        }

        int eyelashYOffset = 0;
        int skinCoverPixels = 0;
        boolean expandUp = false;

        switch (state) {
            case NORMAL:
                break;
            case HALF:
                eyelashYOffset = 1;
                skinCoverPixels = 1;
                break;
            case CLOSED:
                eyelashYOffset = 2;
                skinCoverPixels = 2;
                break;
            case WIDE:
                eyelashYOffset = -1;
                expandUp = true;
                break;
        }

        int[] eyeXPositions = isSingleWidth ? new int[] { 1, 2, 5, 6 } : new int[] { 1, 2, 5, 6 };

        for (int xi = 0; xi < eyeXPositions.length; xi++) {
            int x = eyeXPositions[xi];
            int srcX = srcBaseX + x;

            int eyelashColor = src.getPixel(srcX, eyelashSrcY);
            int eyelashAlpha = (eyelashColor >> 24) & 0xFF;
            if (eyelashAlpha == 0)
                continue;

            int skinColor = src.getPixel(srcX, srcBaseY + 3);

            int eye1Color = src.getPixel(srcX, eyeSrcY);
            int eye2Color = src.getPixel(srcX, eyeSrcY + 1);

            int destX = faceX + x;

            if (expandUp) {
                dest.setPixel(destX, faceY + eyeY, eye1Color);
                dest.setPixel(destX, faceY + eyeY + 1, eye1Color);
                dest.setPixel(destX, faceY + eyeY + 2, eye2Color);
            }

            int eyelashDestY = faceY + eyeY + eyelashYOffset;
            if (eyelashDestY >= faceY && eyelashDestY < faceY + 8) {
                dest.setPixel(destX, eyelashDestY, eyelashColor);
            }

            for (int dy = 0; dy < skinCoverPixels; dy++) {
                int destY = faceY + eyeY + dy;
                if (destY >= faceY && destY < faceY + 8) {
                    dest.setPixel(destX, destY, skinColor);
                }
            }

            if (skinCoverPixels < 1 && !expandUp) {
                dest.setPixel(destX, faceY + eyeY + 1, eye1Color);
            }

            if (skinCoverPixels < 2 && !expandUp) {
                dest.setPixel(destX, faceY + eyeY + 2, eye2Color);
            }
        }
    }

    @Nullable
    private static NativeImage loadSkinImage(Identifier skinTexture) {
        try {
            var client = Minecraft.getInstance();
            var textureManager = client.getTextureManager();
            var texture = textureManager.getTexture(skinTexture);

            if (texture instanceof DynamicTexture nativeTexture) {
                NativeImage pixels = nativeTexture.getPixels();
                if (pixels != null) {
                    NativeImage copy = new NativeImage(pixels.getWidth(), pixels.getHeight(), false);
                    copy.copyFrom(pixels);
                    return copy;
                }
            }

            var resourceManager = client.getResourceManager();
            var resource = resourceManager.getResource(skinTexture);
            if (resource.isPresent()) {
                return NativeImage.read(resource.get().open());
            }
        } catch (Exception e) {
            ExpressionMod.LOGGER.debug("[BlinkTextureManager] Could not load skin: " + skinTexture, e);
        }
        return null;
    }

    private static void registerTexture(Identifier id, NativeImage image) {
        try {
            DynamicTexture texture = new DynamicTexture(() -> "eye_frame", image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[BlinkTextureManager] Failed to register texture: " + id, e);
            image.close();
        }
    }

    private static int lastFrameIndex = -1;

    @Nullable
    public static Identifier getCurrentEyeTexture(Identifier originalSkin) {
        BlinkTextureData data = skinCache.get(originalSkin);
        if (data == null) {
            data = getOrCreate(originalSkin);
        }

        if (data == null) {
            return null;
        }

        AbstractClientPlayer player = currentRenderingPlayer;

        if (player != null && data.emoteData != null && data.emoteData.hasAnyEmote()) {
            int emoteIndex = EmoteManager.getCurrentEmote(player.getUUID());
            if (emoteIndex >= 0 && emoteIndex < EmoteManager.MAX_EMOTES) {
                if (data.emoteData.emoteEnabled[emoteIndex] && data.emoteData.emoteTextures[emoteIndex] != null) {
                    return data.emoteData.emoteTextures[emoteIndex];
                } else {
                    ExpressionMod.LOGGER.warn("[BlinkTextureManager] Emote " + emoteIndex + " texture missing: enabled=" +
                            data.emoteData.emoteEnabled[emoteIndex] + ", texture=" + data.emoteData.emoteTextures[emoteIndex]);
                }
            }
        } else if (player != null && EmoteManager.isEmoting(player.getUUID())) {
            ExpressionMod.LOGGER.warn("[BlinkTextureManager] Emote active but check failed: player=" +
                    (player != null ? player.getName().getString() : "null") +
                    ", emoteData=" + (data.emoteData != null ? "exists(count=" + data.emoteData.emoteCount + ")" : "null") +
                    ", skin=" + originalSkin);
        }

        if (!data.hasBlinkFeature || data.frames == null) {
            return null;
        }

        EyeState state;
        EyePosition position;

        if (player != null) {
            PlayerEyeResult result = calculatePlayerEyeState(player);
            state = result.state;
            position = result.position;
        } else {
            state = EyeStateController.getCurrentState();
            position = EyeStateController.getCurrentPosition();
        }

        int frameIndex = getFrameIndex(state, position);

        if (frameIndex != lastFrameIndex) {
            ExpressionMod.LOGGER.info("[BlinkTextureManager] Frame changed: " + lastFrameIndex + " -> " + frameIndex
                    + " (state=" + state + ", position=" + position + ")");
            lastFrameIndex = frameIndex;
        }

        if (frameIndex < 0 || frameIndex >= data.frames.length || data.frames[frameIndex] == null) {
            ExpressionMod.LOGGER.warn("[BlinkTextureManager] Invalid frame index: " + frameIndex);
            return null;
        }

        return data.frames[frameIndex];
    }

    public static class PlayerEyeResult {
        public final EyeState state;
        public final EyePosition position;

        public PlayerEyeResult(EyeState state, EyePosition position) {
            this.state = state;
            this.position = position;
        }
    }

    private static PlayerEyeResult calculatePlayerEyeState(AbstractClientPlayer player) {
        UUID playerId = player.getUUID();
        long currentTime = System.currentTimeMillis();

        PlayerBlinkState blinkState = playerBlinkStates.computeIfAbsent(playerId, k -> new PlayerBlinkState());

        EyeState state = EyeState.NORMAL;
        EyePosition position = EyePosition.CENTER;

        if (player.getPose() == Pose.SLEEPING || player.hasEffect(MobEffects.BLINDNESS)) {
            return new PlayerEyeResult(EyeState.CLOSED, EyePosition.CENTER);
        }

        if (blinkState.isBlinking) {
            long blinkElapsed = currentTime - blinkState.blinkStartTime;
            long blinkDuration = 150;

            if (blinkElapsed >= blinkDuration) {
                blinkState.isBlinking = false;
                blinkState.lastBlinkTime = currentTime;
                blinkState.scheduleNextBlink();
            } else {
                float progress = (float) blinkElapsed / blinkDuration;
                if (progress < 0.3f) {
                    state = EyeState.HALF;
                } else if (progress < 0.7f) {
                    state = EyeState.CLOSED;
                } else {
                    state = EyeState.HALF;
                }
            }
        } else {
            if (currentTime - blinkState.lastBlinkTime >= blinkState.nextBlinkInterval) {
                blinkState.isBlinking = true;
                blinkState.blinkStartTime = currentTime;
            }
        }

        float headYaw = player.getYHeadRot();
        float headPitch = player.getXRot();
        float bodyYaw = player.yBodyRot;

        if (!blinkState.bodyYawInitialized) {
            blinkState.trackedBodyYaw = headYaw;
            blinkState.bodyYawInitialized = true;
        } else {
            float diff = headYaw - blinkState.trackedBodyYaw;
            while (diff > 180)
                diff -= 360;
            while (diff < -180)
                diff += 360;
            if (Math.abs(diff) > 45) {
                blinkState.trackedBodyYaw += diff * 0.3f;
            } else {
                blinkState.trackedBodyYaw += diff * BODY_YAW_SPEED;
            }
        }

        float relativeYaw = headYaw - blinkState.trackedBodyYaw;
        while (relativeYaw > 180)
            relativeYaw -= 360;
        while (relativeYaw < -180)
            relativeYaw += 360;

        if (relativeYaw > YAW_THRESHOLD) {
            position = EyePosition.RIGHT;
        } else if (relativeYaw < -YAW_THRESHOLD) {
            position = EyePosition.LEFT;
        }

        if (!blinkState.isBlinking) {
            if (headPitch > PITCH_DOWN_THRESHOLD) {
                state = EyeState.HALF;
            } else if (headPitch < PITCH_UP_THRESHOLD) {
                state = EyeState.WIDE;
            }
        }

        return new PlayerEyeResult(state, position);
    }

    public static void clearCache() {
        skinCache.clear();
        playerBlinkStates.clear();
        ExpressionMod.LOGGER.info("[BlinkTextureManager] Cache cleared");
    }
}
