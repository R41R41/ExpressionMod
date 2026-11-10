package com.expression.test;

import com.expression.ModMenuIntegration;
import com.expression.config.EmoteSlotNames;
import com.expression.config.ModConfig;
import com.expression.render.LegacyEmoteScreen;
import com.expression.skin.LegacySkinTextures;
import com.expression.test.mixin.SkinDownloadInvoker;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.net.Proxy;

/** The expression picker and settings show the names this player gave the painted slots, and editing saves them. */
public final class EmoteNamesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        PreviewSkin.clear();
        var upload = context.computeOnClient(client -> {
            NativeImage image;
            try (var stream = client.getResourceManager().getResource(Identifier.of("expressionmod_test", "textures/legacy-fixture.png")).orElseThrow().getInputStream()) {
                image = NativeImage.read(stream);
            } catch (IOException error) { throw new AssertionError(error); }
            var asset = new AssetInfo.TextureAssetInfo(Identifier.of("expressionmod_test", "names-uploaded"));
            return ((SkinDownloadInvoker)new PlayerSkinTextureDownloader(Proxy.NO_PROXY, client.getTextureManager(), client))
                    .expressionmod$register(asset, image);
        });
        context.waitFor(client -> upload.isDone());
        Identifier skin = upload.join().texturePath();
        String[] saved = context.computeOnClient(client -> ModConfig.getInstance().emoteSlotNames.clone());
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.world != null);
            context.runOnClient(client -> {
                if (!LegacySkinTextures.hasEmote(skin, 0)) throw new AssertionError("Fixture lost its painted slots");
                ModConfig.getInstance().emoteSlotNames = new String[]{"ウインク", "ジト目", "", "びっくり", "ドヤ顔"};
            });
            context.runOnClient(client -> {
                client.getLanguageManager().setLanguage("ja_jp");
                client.options.language = "ja_jp";
            });
            // The client only ticks while this thread waits, so never block on the reload future here.
            var reload = context.computeOnClient(client -> client.reloadResources());
            // The reload's fade-out overlay swallows key input until it is gone.
            context.waitFor(client -> reload.isDone() && client.getOverlay() == null, 600);
            context.setScreen(() -> new LegacyEmoteScreen(false, skin));
            context.takeScreenshot("names-picker");
            context.setScreen(() -> new LegacyEmoteScreen(true, skin));
            context.takeScreenshot("names-picker-edit");
            context.runOnClient(client -> {
                // Focus the first name field the way a click would; the language reload can leave focus elsewhere.
                var field = (net.minecraft.client.gui.widget.TextFieldWidget) client.currentScreen.children().get(0);
                client.currentScreen.setFocused(field);
                field.setCursorToEnd(false);
            });
            context.waitTicks(1);
            context.getInput().typeChars("Wink");
            context.runOnClient(client -> ((LegacyEmoteScreen) client.currentScreen).saveNames());
            context.runOnClient(client -> {
                String first = ModConfig.getInstance().slotName(0);
                if (!first.endsWith("Wink")) throw new AssertionError("Typed name was not saved: " + first);
                if (!ModConfig.getInstance().slotName(2).equals("表情 3")) throw new AssertionError("Blank name must fall back");
            });
            context.takeScreenshot("names-picker-after-save");
            context.setScreen(() -> ModMenuIntegration.create(null, true));
            context.takeScreenshot("names-settings-tab");
            context.setScreen(() -> ModMenuIntegration.create(null, false));
            context.takeScreenshot("names-settings-general");
            context.setScreen(() -> null);
        } finally {
            context.runOnClient(client -> {
                var config = ModConfig.getInstance();
                config.emoteSlotNames = EmoteSlotNames.normalize(saved);
                config.save();
            });
        }
    }
}
