package com.expression;

import com.expression.network.ClientNetworkHandler;
import com.expression.render.EyelidRenderer;
import com.expression.skin.BlinkController;
import com.expression.skin.BlinkTextureManager;
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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffects;
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

    @Override
    public void onInitializeClient() {
        // キーバインド登録 - Numpad 1-6 を使用
        smileKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.smile",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_1, // Numpad 1
                "category.expressionmod.emotes"));

        angryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.angry",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_2, // Numpad 2
                "category.expressionmod.emotes"));

        sadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.sad",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_3, // Numpad 3
                "category.expressionmod.emotes"));

        surprisedKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.surprised",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_4, // Numpad 4
                "category.expressionmod.emotes"));

        thinkKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.think",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_5, // Numpad 5
                "category.expressionmod.emotes"));

        shyKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.shy",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_6, // Numpad 6
                "category.expressionmod.emotes"));

        reloadCacheKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.expressionmod.reload_cache",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_0, // Numpad 0
                "category.expressionmod.emotes"));

        // クライアントtickイベント - 目の状態を更新
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // キー入力処理
            processKeyInput(client);

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

        // クライアントネットワークハンドラー登録
        ClientNetworkHandler.registerClientReceivers();

        // サーバー切断時に状態をクリア
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ExpressionStateManager.getInstance().clear();
            EyelidRenderer.clearAll();
            BlinkTextureManager.clearCache();
            EyelidTextureData.clearAllCaches();
            EyeStateController.reset();
        });

        ExpressionMod.LOGGER.info("Expression Mod Client initialized!");
        ExpressionMod.LOGGER.info("Emote keys: Numpad 1-6, Cache clear: Numpad 0");
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
                var skinTexture = player.getSkinTextures().texture();
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

            // フィードバック
            client.player.sendMessage(
                    net.minecraft.text.Text.literal("§e[ExpressionMod] " + emote.getId()),
                    true);
        }
    }
}
