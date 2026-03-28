package com.expression.skin;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
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

    private static final Map<UUID, EyelidTextureData> cache = new ConcurrentHashMap<>();

    private static final Map<Identifier, UUID> skinToUuidMap = new ConcurrentHashMap<>();

    public final UUID playerUuid;
    public final boolean hasFeatures;
    public final int eyeYPosition;

    public final Identifier eyelashTexture;
    public final Identifier eyeTexture;
    public final Identifier skinTexture;

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

    @Nullable
    public static EyelidTextureData getOrCreate(UUID playerUuid, Identifier skinTextureId) {
        EyelidTextureData cached = cache.get(playerUuid);
        if (cached != null && cached.originalSkin.equals(skinTextureId)) {
            return cached;
        }

        ExpressionMod.LOGGER.info("[EyelidTextureData] Processing skin for player: " + playerUuid);
        ExpressionMod.LOGGER.info("[EyelidTextureData] Skin texture ID: " + skinTextureId);

        try {
            NativeImage skinImage = loadSkinImage(skinTextureId);
            if (skinImage == null) {
                ExpressionMod.LOGGER.warn("[EyelidTextureData] Could not load skin image!");
                return createWithFeatures(playerUuid, skinTextureId, null);
            }

            ExpressionMod.LOGGER.info("[EyelidTextureData] Skin image loaded: " +
                    skinImage.getWidth() + "x" + skinImage.getHeight());

            int markerColor = skinImage.getPixel(SkinRegions.MARKER_X, SkinRegions.MARKER_Y);
            boolean hasMarker = SkinRegions.isMarkerColor(markerColor);
            ExpressionMod.LOGGER.info("[EyelidTextureData] Marker at (7,7): " +
                    String.format("0x%08X", markerColor) + ", isMarker: " + hasMarker);

            int positionColor = skinImage.getPixel(SkinRegions.EYE_POSITION_X, SkinRegions.EYE_POSITION_Y);
            int eyeYPosition = SkinRegions.colorToNumber(positionColor);
            if (eyeYPosition < 1 || eyeYPosition > 8) {
                eyeYPosition = SkinRegions.DEFAULT_EYE_Y_IN_FACE;
            }
            ExpressionMod.LOGGER.info("[EyelidTextureData] Eye Y position: " + eyeYPosition);

            String baseId = "expr_" + playerUuid.toString().replace("-", "").substring(0, 8) +
                    "_" + System.currentTimeMillis() % 10000;

            NativeImage eyelashImage = extractRegion(skinImage,
                    SkinRegions.EYELASH_X, SkinRegions.EYELASH_Y,
                    SkinRegions.EYELASH_WIDTH, SkinRegions.EYELASH_HEIGHT);
            Identifier eyelashTexture = registerTexture(baseId + "_eyelash", eyelashImage);
            ExpressionMod.LOGGER.info("[EyelidTextureData] Eyelash texture: " + eyelashTexture);

            NativeImage eyeImage = extractRegion(skinImage,
                    SkinRegions.EYE_X, SkinRegions.EYE_Y,
                    SkinRegions.EYE_WIDTH, SkinRegions.EYE_HEIGHT);
            Identifier eyeTexture = registerTexture(baseId + "_eye", eyeImage);
            ExpressionMod.LOGGER.info("[EyelidTextureData] Eye texture: " + eyeTexture);

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

    @Nullable
    private static NativeImage loadSkinImage(Identifier skinTexture) {
        try {
            var client = Minecraft.getInstance();
            var textureManager = client.getTextureManager();
            var texture = textureManager.getTexture(skinTexture);

            ExpressionMod.LOGGER.info("[EyelidTextureData] Texture type: " +
                    (texture != null ? texture.getClass().getSimpleName() : "null"));

            if (texture instanceof DynamicTexture nativeTexture) {
                NativeImage pixels = nativeTexture.getPixels();
                if (pixels != null) {
                    NativeImage copy = new NativeImage(pixels.getWidth(), pixels.getHeight(), false);
                    copy.copyFrom(pixels);
                    ExpressionMod.LOGGER.info("[EyelidTextureData] Loaded from DynamicTexture");
                    return copy;
                }
            }

            var resourceManager = client.getResourceManager();
            var resource = resourceManager.getResource(skinTexture);
            if (resource.isPresent()) {
                ExpressionMod.LOGGER.info("[EyelidTextureData] Loaded from ResourceManager");
                return NativeImage.read(resource.get().open());
            }

            ExpressionMod.LOGGER.warn("[EyelidTextureData] Could not load texture: " + skinTexture);
        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[EyelidTextureData] Exception loading skin: " + skinTexture, e);
        }
        return null;
    }

    private static NativeImage extractRegion(NativeImage source, int x, int y, int width, int height) {
        NativeImage result = new NativeImage(width, height, false);
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < height; dy++) {
                int color = source.getPixel(x + dx, y + dy);
                result.setPixel(dx, dy, color);
            }
        }
        return result;
    }

    @Nullable
    private static Identifier registerTexture(String name, NativeImage image) {
        try {
            Identifier id = Identifier.fromNamespaceAndPath(ExpressionMod.MOD_ID, name);
            DynamicTexture texture = new DynamicTexture(() -> name, image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return id;
        } catch (Exception e) {
            ExpressionMod.LOGGER.error("[EyelidTextureData] Failed to register texture: " + name, e);
            image.close();
            return null;
        }
    }

    public static void clearCache() {
        cache.clear();
    }

    public static void clearCache(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    @Nullable
    public static EyelidTextureData get(UUID playerUuid) {
        return cache.get(playerUuid);
    }

    @Nullable
    public static EyelidTextureData getBySkinTexture(Identifier skinTexture) {
        UUID uuid = skinToUuidMap.get(skinTexture);
        if (uuid != null) {
            return cache.get(uuid);
        }
        return null;
    }

    public static void clearAllCaches() {
        ExpressionMod.LOGGER.info("[EyelidTextureData] Clearing all caches");
        cache.clear();
        skinToUuidMap.clear();
    }
}
