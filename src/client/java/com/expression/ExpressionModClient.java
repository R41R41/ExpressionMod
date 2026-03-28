package com.expression;

import com.expression.network.ClientNetworkHandler;
import com.expression.render.EmoteSelectionScreen;
import com.expression.render.EyelidRenderer;
import com.expression.skin.BlinkController;
import com.expression.skin.BlinkTextureManager;
import com.expression.skin.EmoteManager;
import com.expression.skin.EyeStateController;
import com.expression.skin.EyelidTextureData;
import com.expression.state.EmoteState;
import com.expression.state.EmoteType;
import com.expression.state.ExpressionState;
import com.expression.state.ExpressionStateManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * クライアント側エントリポイント
 */
public class ExpressionModClient implements ClientModInitializer {

    private static KeyMapping smileKey;
    private static KeyMapping angryKey;
    private static KeyMapping sadKey;
    private static KeyMapping surprisedKey;
    private static KeyMapping thinkKey;
    private static KeyMapping shyKey;
    private static KeyMapping reloadCacheKey;

    private static boolean wasRightMousePressed = false;
    private static boolean emoteMenuOpened = false;
    private static NativeImage cachedSkinImage = null;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category emoteCategory = new KeyMapping.Category(
                Identifier.fromNamespaceAndPath(ExpressionMod.MOD_ID, "emotes"));

        smileKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.expressionmod.smile",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_1,
                emoteCategory));

        angryKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.expressionmod.angry",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_2,
                emoteCategory));

        sadKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.expressionmod.sad",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_3,
                emoteCategory));

        surprisedKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.expressionmod.surprised",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_4,
                emoteCategory));

        thinkKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.expressionmod.think",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_5,
                emoteCategory));

        shyKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.expressionmod.shy",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_6,
                emoteCategory));

        reloadCacheKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.expressionmod.reload_cache",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_0,
                emoteCategory));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            processKeyInput(client);
            processEmoteSelection(client);
            EyeStateController.update();

            if (client.level != null) {
                for (AbstractClientPlayer player : client.level.players()) {
                    updatePlayerBlink(player);
                }
            }

            if (client.player != null) {
                ExpressionState state = ExpressionStateManager.getInstance()
                        .getOrCreate(client.player.getUUID(), client.player.getName().getString());
                state.tick();
            }
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(ExpressionMod.MOD_ID, "emote_selection"),
                (guiGraphics, tickDelta) -> EmoteSelectionScreen.render(guiGraphics)
        );

        ClientNetworkHandler.registerClientReceivers();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ExpressionStateManager.getInstance().clear();
            EyelidRenderer.clearAll();
            BlinkTextureManager.clearCache();
            EmoteManager.clearCache();
            EyelidTextureData.clearAllCaches();
            EyeStateController.reset();
        });

        ExpressionMod.LOGGER.info("Expression Mod Client initialized!");
        ExpressionMod.LOGGER.info("Emote keys: Numpad 1-6, Cache clear: Numpad 0");
        ExpressionMod.LOGGER.info("Emote selection: Ctrl + Right Click");
    }

    /**
     * エモート選択UIを処理
     */
    private void processEmoteSelection(Minecraft client) {
        if (client.player == null || client.screen != null) {
            if (EmoteSelectionScreen.isActive()) {
                EmoteSelectionScreen.close(false);
                emoteMenuOpened = false;
                wasRightMousePressed = false;
            }
            return;
        }

        long windowHandle = client.getWindow().handle();
        boolean isCtrlPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean isRightMousePressed = GLFW.glfwGetMouseButton(windowHandle,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        double[] mouseX = new double[1];
        double[] mouseY = new double[1];
        GLFW.glfwGetCursorPos(windowHandle, mouseX, mouseY);
        int scaledMouseX = (int) (mouseX[0] * client.getWindow().getGuiScaledWidth() / client.getWindow().getWidth());
        int scaledMouseY = (int) (mouseY[0] * client.getWindow().getGuiScaledHeight() / client.getWindow().getHeight());

        if (isCtrlPressed && isRightMousePressed && !wasRightMousePressed && !emoteMenuOpened) {
            // TODO: Verify skin texture accessor chain for 26.1 Mojang mappings
            Identifier skinTexture = client.getConnection().getPlayerInfo(client.player.getUUID()).getSkin().body().texturePath();
            BlinkTextureManager.BlinkTextureData data = BlinkTextureManager.getOrCreate(skinTexture);

            ExpressionMod.LOGGER.info("[ExpressionModClient] Ctrl+Click detected, data=" + data +
                    ", hasAnyEmote=" + (data != null ? data.hasAnyEmote() : "null"));

            if (data != null && data.hasAnyEmote()) {
                try {
                    cachedSkinImage = loadSkinImageForEmote(skinTexture);
                    if (cachedSkinImage != null) {
                        EmoteSelectionScreen.open(scaledMouseX, scaledMouseY, cachedSkinImage, data.emoteData);
                        emoteMenuOpened = true;
                        client.mouseHandler.releaseMouse();
                        ExpressionMod.LOGGER.info("[ExpressionModClient] Emote menu opened, cursor unlocked");
                    }
                } catch (Exception e) {
                    ExpressionMod.LOGGER.error("[ExpressionModClient] Error opening emote menu", e);
                }
            }
        }

        if (EmoteSelectionScreen.isActive()) {
            EmoteSelectionScreen.updateMousePosition(scaledMouseX, scaledMouseY);
        }

        if (emoteMenuOpened && wasRightMousePressed && !isRightMousePressed) {
            EmoteSelectionScreen.close(true);
            emoteMenuOpened = false;
            client.mouseHandler.grabMouse();
            if (cachedSkinImage != null) {
                cachedSkinImage.close();
                cachedSkinImage = null;
            }
        }

        if (emoteMenuOpened && !isCtrlPressed) {
            EmoteSelectionScreen.close(false);
            emoteMenuOpened = false;
            client.mouseHandler.grabMouse();
            if (cachedSkinImage != null) {
                cachedSkinImage.close();
                cachedSkinImage = null;
            }
        }

        wasRightMousePressed = isRightMousePressed;
    }

    /**
     * エモート用にスキン画像を読み込む
     */
    @org.jetbrains.annotations.Nullable
    private NativeImage loadSkinImageForEmote(Identifier skinTexture) {
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
            ExpressionMod.LOGGER.debug("[ExpressionModClient] Could not load skin: " + skinTexture, e);
        }
        return null;
    }

    /**
     * プレイヤーのまばたき状態を更新
     */
    private void updatePlayerBlink(AbstractClientPlayer player) {
        try {
            UUID playerUuid = player.getUUID();

            boolean isSleeping = player.isSleeping();
            boolean isBlind = player.hasEffect(MobEffects.BLINDNESS);
            BlinkController.update(playerUuid, isSleeping, isBlind);

            if (EyelidTextureData.get(playerUuid) == null) {
                // TODO: Verify skin texture accessor chain for 26.1 Mojang mappings
                var entry = Minecraft.getInstance().getConnection().getPlayerInfo(player.getUUID());
                if (entry == null) return;
                var skinTexture = entry.getSkin().body().texturePath();
                EyelidTextureData.getOrCreate(playerUuid, skinTexture);
            }
        } catch (Exception e) {
            // エラーは無視
        }
    }

    /**
     * キー入力を処理してエモートを発動
     */
    private void processKeyInput(Minecraft client) {
        if (client.player == null)
            return;

        long windowHandle = client.getWindow().handle();
        boolean isF3Pressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS;
        if (isF3Pressed) {
            while (smileKey.consumeClick()) {}
            while (angryKey.consumeClick()) {}
            while (sadKey.consumeClick()) {}
            while (surprisedKey.consumeClick()) {}
            while (thinkKey.consumeClick()) {}
            while (shyKey.consumeClick()) {}
            while (reloadCacheKey.consumeClick()) {}
            return;
        }

        EmoteType emote = null;

        while (smileKey.consumeClick()) {
            emote = EmoteType.SMILE;
        }
        while (angryKey.consumeClick()) {
            emote = EmoteType.ANGRY;
        }
        while (sadKey.consumeClick()) {
            emote = EmoteType.SAD;
        }
        while (surprisedKey.consumeClick()) {
            emote = EmoteType.SURPRISED;
        }
        while (thinkKey.consumeClick()) {
            emote = EmoteType.THINK;
        }
        while (shyKey.consumeClick()) {
            emote = EmoteType.SHY;
        }

        while (reloadCacheKey.consumeClick()) {
            ExpressionMod.LOGGER.info("Clearing expression cache...");
            EyelidRenderer.clearAll();
            BlinkTextureManager.clearCache();
            EmoteManager.clearCache();
            EyelidTextureData.clearAllCaches();
            if (client.player != null) {
                client.player.sendOverlayMessage(
                        Component.literal("§a[ExpressionMod] キャッシュをクリアしました"));
            }
        }

        if (emote != null) {
            ExpressionState state = ExpressionStateManager.getInstance()
                    .getOrCreate(client.player.getUUID(), client.player.getName().getString());
            state.setEmote(emote, EmoteState.DEFAULT_DURATION_MS);

            ClientNetworkHandler.sendEmoteChange(emote, EmoteState.DEFAULT_DURATION_MS);

            ExpressionMod.LOGGER.info("Emote triggered: " + emote.getId());
        }
    }
}
