package com.expression.test;

import com.expression.network.EmoteSyncPayload;
import com.expression.network.ServerNetworkHandler;
import com.expression.network.FullStateSyncPayload;
import com.expression.config.ModConfig;
import com.expression.skin.FaceSkinData;
import com.expression.test.mixin.SkinDownloadInvoker;
import com.expression.render.ExpressionRenderState;
import com.expression.state.EmoteState;
import com.expression.state.EmoteType;
import com.expression.state.ExpressionStateManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.net.Proxy;

public class ContinuousFaceGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if ("true".equals(System.getenv("EXPRESSIONMOD_LEGACY_TEST"))) return;
        // Exercise vanilla's skin upload hook without an account or a network skin download.
        var upload = context.computeOnClient(client -> {
            var source = Identifier.of("expressionmod_test", "textures/fixture.png");
            NativeImage image;
            try (var stream = client.getResourceManager().getResource(source).orElseThrow().getInputStream()) {
                image = NativeImage.read(stream);
            } catch (IOException error) { throw new AssertionError(error); }
            var asset = new AssetInfo.TextureAssetInfo(Identifier.of("expressionmod_test", "uploaded-fixture"));
            var downloader = new PlayerSkinTextureDownloader(Proxy.NO_PROXY, client.getTextureManager(), client);
            return ((SkinDownloadInvoker) downloader).expressionmod$register(asset, image);
        });
        context.waitFor(client -> upload.isDone());
        context.runOnClient(client -> {
            var path = upload.join().texturePath();
            client.getTextureManager().destroyTexture(path);
            if (FaceSkinData.get(path) == null) throw new AssertionError("skin pixels were not captured before GPU upload");
        });
        boolean preview = PreviewSkin.load(context);
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.world != null);
            UUID self = context.computeOnClient(client -> client.player.getUuid());
            UUID forged = UUID.fromString("11111111-2222-3333-4444-555555555555");
            context.runOnClient(client -> {
                if (!ClientPlayNetworking.canSend(EmoteSyncPayload.ID)) throw new AssertionError("emote channel not negotiated");
                ClientPlayNetworking.send(new EmoteSyncPayload(forged, "NotTheSender", "smile", Long.MAX_VALUE));
            });
            context.waitTicks(2);
            world.getServer().runOnServer(server -> {
                var state = ExpressionStateManager.server().get(self);
                if (state == null || state.getCurrentEmote() != EmoteType.SMILE) throw new AssertionError("sender's face was not updated");
                if (state.getEmoteState().getRemainingTimeMs() > EmoteState.MAX_DURATION_MS) throw new AssertionError("unbounded lease");
                if (ExpressionStateManager.server().get(forged) != null) throw new AssertionError("forged UUID was accepted");
            });
            context.runOnClient(client -> ClientPlayNetworking.send(new EmoteSyncPayload(self, "ignored", "curious", 30_000)));
            context.waitTicks(2);
            world.getServer().runOnServer(server -> {
                if (ExpressionStateManager.server().get(self).getCurrentEmote() != EmoteType.CURIOUS)
                    throw new AssertionError("New expression did not cross the authenticated packet path");
                // The sender uses local prediction and is deliberately not echoed by the server.
                // A different player exercises the actual observer's S2C receiver.
                ServerPlayNetworking.send(server.getPlayerManager().getPlayer(self),
                        new EmoteSyncPayload(forged, "ObserverFixture", "curious", 30_000));
            });
            testBridgeWire(context, world.getServer(), self);
            context.waitFor(client -> ExpressionStateManager.getInstance().get(forged) != null
                    && ExpressionStateManager.getInstance().get(forged).getCurrentEmote() == EmoteType.CURIOUS, 100);
            context.runOnClient(client -> {
                if (preview) ModConfig.getInstance().shannonSkinPlayers = new String[]{self.toString(), forged.toString()};
                ModConfig.getInstance().enableBlinking = false;
                ModConfig.getInstance().enableMicroSaccades = false;
                ModConfig.getInstance().enableShannonEyeGradient = false;
            });
            context.setScreen(() -> new FacePreviewScreen());
            if (preview) {
                context.takeScreenshot("shannon-original-unmodified");
                context.runOnClient(client -> PreviewSkin.enableExpressions());
            }
            Path neutral = null;
            for (var emote : EmoteType.values()) {
                context.runOnClient(client -> ExpressionStateManager.getInstance()
                        .getOrCreate(self, client.player.getName().getString()).setEmote(emote, 3000));
                context.waitFor(client -> !ExpressionStateManager.getInstance().get(self).getEmoteState().isTransitioning(), 200);
                Path image = context.takeScreenshot("face-" + emote.getId());
                if (emote == EmoteType.NEUTRAL) neutral = image;
                if ((emote == EmoteType.SMILE || emote == EmoteType.ANGRY) && changedPixels(neutral, image) < 20)
                    throw new AssertionError("Eye geometry did not change for " + emote);
                context.runOnClient(client -> {
                    var state = client.getEntityRenderDispatcher().getRenderer(client.player).createRenderState();
                    // The actual preview already rendered the model, testing all required Mixins and deferred vertices.
                    if (!(state instanceof ExpressionRenderState)) throw new AssertionError("render snapshot Mixin missing");
                });
            }
            if (preview) {
                var sampleClock = new java.util.concurrent.atomic.AtomicLong(1000);
                var timeline = new EmoteState(sampleClock::get);
                for (var target : new EmoteType[]{EmoteType.SMILE, EmoteType.CURIOUS}) {
                    long start = sampleClock.get();
                    timeline.setEmote(target, 30_000);
                    for (int index = 0; index <= 12; index++) {
                        sampleClock.set(start + EmoteState.TRANSITION_DURATION_MS * index / 12);
                        var pose = timeline.sampleFace();
                        context.runOnClient(client -> PreviewSkin.sampledPose(pose));
                        context.waitTicks(2);
                        if (index == 12) assertSampledFrame(context, pose, null);
                        context.takeScreenshot("transition-" + target.getId() + "-" + String.format("%02d", index));
                    }
                }
                context.runOnClient(client -> PreviewSkin.sampledPose(null));
                context.runOnClient(client -> ExpressionStateManager.getInstance().get(self).setEmote(EmoteType.NEUTRAL, 3000));
                context.waitFor(client -> !ExpressionStateManager.getInstance().get(self).getEmoteState().isTransitioning(), 200);
                boolean softRobotV5 = context.computeOnClient(client -> FaceSkinData.getForPlayer(
                        PreviewSkin.body().texturePath(), self).isSoftRobotV5());
                boolean minimalLines = softRobotV5 && context.computeOnClient(client -> FaceSkinData.getForPlayer(
                        PreviewSkin.body().texturePath(), self).pixel(42,21) == 0xFF2BA6BF);
                for (String view : new String[]{"front", "side"}) {
                    var neutralPose = com.expression.state.FacePose.NEUTRAL;
                    var viewPose = new com.expression.state.FacePose(neutralPose.leftOpen(), neutralPose.rightOpen(),
                            0, 0, 0, 0, 0, 0, 0, 1, 1, neutralPose.energy());
                    context.setScreen(() -> new FacePreviewScreen(null, view.equals("side")));
                    context.runOnClient(client -> {
                        PreviewSkin.sampledPose(viewPose);
                        PreviewSkin.circuitSeconds(null);
                        ModConfig.getInstance().enableShannonCircuitFlow = false;
                        ModConfig.getInstance().enableShannonEyeGradient = false;
                    });
                    context.waitTicks(2); // Flush deferred render commands before sampling a new view.
                    Path baseline = context.takeScreenshot("circuit-" + view + "-off");
                    context.runOnClient(client -> {
                        ModConfig.getInstance().enableShannonCircuitFlow = true;
                        ModConfig.getInstance().enableShannonEyeGradient = true;
                    });
                    Path first = null;
                    for (int index = 0; index < 84; index++) {
                        // 16.8s contains three circuit waves and two eye-gradient cycles.
                        double seconds = 16.8 * index / 84;
                        context.runOnClient(client -> PreviewSkin.circuitSeconds(seconds));
                        context.waitTicks(2);
                        if (index == 0 || index == 14) assertSampledFrame(context, viewPose, seconds);
                        Path image = context.takeScreenshot("circuit-" + view + "-" + String.format("%02d", index));
                        // This fabric panel used to be mistaken for a cyan hardware circuit.
                        if (!softRobotV5 && view.equals("front") && changedPixels(baseline, image, 406, 207, 446, 224) != 0)
                            throw new AssertionError("Circuit animation changed the T-shirt fabric at sample " + index);
                        if (index == 0) first = image;
                        if (index == 14 && changedPixels(first, image) < 5)
                            throw new AssertionError("Circuit wave did not animate in " + view + " view");
                        if (index == 14 && view.equals("front") && changedPixels(first, image, 400, 130, 480, 165) < 10)
                            throw new AssertionError("Eye gradient did not move with a fixed face pose");
                        if (index == 14 && softRobotV5 && view.equals("front")
                                && changedPixels(first, image, 384, 142, 397, 151) < 10)
                            throw new AssertionError("v5 outer head circuit did not animate");
                    }
                    if (changedPixels(baseline, first) < 5) throw new AssertionError("Circuit overlay missing in " + view + " view");
                    if (changedPixels(baseline, first, 300, 182, 540, 420) < 20)
                        throw new AssertionError("Body circuit overlay missing in " + view + " view");
                    if (minimalLines && view.equals("front")) {
                        if (changedTracePixels(baseline, first, 346,182,397,298) < 10)
                            throw new AssertionError("Minimal-line arm circuits missing");
                        if (changedTracePixels(baseline, first, 383,330,424,410) < 10)
                            throw new AssertionError("Minimal-line leg circuits missing");
                    }
                }
                captureAliveMotion(context);
                context.runOnClient(client -> {
                    PreviewSkin.sampledPose(null);
                    PreviewSkin.circuitSeconds(null);
                    ModConfig.getInstance().enableShannonCircuitFlow = false;
                    ModConfig.getInstance().enableShannonEyeGradient = false;
                });
            }
            var other = context.computeOnClient(client -> new OtherClientPlayerEntity(client.world,
                    new GameProfile(forged, "SameSkinDifferentFace")));
            context.runOnClient(client -> {
                ExpressionStateManager.getInstance().get(self).setEmote(EmoteType.SMILE, 3000);
            });
            world.getServer().runOnServer(server -> {
                ExpressionStateManager.server().getOrCreate(forged, "SameSkinDifferentFace").setEmote(EmoteType.ANGRY, 3000);
                if (!ServerPlayNetworking.canSend(server.getPlayerManager().getPlayer(self), FullStateSyncPayload.ID))
                    throw new AssertionError("full-state channel was not negotiated");
                ServerNetworkHandler.sendAllStatesToPlayer(server.getPlayerManager().getPlayer(self));
            });
            context.waitFor(client -> ExpressionStateManager.getInstance().get(forged) != null
                    && ExpressionStateManager.getInstance().get(forged).getCurrentEmote() == EmoteType.ANGRY, 100);
            context.runOnClient(client -> {
                var remote = ExpressionStateManager.getInstance().get(forged);
                if (remote.getCurrentEmote() != EmoteType.ANGRY) throw new AssertionError("joining observer received " + remote.getCurrentEmote()
                        + ", remaining=" + remote.getEmoteState().getRemainingTimeMs());
                if (remote.hasEyeTarget()) throw new AssertionError("initial state disabled natural gaze tracking");
            });
            context.setScreen(() -> new FacePreviewScreen(other));
            context.waitFor(client -> !ExpressionStateManager.getInstance().get(self).getEmoteState().isTransitioning(), 200);
            context.waitFor(client -> !ExpressionStateManager.getInstance().get(forged).getEmoteState().isTransitioning(), 200);
            context.takeScreenshot("two-players-same-skin");
            context.runOnClient(client -> {
                ModConfig.getInstance().enableBlinking = true;
                ExpressionStateManager.getInstance().get(self).getBlinkState().triggerBlink();
            });
            context.takeScreenshot("face-blink-start");
            context.waitTicks(2);
            context.takeScreenshot("face-blink-moving");
            context.setScreen(() -> null);
        }
    }

    private static void testBridgeWire(ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext serverContext, UUID self) {
        com.google.gson.JsonObject fixture;
        try (var stream = ContinuousFaceGameTest.class.getResourceAsStream("/bridge-wire.json")) {
            if (stream == null) throw new AssertionError("Missing Node bridge wire fixture");
            fixture = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException error) { throw new AssertionError(error); }
        for (var item : fixture.getAsJsonArray("emotes")) {
            var row = item.getAsJsonObject(); var id = row.get("expression").getAsString();
            var buffer = new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(
                    java.util.HexFormat.of().parseHex(row.get("hex").getAsString())), net.minecraft.registry.DynamicRegistryManager.EMPTY);
            var payload = EmoteSyncPayload.CODEC.decode(buffer);
            if (buffer.isReadable()) throw new AssertionError("Node/Java emotion wire trailing bytes");
            buffer.release();
            context.runOnClient(client -> ClientPlayNetworking.send(payload)); context.waitTicks(2);
            serverContext.runOnServer(server -> {
                var state = ExpressionStateManager.server().get(self);
                if (state.getCurrentEmote() != EmoteType.fromId(id)) throw new AssertionError("Bridge expression mismatch: " + id);
                if (ExpressionStateManager.server().get(payload.playerUuid()) != null && !payload.playerUuid().equals(self))
                    throw new AssertionError("Bridge packet trusted claimed identity");
            });
        }
        var buffer = new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(
                java.util.HexFormat.of().parseHex(fixture.get("dialogue").getAsString())), net.minecraft.registry.DynamicRegistryManager.EMPTY);
        var dialogue = com.expression.network.DialogueSyncPayload.CODEC.decode(buffer);
        if (buffer.isReadable()) throw new AssertionError("Node/Java activity wire trailing bytes"); buffer.release();
        context.runOnClient(client -> ClientPlayNetworking.send(dialogue)); context.waitTicks(2);
        serverContext.runOnServer(server -> {
            var state = ExpressionStateManager.server().get(self).getDialogueState();
            if (state.activity() != com.expression.state.DialogueActivity.THINKING || state.yaw() != 22 || state.pitch() != -5)
                throw new AssertionError("Activity failed authenticated receiver");
            ServerNetworkHandler.sendDialogueStatesToPlayer(server.getPlayerManager().getPlayer(self));
        });
        context.waitFor(client -> ExpressionStateManager.getInstance().get(self) != null
                && ExpressionStateManager.getInstance().get(self).getDialogueState().activity()
                == com.expression.state.DialogueActivity.THINKING, 100);
        context.runOnClient(client -> ClientPlayNetworking.send(new com.expression.network.DialogueSyncPayload(
                self, "ignored", "speaking", Long.MAX_VALUE, true, Float.NaN, 0))); context.waitTicks(2);
        serverContext.runOnServer(server -> {
            if (ExpressionStateManager.server().get(self).getDialogueState().activity() != com.expression.state.DialogueActivity.THINKING)
                throw new AssertionError("Invalid activity angles accepted");
        });
        context.runOnClient(client -> {
            ClientPlayNetworking.send(new com.expression.network.DialogueSyncPayload(self,"ignored","idle",5000,false,0,0));
            ExpressionStateManager.getInstance().get(self).getDialogueState().update(com.expression.state.DialogueActivity.IDLE,0,false,0,0);
        }); context.waitTicks(2);
    }

    private static void captureAliveMotion(ClientGameTestContext context) {
        var motion = new com.expression.state.AliveMotion();
        var clock = new java.util.concurrent.atomic.AtomicLong(0);
        var emote = new EmoteState(clock::get);
        var blink = new com.expression.state.BlinkState(clock::get, () -> .55);
        context.setScreen(() -> new FacePreviewScreen());
        context.runOnClient(client -> { ModConfig.getInstance().enableShannonCircuitFlow = true; ModConfig.getInstance().enableShannonEyeGradient = true; });
        Path neutral = null, thinking = null, speaking = null;
        for (int index = 0; index < 84; index++) {
            double seconds = index / 10.0; clock.set(index * 100L);
            var activity = index < 10 || index >= 62 ? com.expression.state.DialogueActivity.IDLE
                    : index < 20 ? com.expression.state.DialogueActivity.LISTENING
                    : index < 40 ? com.expression.state.DialogueActivity.THINKING : com.expression.state.DialogueActivity.SPEAKING;
            if (index == 40) emote.setEmote(EmoteType.SMILE, 30000);
            var sample = motion.sample(emote.sampleFace(), emote.getCurrentEmote(), activity,
                    activity == com.expression.state.DialogueActivity.IDLE ? 0 : .27f, 0, seconds, 17, true);
            blink.configure((long)(3000*sample.blinkIntervalScale()),(long)(6000*sample.blinkIntervalScale()));
            blink.openingScale(sample.blinkOpenScale()); if (index == 62) blink.triggerBlink();
            float progress = blink.getBlinkProgress();
            context.runOnClient(client -> { PreviewSkin.aliveSample(sample, progress); PreviewSkin.circuitSeconds(seconds); });
            context.waitTicks(2);
            if (index == 25 || index == 45) assertSampledFrame(context, sample.pose(), seconds);
            var image = context.takeScreenshot("alive-"+String.format("%02d",index));
            if (index == 0) neutral = image; if (index == 25) thinking = image; if (index == 45) speaking = image;
        }
        if (changedPixels(neutral,thinking,380,100,480,185) < 8 || changedPixels(thinking,speaking,380,100,480,185) < 8)
            throw new AssertionError("Conversation motion did not render on the native face");
    }

    private static void assertSampledFrame(ClientGameTestContext context, com.expression.state.FacePose pose, Double seconds) {
        context.runOnClient(client -> {
            var renderer = (net.minecraft.client.render.entity.PlayerEntityRenderer)
                    client.getEntityRenderDispatcher().getRenderer(client.player);
            var state = renderer.createRenderState();
            renderer.updateRenderState(client.player, state, 0);
            var frame = ((ExpressionRenderState) state).expressionmod$getFrame();
            if (frame == null || !frame.pose().equals(pose))
                throw new AssertionError("Production render-state update overwrote the sampled preview pose");
            if (seconds != null && frame.circuitSeconds() != seconds)
                throw new AssertionError("Production render-state update overwrote the sampled preview clock");
        });
    }

    private static int changedPixels(Path baseline, Path actual) {
        return changedPixels(baseline, actual, 0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private static int changedTracePixels(Path baseline, Path actual, int x1, int y1, int x2, int y2) {
        try (var aStream = Files.newInputStream(baseline); var bStream = Files.newInputStream(actual);
                var a = NativeImage.read(aStream); var b = NativeImage.read(bStream)) {
            int changed = 0;
            for (int y = y1; y < y2; y++) for (int x = x1; x < x2; x++) {
                int rgb = a.getColorArgb(x,y), r = (rgb >>> 16) & 255, g = (rgb >>> 8) & 255, blue = rgb & 255;
                if (g > r + 40 && blue > r + 40 && blue > g && rgb != b.getColorArgb(x,y)) changed++;
            }
            return changed;
        } catch (IOException error) { throw new AssertionError(error); }
    }
    private static int changedPixels(Path baseline, Path actual, int x1, int y1, int x2, int y2) {
        try (var aStream = Files.newInputStream(baseline); var bStream = Files.newInputStream(actual);
                var a = NativeImage.read(aStream); var b = NativeImage.read(bStream)) {
            if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) throw new AssertionError("screenshot size changed");
            int changed = 0;
            for (int y = y1; y < Math.min(y2, a.getHeight()); y++) for (int x = x1; x < Math.min(x2, a.getWidth()); x++)
                if (a.getColorArgb(x, y) != b.getColorArgb(x, y)) changed++;
            return changed;
        } catch (IOException error) { throw new AssertionError(error); }
    }

    public static class FacePreviewScreen extends Screen {
        private final LivingEntity other;
        private final boolean sideView;
        public FacePreviewScreen() { this(null, false); }
        public FacePreviewScreen(LivingEntity other) { this(other, false); }
        FacePreviewScreen(LivingEntity other, boolean sideView) {
            super(Text.literal("ExpressionMod continuous face test")); this.other = other; this.sideView = sideView;
        }
        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xFF344050);
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
            int size = Math.min(width / 3, height / 3);
            int center = other == null ? width / 2 : width / 3;
            InventoryScreen.drawEntity(context, center - size, 28, center + size, height - 12,
                    size, .0625f, center + (sideView ? 130 : 0), height / 2f, client.player);
            if (other != null) {
                center = width * 2 / 3;
                InventoryScreen.drawEntity(context, center - size, 28, center + size, height - 12,
                        size, .0625f, center, height / 2f, other);
            }
        }
        @Override public boolean shouldPause() { return false; }
    }
}
