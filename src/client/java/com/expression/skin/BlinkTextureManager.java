package com.expression.skin;

import com.expression.ExpressionMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
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

    // プレイヤーごとのデータをキャッシュ
    private static final Map<Identifier, BlinkTextureData> skinCache = new ConcurrentHashMap<>();

    // 現在レンダリング中のプレイヤー（スレッドローカル）
    private static AbstractClientPlayerEntity currentRenderingPlayer = null;

    // プレイヤーごとのまばたき状態
    private static final Map<UUID, PlayerBlinkState> playerBlinkStates = new ConcurrentHashMap<>();

    // しきい値
    private static final float PITCH_DOWN_THRESHOLD = 25.0f;
    private static final float PITCH_UP_THRESHOLD = -20.0f;
    private static final float YAW_THRESHOLD = 5.0f;
    private static final float BODY_YAW_SPEED = 0.1f;

    /**
     * プレイヤーごとのまばたき状態
     */
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

    /**
     * 現在レンダリング中のプレイヤーを設定
     */
    public static void setCurrentRenderingPlayer(AbstractClientPlayerEntity player) {
        currentRenderingPlayer = player;
    }

    /**
     * 現在レンダリング中のプレイヤーをクリア
     */
    public static void clearCurrentRenderingPlayer() {
        currentRenderingPlayer = null;
    }

    /**
     * 現在レンダリング中のプレイヤーを取得
     */
    public static AbstractClientPlayerEntity getCurrentRenderingPlayer() {
        return currentRenderingPlayer;
    }

    // 目の状態
    public enum EyeState {
        NORMAL(0), // 通常
        HALF(1), // 半目（まばたき中/下向き）
        CLOSED(2), // 閉じ（睡眠/盲目）
        WIDE(3); // 見開き（上向き）

        public final int value;

        EyeState(int value) {
            this.value = value;
        }
    }

    // 目の位置
    public enum EyePosition {
        CENTER(0), // 中央
        LEFT(1), // 左向き
        RIGHT(2); // 右向き

        public final int value;

        EyePosition(int value) {
            this.value = value;
        }
    }

    // フレームインデックス計算
    // NORMAL: 0-2, HALF: 3-5, CLOSED: 6, WIDE: 7-9
    public static int getFrameIndex(EyeState state, EyePosition position) {
        switch (state) {
            case NORMAL:
                return position.value; // 0, 1, 2
            case HALF:
                return 3 + position.value; // 3, 4, 5
            case CLOSED:
                return 6; // 6のみ
            case WIDE:
                return 7 + position.value; // 7, 8, 9
            default:
                return 0;
        }
    }

    // フレーム総数
    public static final int TOTAL_FRAMES = 10;

    // シアン色 (#00FFFF) - ARGB形式
    private static final int CYAN_COLOR = 0xFF00FFFF;

    /**
     * 目のテクスチャ設定
     */
    public static class EyeConfig {
        public final int srcX; // ソース領域の開始X
        public final int srcY; // ソース領域の開始Y
        public final int eyeYInFace; // 顔内での目のY位置
        public final boolean valid;
        public final int eyeWidth; // 目の幅（1または2）
        public final boolean separateDirectionEyelash; // 方向別にまつ毛が分かれているか

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

    /**
     * まばたきテクスチャデータ
     */
    public static class BlinkTextureData {
        public final Identifier originalSkin;
        public final Identifier[] frames; // 10フレーム
        public final boolean hasBlinkFeature;
        public final EmoteManager.EmoteTextureData emoteData; // エモートデータ

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

    /**
     * スキンテクスチャからまばたきテクスチャを取得または作成
     */
    @Nullable
    public static BlinkTextureData getOrCreate(Identifier skinTexture) {
        // キャッシュチェック
        BlinkTextureData cached = skinCache.get(skinTexture);
        if (cached != null) {
            return cached;
        }

        try {
            // スキン画像を読み込み
            NativeImage skinImage = loadSkinImage(skinTexture);
            if (skinImage == null) {
                ExpressionMod.LOGGER.debug("[BlinkTextureManager] Could not load skin: " + skinTexture);
                return createNoFeature(skinTexture);
            }

            // マーカーをチェックして目の設定を取得
            EyeConfig eyeConfig = checkMarkers(skinImage);

            if (!eyeConfig.valid) {
                skinImage.close();
                ExpressionMod.LOGGER.debug("[BlinkTextureManager] No valid markers in skin: " + skinTexture);
                return createNoFeature(skinTexture);
            }

            ExpressionMod.LOGGER.info("[BlinkTextureManager] Creating eye textures for: " + skinTexture +
                    " (src=" + eyeConfig.srcX + "," + eyeConfig.srcY + ", eyeY=" + eyeConfig.eyeYInFace + ")");

            // 全フレームを生成
            Identifier[] frames = new Identifier[TOTAL_FRAMES];
            String baseName = "eye_" + skinTexture.getPath().hashCode() + "_" + System.currentTimeMillis() % 10000;

            for (EyeState state : EyeState.values()) {
                if (state == EyeState.CLOSED) {
                    // CLOSEDは中央のみ
                    int frameIndex = getFrameIndex(state, EyePosition.CENTER);
                    NativeImage frameSkin = createEyeFrame(skinImage, state, EyePosition.CENTER, eyeConfig);
                    if (frameSkin != null) {
                        Identifier frameId = Identifier.of(ExpressionMod.MOD_ID, baseName + "_f" + frameIndex);
                        registerTexture(frameId, frameSkin);
                        frames[frameIndex] = frameId;
                        ExpressionMod.LOGGER.info("[BlinkTextureManager] Created " + state + "_CENTER: " + frameId);
                    }
                } else {
                    // NORMAL, HALF, WIDEは3方向
                    for (EyePosition position : EyePosition.values()) {
                        int frameIndex = getFrameIndex(state, position);
                        NativeImage frameSkin = createEyeFrame(skinImage, state, position, eyeConfig);
                        if (frameSkin != null) {
                            Identifier frameId = Identifier.of(ExpressionMod.MOD_ID, baseName + "_f" + frameIndex);
                            registerTexture(frameId, frameSkin);
                            frames[frameIndex] = frameId;
                            ExpressionMod.LOGGER
                                    .info("[BlinkTextureManager] Created " + state + "_" + position + ": " + frameId);
                        }
                    }
                }
            }

            // エモートデータを作成（skinImageを閉じる前に）
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

    /**
     * 機能なしデータを作成
     */
    private static BlinkTextureData createNoFeature(Identifier skinTexture) {
        BlinkTextureData data = new BlinkTextureData(skinTexture, null, false);
        skinCache.put(skinTexture, data);
        return data;
    }

    /**
     * マーカーをチェックして目の設定を取得
     * getColorArgb(x, y) - マーカーはy=0行に横並び:
     * [0,0]にシアン → テクスチャ[24-31, 0-7]、eyeY=4（標準形式、目幅2マス）
     * [1,0]にシアン → テクスチャ[24-31, 0-7]、eyeY=3（1マス上、目幅2マス）
     * [2,0]にシアン → テクスチャ[56-63, 16-25]、eyeY=4（目幅1マス形式）
     * マーカーがない場合は無効
     */
    private static EyeConfig checkMarkers(NativeImage skin) {
        // スキンが64x64以上あるか確認
        if (skin.getHeight() < 64 || skin.getWidth() < 64) {
            return EyeConfig.invalid();
        }

        // [0,0]のマーカーをチェック（標準形式、目幅2マス）
        int marker0 = skin.getColorArgb(0, 0);
        if (isCyan(marker0)) {
            ExpressionMod.LOGGER
                    .info("[BlinkTextureManager] Found marker at [0,0] - standard format (tex=24-31,0-7, eyeWidth=2)");
            return new EyeConfig(24, 0, 4, true, 2, false);
        }

        // [1,0]のマーカーをチェック（描画位置1マス上、目幅2マス）
        int marker1 = skin.getColorArgb(1, 0);
        if (isCyan(marker1)) {
            ExpressionMod.LOGGER
                    .info("[BlinkTextureManager] Found marker at [1,0] - alternate format (tex=24-31,0-7, eyeWidth=2, eyeY=3)");
            return new EyeConfig(24, 0, 3, true, 2, false);
        }

        // [2,0]のマーカーをチェック（目幅1マス形式）
        int marker2 = skin.getColorArgb(2, 0);
        if (isCyan(marker2)) {
            ExpressionMod.LOGGER
                    .info("[BlinkTextureManager] Found marker at [2,0] - single-width eye format (tex=56-63,16-25, eyeWidth=1)");
            return new EyeConfig(56, 16, 4, true, 1, true);
        }

        // マーカーがない場合は無効（MODを発動しない）
        ExpressionMod.LOGGER
                .debug("[BlinkTextureManager] No cyan marker found at [0,0], [1,0], or [2,0]");
        return EyeConfig.invalid();
    }

    /**
     * シアン色かどうか判定 (#00FFFF)
     */
    private static boolean isCyan(int color) {
        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        // アルファが不透明で、R=0, G=255, B=255（シアン）
        return alpha > 200 && red < 20 && green > 230 && blue > 230;
    }

    /**
     * 目のフレームを作成
     */
    @Nullable
    private static NativeImage createEyeFrame(NativeImage originalSkin, EyeState state, EyePosition position,
            EyeConfig config) {
        try {
            NativeImage newSkin = new NativeImage(originalSkin.getWidth(), originalSkin.getHeight(), false);
            newSkin.copyFrom(originalSkin);

            // 顔のベースレイヤー (8,8)-(15,15) の目の領域を更新
            applyEyeToFace(newSkin, originalSkin, 8, 8, config.eyeYInFace, state, position, config);

            return newSkin;
        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[BlinkTextureManager] Error creating eye frame", e);
            return null;
        }
    }

    /**
     * 顔に目の状態を適用
     * 
     * 標準形式（[0,63]マーカー）: スキンの(0,0)-(7,7)から、目幅2マス:
     * Row 0: まつ毛（共通）
     * Row 1-2: 目（中央向き）
     * Row 3: 肌色（共通）
     * Row 4-5: 目（右向き）
     * Row 6-7: 目（左向き）
     * 
     * 代替形式（[1,63]マーカー）: スキンの(24,0)-(31,7)から、目幅2マス:
     * 構造は標準形式と同じ
     * 
     * 目幅1マス形式（[2,63]マーカー）: スキンの(56,16)-(63,25)から:
     * Row 0: まつ毛（中央向き）
     * Row 1-2: 目（中央向き）
     * Row 3: 肌色（共通）
     * Row 4: まつ毛（右向き）
     * Row 5-6: 目（右向き）
     * Row 7: まつ毛（左向き）
     * Row 8-9: 目（左向き）
     * 
     * @param state    目の状態
     * @param position 目の位置（CENTER/LEFT/RIGHT）
     * @param config   目のテクスチャ設定
     */
    private static void applyEyeToFace(NativeImage dest, NativeImage src,
            int faceX, int faceY, int eyeY, EyeState state, EyePosition position, EyeConfig config) {

        // ソース領域の開始位置
        int srcBaseX = config.srcX;
        int srcBaseY = config.srcY;

        // 目幅1マス形式かどうか
        boolean isSingleWidth = (config.eyeWidth == 1);

        // 目のテクスチャの開始Y座標とまつ毛のY座標（positionに応じて異なる）
        int eyeSrcY;
        int eyelashSrcY;

        if (isSingleWidth) {
            // 目幅1マス形式: 方向別にまつ毛も分かれている
            switch (position) {
                case RIGHT:
                    eyelashSrcY = srcBaseY + 4; // Row 4: まつ毛（右向き）
                    eyeSrcY = srcBaseY + 5; // Row 5-6: 目（右向き）
                    break;
                case LEFT:
                    eyelashSrcY = srcBaseY + 7; // Row 7: まつ毛（左向き）
                    eyeSrcY = srcBaseY + 8; // Row 8-9: 目（左向き）
                    break;
                default: // CENTER
                    eyelashSrcY = srcBaseY; // Row 0: まつ毛（中央向き）
                    eyeSrcY = srcBaseY + 1; // Row 1-2: 目（中央向き）
                    break;
            }
        } else {
            // 目幅2マス形式: まつ毛は共通
            eyelashSrcY = srcBaseY; // Row 0: まつ毛（共通）
            switch (position) {
                case RIGHT:
                    eyeSrcY = srcBaseY + 4; // Row 4-5（相対）
                    break;
                case LEFT:
                    eyeSrcY = srcBaseY + 6; // Row 6-7（相対）
                    break;
                default: // CENTER
                    eyeSrcY = srcBaseY + 1; // Row 1-2（相対）
                    break;
            }
        }

        // まつ毛のY方向オフセット
        int eyelashYOffset = 0;
        // 肌色で覆うピクセル数（上から）
        int skinCoverPixels = 0;
        // 見開き（上に拡大）
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

        // 目の位置を決定（目幅1マスの場合は x=1-2, x=5-6 → 顔のx=57-58, x=61-62）
        int[] eyeXPositions = isSingleWidth ? new int[] { 1, 2, 5, 6 } : new int[] { 1, 2, 5, 6 };

        for (int xi = 0; xi < eyeXPositions.length; xi++) {
            int x = eyeXPositions[xi];
            int srcX = srcBaseX + x;

            // まつ毛の色を取得
            int eyelashColor = src.getColorArgb(srcX, eyelashSrcY);
            int eyelashAlpha = (eyelashColor >> 24) & 0xFF;
            if (eyelashAlpha == 0)
                continue;

            // 肌色を取得（Row 3、共通）
            int skinColor = src.getColorArgb(srcX, srcBaseY + 3);

            // 目の色を取得（positionに応じたRowから）
            int eye1Color = src.getColorArgb(srcX, eyeSrcY); // 目上段
            int eye2Color = src.getColorArgb(srcX, eyeSrcY + 1); // 目下段

            int destX = faceX + x;

            // 見開き: 目を上に1px拡大（3マスになる）
            if (expandUp) {
                // Y=eyeY-1: まつ毛（後で描画）
                // Y=eyeY: 目の上段（拡大分）
                // Y=eyeY+1: 目の上段
                // Y=eyeY+2: 目の下段
                dest.setColorArgb(destX, faceY + eyeY, eye1Color); // 上に追加
                dest.setColorArgb(destX, faceY + eyeY + 1, eye1Color); // 目の上段
                dest.setColorArgb(destX, faceY + eyeY + 2, eye2Color); // 目の下段
            }

            // まつ毛を描画
            int eyelashDestY = faceY + eyeY + eyelashYOffset;
            if (eyelashDestY >= faceY && eyelashDestY < faceY + 8) {
                dest.setColorArgb(destX, eyelashDestY, eyelashColor);
            }

            // 肌色で覆う（半目/閉じ）
            for (int dy = 0; dy < skinCoverPixels; dy++) {
                int destY = faceY + eyeY + dy;
                if (destY >= faceY && destY < faceY + 8) {
                    dest.setColorArgb(destX, destY, skinColor);
                }
            }

            // 目の上段を描画（通常時のみ、半目/見開きでは描画しない）
            if (skinCoverPixels < 1 && !expandUp) {
                dest.setColorArgb(destX, faceY + eyeY + 1, eye1Color);
            }

            // 目の下段を描画（通常時のみ、閉じ/見開きでは描画しない）
            if (skinCoverPixels < 2 && !expandUp) {
                dest.setColorArgb(destX, faceY + eyeY + 2, eye2Color);
            }
        }
    }

    /**
     * スキン画像を読み込み
     */
    @Nullable
    private static NativeImage loadSkinImage(Identifier skinTexture) {
        try {
            var client = MinecraftClient.getInstance();
            var textureManager = client.getTextureManager();
            var texture = textureManager.getTexture(skinTexture);

            if (texture instanceof NativeImageBackedTexture nativeTexture) {
                NativeImage pixels = nativeTexture.getImage();
                if (pixels != null) {
                    NativeImage copy = new NativeImage(pixels.getWidth(), pixels.getHeight(), false);
                    copy.copyFrom(pixels);
                    return copy;
                }
            }

            var resourceManager = client.getResourceManager();
            var resource = resourceManager.getResource(skinTexture);
            if (resource.isPresent()) {
                return NativeImage.read(resource.get().getInputStream());
            }
        } catch (Exception e) {
            ExpressionMod.LOGGER.debug("[BlinkTextureManager] Could not load skin: " + skinTexture, e);
        }
        return null;
    }

    /**
     * テクスチャを登録
     */
    private static void registerTexture(Identifier id, NativeImage image) {
        try {
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[BlinkTextureManager] Failed to register texture: " + id, e);
            image.close();
        }
    }

    // デバッグ用：前回のフレームインデックス
    private static int lastFrameIndex = -1;

    /**
     * 現在の目の状態に応じたテクスチャを取得
     * 
     * @return 目のテクスチャ、またはnull（元のスキンを使用）
     */
    @Nullable
    public static Identifier getCurrentEyeTexture(Identifier originalSkin) {
        BlinkTextureData data = skinCache.get(originalSkin);
        if (data == null) {
            data = getOrCreate(originalSkin);
        }

        if (data == null) {
            return null;
        }

        // 現在レンダリング中のプレイヤーを取得
        AbstractClientPlayerEntity player = currentRenderingPlayer;

        // エモート中かどうかチェック
        if (player != null && data.emoteData != null && data.emoteData.hasAnyEmote()) {
            int emoteIndex = EmoteManager.getCurrentEmote(player.getUuid());
            if (emoteIndex >= 0 && emoteIndex < EmoteManager.MAX_EMOTES) {
                if (data.emoteData.emoteEnabled[emoteIndex] && data.emoteData.emoteTextures[emoteIndex] != null) {
                    return data.emoteData.emoteTextures[emoteIndex];
                }
            }
        }

        // まばたき機能がない場合は元のスキンを使用
        if (!data.hasBlinkFeature || data.frames == null) {
            return null;
        }

        // 現在レンダリング中のプレイヤーの状態を計算
        EyeState state;
        EyePosition position;

        if (player != null) {
            // そのプレイヤー固有の状態を計算
            PlayerEyeResult result = calculatePlayerEyeState(player);
            state = result.state;
            position = result.position;
        } else {
            // フォールバック: グローバルな状態を使用
            state = EyeStateController.getCurrentState();
            position = EyeStateController.getCurrentPosition();
        }

        int frameIndex = getFrameIndex(state, position);

        // フレームが変わった時にログ
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

    /**
     * プレイヤーの目の状態を計算した結果
     */
    public static class PlayerEyeResult {
        public final EyeState state;
        public final EyePosition position;

        public PlayerEyeResult(EyeState state, EyePosition position) {
            this.state = state;
            this.position = position;
        }
    }

    /**
     * プレイヤーごとの目の状態を計算
     */
    private static PlayerEyeResult calculatePlayerEyeState(AbstractClientPlayerEntity player) {
        UUID playerId = player.getUuid();
        long currentTime = System.currentTimeMillis();

        // プレイヤーのまばたき状態を取得または作成
        PlayerBlinkState blinkState = playerBlinkStates.computeIfAbsent(playerId, k -> new PlayerBlinkState());

        EyeState state = EyeState.NORMAL;
        EyePosition position = EyePosition.CENTER;

        // 睡眠/盲目チェック
        if (player.getPose() == EntityPose.SLEEPING || player.hasStatusEffect(StatusEffects.BLINDNESS)) {
            return new PlayerEyeResult(EyeState.CLOSED, EyePosition.CENTER);
        }

        // まばたき処理
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
            // まばたき開始チェック
            if (currentTime - blinkState.lastBlinkTime >= blinkState.nextBlinkInterval) {
                blinkState.isBlinking = true;
                blinkState.blinkStartTime = currentTime;
            }
        }

        // アイトラッキング（まばたき中でなければ上下を計算）
        float headYaw = player.getHeadYaw();
        float headPitch = player.getPitch();
        float bodyYaw = player.getBodyYaw();

        // 体のYawを追跡
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

        // 左右の位置
        if (relativeYaw > YAW_THRESHOLD) {
            position = EyePosition.RIGHT;
        } else if (relativeYaw < -YAW_THRESHOLD) {
            position = EyePosition.LEFT;
        }

        // 上下の状態（まばたき中でなければ）
        if (!blinkState.isBlinking) {
            if (headPitch > PITCH_DOWN_THRESHOLD) {
                state = EyeState.HALF;
            } else if (headPitch < PITCH_UP_THRESHOLD) {
                state = EyeState.WIDE;
            }
        }

        return new PlayerEyeResult(state, position);
    }

    /**
     * キャッシュをクリア
     */
    public static void clearCache() {
        skinCache.clear();
        playerBlinkStates.clear();
        ExpressionMod.LOGGER.info("[BlinkTextureManager] Cache cleared");
    }
}
