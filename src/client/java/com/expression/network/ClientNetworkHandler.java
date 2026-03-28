package com.expression.network;

import com.expression.ExpressionMod;
import com.expression.skin.EmoteManager;
import com.expression.state.EmoteType;
import com.expression.state.ExpressionState;
import com.expression.state.ExpressionStateManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * クライアント側のネットワークハンドラー
 */
public class ClientNetworkHandler {

    /**
     * クライアント側レシーバーを登録
     */
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(EmoteSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null && !client.player.getUUID().equals(payload.playerUuid())) {
                    String emoteId = payload.emoteId();

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
                        ExpressionState state = ExpressionStateManager.getInstance()
                                .getOrCreate(payload.playerUuid(), payload.playerName());
                        state.setEmote(EmoteType.fromId(emoteId), payload.durationMs());
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EyeSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null && !client.player.getUUID().equals(payload.playerUuid())) {
                    ExpressionState state = ExpressionStateManager.getInstance()
                            .getOrCreate(payload.playerUuid(), payload.playerName());

                    if (payload.hasTarget()) {
                        state.setLookAtTarget(new Vec3(payload.targetX(), payload.targetY(), payload.targetZ()));
                    }
                    state.setEyeAngles(payload.eyeYaw(), payload.eyePitch());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(FullStateSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null && !client.player.getUUID().equals(payload.playerUuid())) {
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
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.getConnection() != null) {
            EmoteSyncPayload payload = new EmoteSyncPayload(
                    client.player.getUUID(),
                    client.player.getName().getString(),
                    emote.getId(),
                    durationMs);
            ClientPlayNetworking.send(payload);
        }
    }

    /**
     * 視線変更をサーバーに送信
     */
    public static void sendEyeChange(float yaw, float pitch, Vec3 target) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.getConnection() != null) {
            EyeSyncPayload payload = new EyeSyncPayload(
                    client.player.getUUID(),
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
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.getConnection() != null) {
            String emoteId = "skin_emote_" + emoteIndex;
            EmoteSyncPayload payload = new EmoteSyncPayload(
                    client.player.getUUID(),
                    client.player.getName().getString(),
                    emoteId,
                    com.expression.skin.EmoteManager.EMOTE_DURATION_MS);
            ClientPlayNetworking.send(payload);
            ExpressionMod.LOGGER.info("[ClientNetworkHandler] Sent skin emote: " + emoteId);
        }
    }
}
