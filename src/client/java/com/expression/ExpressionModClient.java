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
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * クライアント側エントリポイント
 */
public class ExpressionModClient implements ClientModInitializer {

    // キーバインド - Numpadキーを使用（衝突回避）
    private static KeyBinding smileKey;
    private static KeyBinding angryKey;
    private static KeyBinding sadKey;
    private static KeyBinding surprisedKey;
    private static KeyBinding thinkKey;
    private static KeyBinding shyKey;
    private static KeyBinding reloadCacheKey;

    // エモート選択UI用
    private static boolean wasRightMousePressed = false;
    private static boolean emoteMenuOpened = false;
    private static NativeImage cachedSkinImage = null;

    @Override
    public void onInitializeClient() {
        // キーバインド登録 - Numpad 1-6 を使用
        KeyBinding.Category emoteCategory = new KeyBinding.Category(
                Identifier.of(ExpressionMod.MOD_ID, "emotes"));

        smileKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.smile",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_1, // Numpad 1
                emoteCategory));

        angryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.angry",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_2, // Numpad 2
                emoteCategory));

        sadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.sad",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_3, // Numpad 3
                emoteCategory));

        surprisedKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.surprised",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_4, // Numpad 4
                emoteCategory));

        thinkKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.think",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_5, // Numpad 5
                emoteCategory));

        shyKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.shy",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_6, // Numpad 6
                emoteCategory));

        reloadCacheKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.reload_cache",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_0, // Numpad 0
                emoteCategory));

        // クライアントtickイベント - 目の状態を更新
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // キー入力処理
            processKeyInput(client);

            // エモート選択UI処理
            processEmoteSelection(client);

            // 目の状態を更新（まばたき + アイトラッキング + 睡眠/盲目）
            EyeStateController.update();

            // ワールド内のプレイヤーのまばたき状態を更新（旧システム互換）
            if (client.world != null) {
                for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
                    updatePlayerBlink(player);
                }
            }

            // 自分のExpressionStateを更新
            if (client.player != null) {
                ExpressionState state = ExpressionStateManager.getInstance()
                        .getOrCreate(client.player.getUuid(), client.player.getName().getString());
                state.tick();
            }
        });

        // HUD描画イベント - エモート選択UIを描画
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of(ExpressionMod.MOD_ID, "emote_selection"),
                (context, tickDelta) -> EmoteSelectionScreen.render(context)
        );

        // クライアントネットワークハンドラー登録
        ClientNetworkHandler.registerClientReceivers();

        // サーバー切断時に状態をクリア
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
    private void processEmoteSelection(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null) {
            // 画面が開いている場合はエモートメニューを閉じる
            if (EmoteSelectionScreen.isActive()) {
                EmoteSelectionScreen.close(false);
                emoteMenuOpened = false;
                wasRightMousePressed = false;
            }
            return;
        }

        long windowHandle = client.getWindow().getHandle();
        boolean isCtrlPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean isRightMousePressed = GLFW.glfwGetMouseButton(windowHandle,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        // マウス位置を取得
        double[] mouseX = new double[1];
        double[] mouseY = new double[1];
        GLFW.glfwGetCursorPos(windowHandle, mouseX, mouseY);
        int scaledMouseX = (int) (mouseX[0] * client.getWindow().getScaledWidth() / client.getWindow().getWidth());
        int scaledMouseY = (int) (mouseY[0] * client.getWindow().getScaledHeight() / client.getWindow().getHeight());

        // Ctrl + 右クリック開始でメニューを開く
        if (isCtrlPressed && isRightMousePressed && !wasRightMousePressed && !emoteMenuOpened) {
            // スキンテクスチャからエモートデータを取得
            Identifier skinTexture = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()).getSkinTextures().body().texturePath();
            BlinkTextureManager.BlinkTextureData data = BlinkTextureManager.getOrCreate(skinTexture);

            ExpressionMod.LOGGER.info("[ExpressionModClient] Ctrl+Click detected, data=" + data +
                    ", hasAnyEmote=" + (data != null ? data.hasAnyEmote() : "null"));

            if (data != null && data.hasAnyEmote()) {
                // スキン画像を読み込んでエモートメニューを開く
                try {
                    cachedSkinImage = loadSkinImageForEmote(skinTexture);
                    if (cachedSkinImage != null) {
                        EmoteSelectionScreen.open(scaledMouseX, scaledMouseY, cachedSkinImage, data.emoteData);
                        emoteMenuOpened = true;
                        // マウスカーソルを表示
                        client.mouse.unlockCursor();
                        ExpressionMod.LOGGER.info("[ExpressionModClient] Emote menu opened, cursor unlocked");
                    }
                } catch (Exception e) {
                    ExpressionMod.LOGGER.error("[ExpressionModClient] Error opening emote menu", e);
                }
            }
        }

        // メニューが開いている間はマウス位置を更新
        if (EmoteSelectionScreen.isActive()) {
            EmoteSelectionScreen.updateMousePosition(scaledMouseX, scaledMouseY);
        }

        // 右クリックを離したらメニューを閉じて選択確定
        if (emoteMenuOpened && wasRightMousePressed && !isRightMousePressed) {
            EmoteSelectionScreen.close(true);
            emoteMenuOpened = false;
            // カーソルをロック
            client.mouse.lockCursor();
            if (cachedSkinImage != null) {
                cachedSkinImage.close();
                cachedSkinImage = null;
            }
        }

        // Ctrlを離したらキャンセル
        if (emoteMenuOpened && !isCtrlPressed) {
            EmoteSelectionScreen.close(false);
            emoteMenuOpened = false;
            // カーソルをロック
            client.mouse.lockCursor();
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
            var client = MinecraftClient.getInstance();
            var textureManager = client.getTextureManager();
            var texture = textureManager.getTexture(skinTexture);

            if (texture instanceof net.minecraft.client.texture.NativeImageBackedTexture nativeTexture) {
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
            ExpressionMod.LOGGER.debug("[ExpressionModClient] Could not load skin: " + skinTexture, e);
        }
        return null;
    }

    /**
     * プレイヤーのまばたき状態を更新
     */
    private void updatePlayerBlink(AbstractClientPlayerEntity player) {
        try {
            UUID playerUuid = player.getUuid();

            // まばたき状態を更新
            boolean isSleeping = player.isSleeping();
            boolean isBlind = player.hasStatusEffect(StatusEffects.BLINDNESS);
            BlinkController.update(playerUuid, isSleeping, isBlind);

            // スキンテクスチャからまぶたデータを初期化（一度だけ）
            if (EyelidTextureData.get(playerUuid) == null) {
                var entry = MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(player.getUuid());
                if (entry == null) return;
                var skinTexture = entry.getSkinTextures().body().texturePath();
                EyelidTextureData.getOrCreate(playerUuid, skinTexture);
            }
        } catch (Exception e) {
            // エラーは無視
        }
    }

    /**
     * キー入力を処理してエモートを発動
     */
    private void processKeyInput(MinecraftClient client) {
        if (client.player == null)
            return;

        // F3（デバッグ）キーが押されている場合はエモートキーを処理しない
        // F3+数字キーはMinecraftのデバッグショートカットと競合するため
        long windowHandle = client.getWindow().getHandle();
        boolean isF3Pressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS;
        if (isF3Pressed) {
            // wasPressed() を消費して、F3リリース後に誤発動しないようにする
            while (smileKey.wasPressed()) {}
            while (angryKey.wasPressed()) {}
            while (sadKey.wasPressed()) {}
            while (surprisedKey.wasPressed()) {}
            while (thinkKey.wasPressed()) {}
            while (shyKey.wasPressed()) {}
            while (reloadCacheKey.wasPressed()) {}
            return;
        }

        EmoteType emote = null;

        while (smileKey.wasPressed()) {
            emote = EmoteType.SMILE;
        }
        while (angryKey.wasPressed()) {
            emote = EmoteType.ANGRY;
        }
        while (sadKey.wasPressed()) {
            emote = EmoteType.SAD;
        }
        while (surprisedKey.wasPressed()) {
            emote = EmoteType.SURPRISED;
        }
        while (thinkKey.wasPressed()) {
            emote = EmoteType.THINK;
        }
        while (shyKey.wasPressed()) {
            emote = EmoteType.SHY;
        }

        // キャッシュ再読み込みキー
        while (reloadCacheKey.wasPressed()) {
            ExpressionMod.LOGGER.info("Clearing expression cache...");
            EyelidRenderer.clearAll();
            BlinkTextureManager.clearCache();
            EmoteManager.clearCache();
            EyelidTextureData.clearAllCaches();
            if (client.player != null) {
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§a[ExpressionMod] キャッシュをクリアしました"),
                        true);
            }
        }

        if (emote != null) {
            // 自分の状態を更新
            ExpressionState state = ExpressionStateManager.getInstance()
                    .getOrCreate(client.player.getUuid(), client.player.getName().getString());
            state.setEmote(emote, EmoteState.DEFAULT_DURATION_MS);

            // サーバーに送信
            ClientNetworkHandler.sendEmoteChange(emote, EmoteState.DEFAULT_DURATION_MS);

            ExpressionMod.LOGGER.info("Emote triggered: " + emote.getId());
        }
    }
}
