package com.expression.test;

import com.expression.config.ModConfig;
import com.expression.network.ClientNetworkHandler;
import com.expression.network.EmoteSyncPayload;
import com.expression.render.ExpressionRenderState;
import com.expression.skin.LegacySkinFrames;
import com.expression.skin.LegacySkinTextures;
import com.expression.state.EmoteType;
import com.expression.state.ExpressionStateManager;
import com.expression.test.mixin.SkinDownloadInvoker;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.net.Proxy;
import java.util.UUID;

/** The real Rai1241 skin, vanilla upload, production render selection and authenticated network path. */
public final class LegacyFaceGameTest implements FabricClientGameTest {
    private static AssetInfo.TextureAsset body;
    private static int[] original;
    public static AssetInfo.TextureAsset body() { return body; }
    @Override public void runTest(ClientGameTestContext context) {
        PreviewSkin.clear();
        var upload = context.computeOnClient(client -> {
            NativeImage image;
            try (var stream = client.getResourceManager().getResource(Identifier.of("expressionmod_test", "textures/legacy-fixture.png")).orElseThrow().getInputStream()) {
                image = NativeImage.read(stream);
            } catch (IOException error) { throw new AssertionError(error); }
            original = new int[4096];
            for (int y=0;y<64;y++) for (int x=0;x<64;x++) original[y*64+x] = image.getColorArgb(x,y);
            var asset = new AssetInfo.TextureAssetInfo(Identifier.of("expressionmod_test", "legacy-uploaded"));
            return ((SkinDownloadInvoker)new PlayerSkinTextureDownloader(Proxy.NO_PROXY, client.getTextureManager(), client))
                    .expressionmod$register(asset, image);
        });
        context.waitFor(client -> upload.isDone());
        context.runOnClient(client -> {
            body = upload.join();
            for(int slot=0;slot<5;slot++) if(!LegacySkinTextures.hasEmote(body.texturePath(),slot))
                throw new AssertionError("Vanilla upload lost authored emote " + slot);
            var config=ModConfig.getInstance();
            config.enableExpressions=true; config.enableEyeMovement=true; config.enableBlinking=false;
        });
        try(var world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player!=null && client.world!=null);
            UUID self = context.computeOnClient(client -> client.player.getUuid());
            context.runOnClient(client -> ExpressionStateManager.getInstance().getOrCreate(self,"RaiFixture").reset());
            context.setScreen(ContinuousFaceGameTest.FacePreviewScreen::new);
            context.runOnClient(client -> assertFrame(client.player, LegacySkinFrames.eyes(original,
                    LegacySkinFrames.detect(64,64,(x,y)->original[y*64+x]).orElseThrow(),0)));
            context.takeScreenshot("legacy-rai-neutral");
            var types=new EmoteType[]{EmoteType.SMILE,EmoteType.ANGRY,EmoteType.SAD,EmoteType.SURPRISED,EmoteType.THINK};
            for(int slot=0;slot<5;slot++) {
                var type=types[slot]; final int index=slot;
                context.runOnClient(client -> {
                    ExpressionStateManager.getInstance().get(self).setEmote(type,30_000);
                    ClientNetworkHandler.sendEmoteChange(type,30_000);
                });
                context.waitTicks(2);
                world.getServer().runOnServer(server -> {
                    if(ExpressionStateManager.server().get(self).getCurrentEmote()!=type)
                        throw new AssertionError("Authored emote did not synchronize: "+type);
                });
                context.runOnClient(client -> {
                    int[] expected=original.clone();
                    int[][] sources={{24,0},{32,0},{56,32},{56,37},{56,42}};
                    for(int y=0;y<5;y++) for(int x=0;x<8;x++) {
                        int color=original[(sources[index][1]+y)*64+sources[index][0]+x];
                        if((color>>>24)!=0) expected[(11+y)*64+8+x]=color;
                    }
                    assertFrame(client.player,expected);
                });
                context.takeScreenshot("legacy-rai-emote-"+(slot+1));
            }
            var other=context.computeOnClient(client -> new OtherClientPlayerEntity(client.world,
                    new GameProfile(UUID.fromString("11111111-2222-3333-4444-555555555555"),"OtherSameSkin")));
            context.runOnClient(client -> ExpressionStateManager.getInstance().get(self).setEmote(EmoteType.SMILE,30_000));
            world.getServer().runOnServer(server -> ServerPlayNetworking.send(server.getPlayerManager().getPlayer(self),
                    new EmoteSyncPayload(other.getUuid(),"OtherSameSkin","angry",30_000)));
            context.waitFor(client -> ExpressionStateManager.getInstance().get(other.getUuid())!=null
                    && ExpressionStateManager.getInstance().get(other.getUuid()).getCurrentEmote()==EmoteType.ANGRY);
            context.runOnClient(client -> {
                var a=rendered(client.player); var b=rendered(other);
                if(a.texturePath().equals(b.texturePath())) throw new AssertionError("Same-skin players shared expression state");
            });
            context.setScreen(()->new ContinuousFaceGameTest.FacePreviewScreen(other));
            context.takeScreenshot("legacy-two-players-independent");
            context.setScreen(ContinuousFaceGameTest.FacePreviewScreen::new);
            var layout=LegacySkinFrames.detect(64,64,(x,y)->original[y*64+x]).orElseThrow();
            for(int direction : new int[]{-30,30}) {
                context.runOnClient(client -> {
                    var state=ExpressionStateManager.getInstance().get(self); state.reset(); state.setEyeAngles(direction,0);
                    assertFrame(client.player,LegacySkinFrames.eyes(original,layout,direction<0?1:2));
                });
                context.takeScreenshot("legacy-rai-look-"+(direction<0?"left":"right"));
            }
            context.runOnClient(client -> {
                ExpressionStateManager.getInstance().get(self).reset();
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS,100));
                assertFrame(client.player,LegacySkinFrames.eyes(original,layout,6));
            });
            context.takeScreenshot("legacy-rai-closed");
            context.runOnClient(client -> {
                client.player.removeStatusEffect(StatusEffects.BLINDNESS);
                var config=ModConfig.getInstance();config.enableBlinking=true;config.blinkIntervalMin=250;config.blinkIntervalMax=250;
                ExpressionStateManager.getInstance().get(self).reset();
            });
            // Observe a real automatic blink reaching the native renderer, without sleeping the render thread.
            context.waitFor(client -> {
                var asset=rendered(client.player);
                var image=((NativeImageBackedTexture)client.getTextureManager().getTexture(asset.texturePath())).getImage();
                return image.getColorArgb(9,12)==original[3*64+57];
            },100);
            context.takeScreenshot("legacy-rai-auto-blink");
            context.runOnClient(client -> {
                var config=ModConfig.getInstance();config.blinkIntervalMin=3000;config.blinkIntervalMax=6000;
            });
            context.setScreen(()->null);
        } finally { body=null; }
    }
    private static AssetInfo.TextureAsset rendered(AbstractClientPlayerEntity player) {
        var client=net.minecraft.client.MinecraftClient.getInstance();
        var renderer=(PlayerEntityRenderer)client.getEntityRenderDispatcher().getRenderer(player);
        var state=renderer.createRenderState();renderer.updateRenderState(player,state,0);
        if(((ExpressionRenderState)state).expressionmod$getFrame()!=null) throw new AssertionError("Ordinary skin got Shannon geometry");
        if(state.skinTextures.model()!=PlayerSkinType.SLIM) throw new AssertionError("Arm model changed");
        return state.skinTextures.body();
    }
    private static void assertFrame(AbstractClientPlayerEntity player,int[] expected) {
        var client=net.minecraft.client.MinecraftClient.getInstance();
        var asset=rendered(player);
        if(asset.texturePath().equals(body.texturePath())) throw new AssertionError("Legacy texture was not selected");
        var image=((NativeImageBackedTexture)client.getTextureManager().getTexture(asset.texturePath())).getImage();
        for(int y=0;y<64;y++) for(int x=0;x<64;x++) if(image.getColorArgb(x,y)!=expected[y*64+x])
            throw new AssertionError("Unexpected texture pixel at "+x+","+y);
    }
}
