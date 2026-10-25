package com.expression.skin;

import com.expression.config.ModConfig;
import com.expression.state.ExpressionStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import java.util.*;

/** Shared immutable frames, selected using each player's own state. No global rendering player. */
public final class LegacySkinTextures {
    private record Pixels(int[] colors, SkinLayout layout) { int at(int x,int y) { return colors[y*64+x]; } }
    private static final Map<Identifier, Optional<Pixels>> PIXELS = new LinkedHashMap<>(16,.75f,true);
    private static final Map<Identifier, Frames> FRAMES = new LinkedHashMap<>(16,.75f,true);
    private static final Map<UUID, Gaze> GAZE = new HashMap<>();
    private static long serial;
    private static final class Frames {
        final Pixels pixels;
        final AssetInfo.TextureAsset[] assets = new AssetInfo.TextureAsset[15];
        Frames(Pixels pixels) { this.pixels=pixels; }
        void close() { for (var asset:assets) if(asset!=null) MinecraftClient.getInstance().getTextureManager().destroyTexture(asset.texturePath()); }
    }
    private static final class Gaze { float trackedYaw; long at; }
    public static synchronized void capture(Identifier original, NativeImage image) {
        Optional<Pixels> result = Optional.empty();
        if (image.getWidth()==64 && image.getHeight()==64
                && !ShannonSkinProfile.matches(64,64,image::getColorArgb)) {
            var layout=LegacySkinFrames.detect(64,64,image::getColorArgb);
            boolean emotes=false;
            for(int slot=0;slot<5;slot++) emotes |= LegacySkinFrames.hasEmote(image::getColorArgb,slot);
            if(layout.isPresent() || emotes) {
                int[] colors=new int[4096];
                for(int y=0;y<64;y++) for(int x=0;x<64;x++) colors[y*64+x]=image.getColorArgb(x,y);
                result=Optional.of(new Pixels(colors,layout.orElse(null)));
            }
        }
        PIXELS.put(original,result);
        while(PIXELS.size()>256) PIXELS.remove(PIXELS.keySet().iterator().next());
    }
    private static Pixels pixels(Identifier original) {
        var cached=PIXELS.get(original);
        if(cached!=null) return cached.orElse(null);
        var client=MinecraftClient.getInstance();
        if(client.getTextureManager().getTexture(original) instanceof NativeImageBackedTexture texture && texture.getImage()!=null)
            capture(original,texture.getImage());
        else {
            var resource=client.getResourceManager().getResource(original);
            if(resource.isPresent()) try(var stream=resource.get().getInputStream(); var image=NativeImage.read(stream)) {
                capture(original,image);
            } catch(java.io.IOException ignored) { return null; }
        }
        return PIXELS.getOrDefault(original,Optional.empty()).orElse(null);
    }
    public static boolean hasEmote(Identifier original,int slot) {
        var pixels=pixels(original); return pixels!=null && LegacySkinFrames.hasEmote(pixels::at,slot);
    }
    public static AssetInfo.TextureAsset select(AbstractClientPlayerEntity player, AssetInfo.TextureAsset original) {
        var pixels=pixels(original.texturePath());
        if(pixels==null) return original;
        var config=ModConfig.getInstance();
        var state=ExpressionStateManager.getInstance().getOrCreate(player.getUuid(),player.getName().getString());
        int slot=config.enableExpressions ? LegacySkinFrames.slot(state.getCurrentEmote()) : -1;
        if(slot>=0 && LegacySkinFrames.hasEmote(pixels::at,slot)) return frame(original.texturePath(),pixels,10+slot);
        if(pixels.layout==null) return original;
        state.getBlinkState().configure(config.blinkIntervalMin,config.blinkIntervalMax);
        state.getBlinkState().openingScale(1);
        long now=System.nanoTime();
        var gaze=GAZE.computeIfAbsent(player.getUuid(),id->{var g=new Gaze();g.trackedYaw=player.getHeadYaw();g.at=now;return g;});
        float diff=MathHelper.wrapDegrees(player.getHeadYaw()-gaze.trackedYaw);
        double elapsed=Math.clamp((now-gaze.at)/1_000_000_000.0,0,.25);
        gaze.trackedYaw += diff*(float)(1-Math.exp(-elapsed*(Math.abs(diff)>45?18:6))); gaze.at=now;
        float yaw=state.hasEyeTarget()?state.getEyeState().getTargetYaw():MathHelper.wrapDegrees(player.getHeadYaw()-gaze.trackedYaw);
        float pitch=state.hasEyeTarget()?state.getEyeState().getTargetPitch():player.getPitch();
        int index=LegacySkinFrames.eyeFrame(player.isSleeping() || player.hasStatusEffect(StatusEffects.BLINDNESS),
                config.enableBlinking?state.getBlinkProgress():0,config.enableEyeMovement?yaw:0,config.enableEyeMovement?pitch:0);
        return frame(original.texturePath(),pixels,index);
    }
    private static AssetInfo.TextureAsset frame(Identifier original,Pixels pixels,int index) {
        var frames=FRAMES.get(original);
        if(frames!=null && frames.pixels!=pixels) { frames.close();FRAMES.remove(original);frames=null; }
        if(frames==null) {
            frames=new Frames(pixels); FRAMES.put(original,frames);
            while(FRAMES.size()>64) { var first=FRAMES.keySet().iterator().next(); FRAMES.remove(first).close(); }
        }
        if(frames.assets[index]==null) {
            int[] colors=index>=10?LegacySkinFrames.emote(pixels.colors,pixels.layout,index-10):LegacySkinFrames.eyes(pixels.colors,pixels.layout,index);
            var image=new NativeImage(64,64,false);
            for(int y=0;y<64;y++) for(int x=0;x<64;x++) image.setColorArgb(x,y,colors[y*64+x]);
            var id=Identifier.of("expressionmod","legacy/"+(serial++));
            var asset=new AssetInfo.TextureAssetInfo(id);
            var texture=new NativeImageBackedTexture(()->"ExpressionMod legacy skin",image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(asset.texturePath(),texture);
            frames.assets[index]=asset;
        }
        return frames.assets[index];
    }
    public static void clearFrames() {
        var client=MinecraftClient.getInstance();
        if(!client.isOnThread()) { client.execute(LegacySkinTextures::clearFrames); return; }
        for(var frames:FRAMES.values()) frames.close();FRAMES.clear();GAZE.clear();
    }
    public static void retain(Set<UUID> players) { GAZE.keySet().retainAll(players); }
    private LegacySkinTextures() {}
}
