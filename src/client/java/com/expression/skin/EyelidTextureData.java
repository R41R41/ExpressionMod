package com.expression.skin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import com.expression.ExpressionMod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * まぶた（まつ毛）アニメーション用のテクスチャデータ
 * 
 * スキンの (0,0)-(7,7) 領域から以下を抽出：
 * - まつ毛テクスチャ (8x1)
 * - 目テクスチャ (8x2)
 * - 肌色テクスチャ (8x1)
 */
public class EyelidTextureData {

    // プレイヤーごとのデータをキャッシュ
    private static final Map<UUID, EyelidTextureData> cache = new ConcurrentHashMap<>();

    // スキンテクスチャ → UUID の逆引きマップ
    private static final Map<Identifier, UUID> skinToUuidMap = new ConcurrentHashMap<>();

    // このプレイヤーのUUID
    public final UUID playerUuid;

    // ExpressionMod機能が有効か
    public final boolean hasFeatures;

    // 目のY位置（顔内での位置、1-8）
    public final int eyeYPosition;

    // 抽出したテクスチャ
    public final Identifier eyelashTexture; // まつ毛 (8x1)
    public final Identifier eyeTexture; // 目 (8x2)
    public final Identifier skinTexture; // 肌色 (8x1)

    // 元のスキンテクスチャ
    public final Identifier originalSkin;

    private EyelidTextureData(UUID playerUuid, boolean hasFeatures, int eyeYPosition,
            Identifier eyelashTexture, Identifier eyeTexture,
            Identifier skinTexture, Identifier originalSkin) {
        this.playerUuid = playerUuid;
        this.hasFeatures = hasFeatures;
        this.eyeYPosition = eyeYPosition;
        this.eyelashTexture = eyelashTexture;
        this.eyeTexture = eyeTexture;
        this.skinTexture = skinTexture;
        this.originalSkin = originalSkin;
    }

    /**
     * プレイヤーのスキンからまぶたデータを抽出
     * マーカーの有無に関係なく、常に機能を有効にする
     */
    @Nullable
    public static EyelidTextureData getOrCreate(UUID playerUuid, Identifier skinTextureId) {
        // キャッシュチェック
        EyelidTextureData cached = cache.get(playerUuid);
        if (cached != null && cached.originalSkin.equals(skinTextureId)) {
            return cached;
        }

        ExpressionMod.LOGGER.info("[EyelidTextureData] Processing skin for player: " + playerUuid);
        ExpressionMod.LOGGER.info("[EyelidTextureData] Skin texture ID: " + skinTextureId);

        try {
            // スキン画像を読み込み
            NativeImage skinImage = loadSkinImage(skinTextureId);
            if (skinImage == null) {
                ExpressionMod.LOGGER.warn("[EyelidTextureData] Could not load skin image!");
                return createWithFeatures(playerUuid, skinTextureId, null);
            }

            ExpressionMod.LOGGER.info("[EyelidTextureData] Skin image loaded: " +
                    skinImage.getWidth() + "x" + skinImage.getHeight());

            // マーカーをチェック（ログ用、無視して続行）
            int markerColor = skinImage.getColorArgb(SkinRegions.MARKER_X, SkinRegions.MARKER_Y);
            boolean hasMarker = SkinRegions.isMarkerColor(markerColor);
            ExpressionMod.LOGGER.info("[EyelidTextureData] Marker at (7,7): " +
                    String.format("0x%08X", markerColor) + ", isMarker: " + hasMarker);

            // 目のY位置を取得
            int positionColor = skinImage.getColorArgb(SkinRegions.EYE_POSITION_X, SkinRegions.EYE_POSITION_Y);
            int eyeYPosition = SkinRegions.colorToNumber(positionColor);
            if (eyeYPosition < 1 || eyeYPosition > 8) {
                eyeYPosition = SkinRegions.DEFAULT_EYE_Y_IN_FACE;
            }
            ExpressionMod.LOGGER.info("[EyelidTextureData] Eye Y position: " + eyeYPosition);

            String baseId = "expr_" + playerUuid.toString().replace("-", "").substring(0, 8) +
                    "_" + System.currentTimeMillis() % 10000;

            // まつ毛テクスチャを抽出 (8x1)
            NativeImage eyelashImage = extractRegion(skinImage,
                    SkinRegions.EYELASH_X, SkinRegions.EYELASH_Y,
                    SkinRegions.EYELASH_WIDTH, SkinRegions.EYELASH_HEIGHT);
            Identifier eyelashTexture = registerTexture(baseId + "_eyelash", eyelashImage);
            ExpressionMod.LOGGER.info("[EyelidTextureData] Eyelash texture: " + eyelashTexture);

            // 目テクスチャを抽出 (8x2)
            NativeImage eyeImage = extractRegion(skinImage,
                    SkinRegions.EYE_X, SkinRegions.EYE_Y,
                    SkinRegions.EYE_WIDTH, SkinRegions.EYE_HEIGHT);
            Identifier eyeTexture = registerTexture(baseId + "_eye", eyeImage);
            ExpressionMod.LOGGER.info("[EyelidTextureData] Eye texture: " + eyeTexture);

            // 肌色テクスチャを抽出 (8x1)
            NativeImage skinColorImage = extractRegion(skinImage,
                    SkinRegions.SKIN_X, SkinRegions.SKIN_Y,
                    SkinRegions.SKIN_WIDTH, SkinRegions.SKIN_HEIGHT);
            Identifier skinColorTexture = registerTexture(baseId + "_skin", skinColorImage);
            ExpressionMod.LOGGER.info("[EyelidTextureData] Skin texture: " + skinColorTexture);

            skinImage.close();

            EyelidTextureData data = new EyelidTextureData(
                    playerUuid, true, eyeYPosition,
                    eyelashTexture, eyeTexture, skinColorTexture, skinTextureId);
            cache.put(playerUuid, data);
            skinToUuidMap.put(skinTextureId, playerUuid);

            ExpressionMod.LOGGER.info("[EyelidTextureData] Successfully created data for player: " + playerUuid);
            return data;

        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[EyelidTextureData] Error processing skin for player: " + playerUuid, e);
            return createWithFeatures(playerUuid, skinTextureId, null);
        }
    }

