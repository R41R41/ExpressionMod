package com.expression.skin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.expression.config.ModConfig;

/** CPU-only skin samples. No generated eye frames or per-player GPU textures. */
public final class FaceSkinData {
    private static final int CACHE_LIMIT = 256;
    private static final Map<Identifier, Optional<FaceSkinData>> CACHE = new LinkedHashMap<>(16, .75f, true);
    public final SkinLayout layout;
    private final int[] colors;
    private final SkinLayout.EyeSpan[] eyeSpans;
    private final boolean shannonProfile;
    private final boolean softRobotV5;

    private FaceSkinData(SkinLayout layout, NativeImage image) {
        this(layout, image, false);
    }

    private FaceSkinData(SkinLayout layout, NativeImage image, boolean shannonProfile) {
        this.layout = layout;
        this.shannonProfile = shannonProfile;
        this.softRobotV5 = shannonProfile && ShannonSkinProfile.isSoftRobotV5(image::getColorArgb);
        colors = shannonProfile ? ShannonSkinProfile.palette(image::getColorArgb) : new int[64 * 64];
        if (!shannonProfile)
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) colors[y * 64 + x] = image.getColorArgb(x, y);
        eyeSpans = new SkinLayout.EyeSpan[]{layout.eyeSpan(0, this::sample), layout.eyeSpan(1, this::sample)};
    }

    /** Called while vanilla still owns the downloaded image, before its upload/close. */
    public static synchronized void capture(Identifier texture, NativeImage image) {
        Optional<FaceSkinData> data = SkinLayout.detect(image.getWidth(), image.getHeight(), image::getColorArgb)
                .map(layout -> new FaceSkinData(layout, image));
        if (data.isEmpty() && ShannonSkinProfile.matches(image.getWidth(), image.getHeight(), image::getColorArgb))
            data = Optional.of(new FaceSkinData(ShannonSkinProfile.isSoftRobotV5(image::getColorArgb)
                    ? ShannonSkinProfile.SOFT_ROBOT_V5_LAYOUT : ShannonSkinProfile.LAYOUT, image, true));
        put(texture, data);
    }

    private static void put(Identifier texture, Optional<FaceSkinData> data) {
        CACHE.put(texture, data);
        while (CACHE.size() > CACHE_LIMIT) CACHE.remove(CACHE.keySet().iterator().next());
    }

    public static synchronized FaceSkinData get(Identifier texture) {
        var data = raw(texture);
        return data != null && !data.shannonProfile ? data : null;
    }

    public static synchronized FaceSkinData getForPlayer(Identifier texture, UUID player) {
        var data = raw(texture);
        if (data == null || !data.shannonProfile) return data;
        var config = ModConfig.getInstance();
        return ShannonSkinProfile.allowedPlayer(player, config.shannonSkinPlayers) ? data : null;
    }

    private static FaceSkinData raw(Identifier texture) {
        Optional<FaceSkinData> cached = CACHE.get(texture);
        if (cached != null) return cached.orElse(null);
        var client = MinecraftClient.getInstance();
        var nativeTexture = client.getTextureManager().getTexture(texture);
        if (nativeTexture instanceof NativeImageBackedTexture pixels && pixels.getImage() != null) {
            capture(texture, pixels.getImage());
        } else {
            var resource = client.getResourceManager().getResource(texture);
            if (resource.isPresent()) {
                try (var stream = resource.get().getInputStream(); var image = NativeImage.read(stream)) {
                    capture(texture, image);
                } catch (IOException ignored) { put(texture, Optional.empty()); }
            } else {
                // A later download capture replaces this entry; never cache a missing image as an enabled skin.
                put(texture, Optional.empty());
            }
        }
        return CACHE.get(texture).orElse(null);
    }

    public int sample(int column, int row) { return colors[(layout.sourceY() + row) * 64 + layout.sourceX() + column]; }
    public SkinLayout.EyeSpan eyeSpan(int side) { return eyeSpans[side]; }
    public boolean usesWaterlightGradient() { return shannonProfile; }
    public boolean isSoftRobotV5() { return softRobotV5; }
    public int face(int x, int y) { return colors[(8 + y) * 64 + 8 + x]; }
    public int headPixel(int face, int x, int y) { return colors[(8 + y) * 64 + face * 8 + x]; }
    public int headHatPixel(int face, int x, int y) { return colors[(8 + y) * 64 + 32 + face * 8 + x]; }
    public int pixel(int x, int y) { return colors[y * 64 + x]; }
    public int hat(int x, int y) { return colors[(8 + y) * 64 + 40 + x]; }

    /** Keep downloaded samples across disconnects: vanilla may reuse its GPU skin without downloading it again. */
    public static synchronized void invalidateResources() {
        CACHE.entrySet().removeIf(entry -> !entry.getKey().getPath().startsWith("skins/"));
    }
}
