package com.expression.network;

import com.expression.ExpressionMod;
import com.expression.skin.EmoteManager;
import com.expression.state.EmoteType;
import com.expression.state.ExpressionState;
import com.expression.state.ExpressionStateManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * クライアント側のネットワークハンドラー
 */
public class ClientNetworkHandler {

    /**
     * クライアント側レシーバーを登録
     */
    public static void registerClientReceivers() {
        // エモート同期受信
        ClientPlayNetworking.registerGlobalReceiver(EmoteSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                // 自分以外のプレイヤーの状態を更新
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && !client.player.getUuid().equals(payload.playerUuid())) {
                    String emoteId = payload.emoteId();

                    // スキンベースのエモートかチェック
                    if (emoteId != null && emoteId.startsWith("skin_emote_")) {
                        try {
                            int emoteIndex = Integer.parseInt(emoteId.substring("skin_emote_".length()));
                            EmoteManager.startEmote(payload.playerUuid(), emoteIndex);
                            ExpressionMod.LOGGER.info("[ClientNetworkHandler] Received skin emote " + emoteIndex +
                                    " from " + payload.playerName());
                        } catch (NumberFormatException e) {
                            ExpressionMod.LOGGER.warn("[ClientNetworkHandler] Invalid skin emote ID: " + emoteId);
                        }
                    } else {
                        // 通常のエモート
                        ExpressionState state = ExpressionStateManager.getInstance()
                                .getOrCreate(payload.playerUuid(), payload.playerName());
                        state.setEmote(EmoteType.fromId(emoteId), payload.durationMs());
                    }
                }
            });
        });

        // 視線同期受信
        ClientPlayNetworking.registerGlobalReceiver(EyeSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && !client.player.getUuid().equals(payload.playerUuid())) {
                    ExpressionState state = ExpressionStateManager.getInstance()
                            .getOrCreate(payload.playerUuid(), payload.playerName());

                    if (payload.hasTarget()) {
                        state.setLookAtTarget(new Vec3d(payload.targetX(), payload.targetY(), payload.targetZ()));
                    }
                    state.setEyeAngles(payload.eyeYaw(), payload.eyePitch());
                }
            });
        });

        // 全状態同期受信（新規参加時）
        ClientPlayNetworking.registerGlobalReceiver(FullStateSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && !client.player.getUuid().equals(payload.playerUuid())) {
                    ExpressionState state = ExpressionStateManager.getInstance()
                            .getOrCreate(payload.playerUuid(), payload.playerName());

                    state.setEmote(EmoteType.fromId(payload.emoteId()), payload.emoteRemainingMs());
                    state.setEyeAngles(payload.eyeYaw(), payload.eyePitch());
                }
            });
        });

        ExpressionMod.LOGGER.info("Client network receivers registered");
    }

    /**
     * エモート変更をサーバーに送信
     */
    public static void sendEmoteChange(EmoteType emote, long durationMs) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.getNetworkHandler() != null) {
            EmoteSyncPayload payload = new EmoteSyncPayload(
                    client.player.getUuid(),
                    client.player.getName().getString(),
                    emote.getId(),
                    durationMs);
            ClientPlayNetworking.send(payload);
        }
    }

    /**
     * 視線変更をサーバーに送信
     */
    public static void sendEyeChange(float yaw, float pitch, Vec3d target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.getNetworkHandler() != null) {
            EyeSyncPayload payload = new EyeSyncPayload(
                    client.player.getUuid(),
                    client.player.getName().getString(),
                    yaw,
                    pitch,
                    target != null,
                    target != null ? target.x : 0,
                    target != null ? target.y : 0,
                    target != null ? target.z : 0);
            ClientPlayNetworking.send(payload);
        }
    }

    /**
     * スキンベースのエモートをサーバーに送信
     * 
     * @param emoteIndex エモートインデックス（0-4）
     */
    public static void sendEmoteSync(int emoteIndex) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.getNetworkHandler() != null) {
            // スキンエモートはemoteIdを "skin_emote_X" の形式で送信
            String emoteId = "skin_emote_" + emoteIndex;
            EmoteSyncPayload payload = new EmoteSyncPayload(
                    client.player.getUuid(),
                    client.player.getName().getString(),
                    emoteId,
                    com.expression.skin.EmoteManager.EMOTE_DURATION_MS);
            ClientPlayNetworking.send(payload);
            ExpressionMod.LOGGER.info("[ClientNetworkHandler] Sent skin emote: " + emoteId);
        }
    }
}