    /**
     * 機能ありのデータを作成（テクスチャがなくても）
     */
    private static EyelidTextureData createWithFeatures(UUID playerUuid, Identifier skinTexture,
            NativeImage skinImage) {
        EyelidTextureData data = new EyelidTextureData(
                playerUuid, true, SkinRegions.DEFAULT_EYE_Y_IN_FACE,
                null, null, null, skinTexture);
        cache.put(playerUuid, data);
        skinToUuidMap.put(skinTexture, playerUuid);
        ExpressionMod.LOGGER.info("[EyelidTextureData] Created data with features (no textures) for: " + playerUuid);
        return data;
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

            ExpressionMod.LOGGER.info("[EyelidTextureData] Texture type: " +
                    (texture != null ? texture.getClass().getSimpleName() : "null"));

            if (texture instanceof NativeImageBackedTexture nativeTexture) {
                NativeImage pixels = nativeTexture.getImage();
                if (pixels != null) {
                    NativeImage copy = new NativeImage(pixels.getWidth(), pixels.getHeight(), false);
                    copy.copyFrom(pixels);
                    ExpressionMod.LOGGER.info("[EyelidTextureData] Loaded from NativeImageBackedTexture");
                    return copy;
                }
            }

            var resourceManager = client.getResourceManager();
            var resource = resourceManager.getResource(skinTexture);
            if (resource.isPresent()) {
                ExpressionMod.LOGGER.info("[EyelidTextureData] Loaded from ResourceManager");
                return NativeImage.read(resource.get().getInputStream());
            }

            ExpressionMod.LOGGER.warn("[EyelidTextureData] Could not load texture: " + skinTexture);
        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[EyelidTextureData] Exception loading skin: " + skinTexture, e);
        }
        return null;
    }

    /**
     * 領域を抽出
     */
    private static NativeImage extractRegion(NativeImage source, int x, int y, int width, int height) {
        NativeImage result = new NativeImage(width, height, false);
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < height; dy++) {
                int color = source.getColorArgb(x + dx, y + dy);
                result.setColorArgb(dx, dy, color);
            }
        }
        return result;
    }

    /**
     * テクスチャを登録
     */
    @Nullable
    private static Identifier registerTexture(String name, NativeImage image) {
        try {
            Identifier id = Identifier.of(ExpressionMod.MOD_ID, name);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "expressionmod_eyelid", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            return id;
        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[EyelidTextureData] Failed to register texture: " + name, e);
            image.close();
            return null;
        }
    }

    /**
     * キャッシュをクリア
     */
    public static void clearCache() {
        cache.clear();
    }

    /**
     * 特定プレイヤーのキャッシュをクリア
     */
    public static void clearCache(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    /**
     * キャッシュからデータを取得
     */
    @Nullable
    public static EyelidTextureData get(UUID playerUuid) {
        return cache.get(playerUuid);
    }

    /**
     * スキンテクスチャからデータを取得
     */
    @Nullable
    public static EyelidTextureData getBySkinTexture(Identifier skinTexture) {
        UUID uuid = skinToUuidMap.get(skinTexture);
        if (uuid != null) {
            return cache.get(uuid);
        }
        return null;
    }

    /**
     * キャッシュをクリア（逆引きマップも含む）
     */
    public static void clearAllCaches() {
        ExpressionMod.LOGGER.info("[EyelidTextureData] Clearing all caches");
        cache.clear();
        skinToUuidMap.clear();
    }
}
