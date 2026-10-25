package com.expression.test;

import com.expression.skin.FaceSkinData;
import com.expression.test.mixin.SkinDownloadInvoker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

/** Optional, local-only preview of I_am_Shannon's current robot skin. Never writes or uploads a skin. */
public final class PreviewSkin {
    private static AssetInfo.TextureAsset body;
    private static boolean expressions;
    private static com.expression.state.FacePose sampledPose;
    private static Double circuitSeconds;
    private static com.expression.state.AliveMotion.Sample aliveSample;
    private static float aliveBlink;

    public static boolean load(ClientGameTestContext context) {
        String path = System.getenv("EXPRESSIONMOD_PREVIEW_SKIN");
        var upload = context.computeOnClient(client -> {
            NativeImage original;
            try (var stream = path == null || path.isBlank()
                    ? client.getResourceManager().getResource(Identifier.of("expressionmod_test", "textures/shannon-fixture.png")).orElseThrow().getInputStream()
                    : Files.newInputStream(Path.of(path))) {
                original = NativeImage.read(stream);
            } catch (IOException error) { throw new AssertionError("Cannot read local preview skin", error); }
            if (original.getWidth() != 64 || original.getHeight() != 64) {
                original.close();
                throw new AssertionError("This preview profile requires a 64x64 skin");
            }
            var asset = new AssetInfo.TextureAssetInfo(Identifier.of("expressionmod_test", "local-preview"));
            var downloader = new PlayerSkinTextureDownloader(Proxy.NO_PROXY, client.getTextureManager(), client);
            return ((SkinDownloadInvoker) downloader).expressionmod$register(asset, original);
        });
        context.waitFor(client -> upload.isDone());
        context.runOnClient(client -> {
            body = upload.join();
            if (FaceSkinData.get(body.texturePath()) != null)
                throw new AssertionError("Expected an unmarked original skin for this preview profile");
            var config = com.expression.config.ModConfig.getInstance();
            var shannon = java.util.UUID.fromString(com.expression.skin.ShannonSkinProfile.PLAYER_UUID);
            // 1.3.2: no opt-in. Shannon's face is adapted for every observer; nobody else's unmarked skin is.
            if (FaceSkinData.getForPlayer(body.texturePath(), shannon) == null)
                throw new AssertionError("Production adapter did not capture the inspected face");
            if (FaceSkinData.getForPlayer(body.texturePath(), java.util.UUID.randomUUID()) != null)
                throw new AssertionError("Production adapter applied to an unconfigured player");
            var saved = config.shannonSkinPlayers;
            config.shannonSkinPlayers = new String[0];
            if (FaceSkinData.getForPlayer(body.texturePath(), shannon) == null)
                throw new AssertionError("Shannon's own face depended on a local setting");
            config.shannonSkinPlayers = saved;
        });
        return true;
    }

    public static AssetInfo.TextureAsset body() { return body; }
    public static void clear() { body = null; expressions = false; sampledPose = null; circuitSeconds = null; aliveSample = null; }
    public static boolean expressions() { return expressions; }
    public static void sampledPose(com.expression.state.FacePose pose) { sampledPose = pose; aliveSample = null; }
    public static void aliveSample(com.expression.state.AliveMotion.Sample sample, float blink) {
        aliveSample = sample; aliveBlink = blink; sampledPose = sample.pose();
    }
    public static void circuitSeconds(Double seconds) { circuitSeconds = seconds; }
    public static com.expression.render.ExpressionRenderState.Frame sample(com.expression.render.ExpressionRenderState.Frame frame) {
        if (frame == null || (sampledPose == null && circuitSeconds == null)) return frame;
        var pose = sampledPose == null ? frame.pose() : sampledPose;
        return new com.expression.render.ExpressionRenderState.Frame(frame.player(), frame.skin(), pose,
                aliveSample == null ? frame.blink() : aliveBlink,
                aliveSample != null ? aliveSample.gazeX() : sampledPose == null ? frame.gazeX() : pose.gazeX(),
                aliveSample != null ? aliveSample.gazeY() : sampledPose == null ? frame.gazeY() : pose.gazeY(), frame.headMovement(),
                circuitSeconds == null ? frame.circuitSeconds() : circuitSeconds,
                circuitSeconds == null ? frame.eyeGradientSeconds()
                        : com.expression.config.ModConfig.getInstance().enableShannonEyeGradient ? circuitSeconds : -1);
    }
    public static void enableExpressions() { expressions = true; }
    private PreviewSkin() {}
}
