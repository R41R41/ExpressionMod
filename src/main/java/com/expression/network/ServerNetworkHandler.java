package com.expression.network;

import com.expression.ExpressionMod;
import com.expression.state.EmoteType;
import com.expression.state.ExpressionState;
import com.expression.state.ExpressionStateManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * サーバー側のネットワークハンドラー
 * クライアントからのパケットを受信し、他のクライアントに転送
 */
public class ServerNetworkHandler {

    /**
     * サーバー側レシーバーを登録
     */
    public static void registerServerReceivers() {
        // エモート同期受信
        ServerPlayNetworking.registerGlobalReceiver(EmoteSyncPayload.ID, (payload, context) -> {
            ServerPlayer sender = context.player();

            context.server().execute(() -> {
                // 状態を更新
                ExpressionState state = ExpressionStateManager.getInstance()
                        .getOrCreate(payload.playerUuid(), payload.playerName());
                state.setEmote(EmoteType.fromId(payload.emoteId()), payload.durationMs());

                // 他の全プレイヤーに転送
                for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
                    if (!player.equals(sender)) {
                        ServerPlayNetworking.send(player, payload);
                    }
                }
            });
        });

        // 視線同期受信
        ServerPlayNetworking.registerGlobalReceiver(EyeSyncPayload.ID, (payload, context) -> {
            ServerPlayer sender = context.player();

            context.server().execute(() -> {
                // 状態を更新
                ExpressionState state = ExpressionStateManager.getInstance()
                        .getOrCreate(payload.playerUuid(), payload.playerName());

                if (payload.hasTarget()) {
                    state.setLookAtTarget(new Vec3(payload.targetX(), payload.targetY(), payload.targetZ()));
                }
                state.setEyeAngles(payload.eyeYaw(), payload.eyePitch());

                // 他の全プレイヤーに転送
                for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
                    if (!player.equals(sender)) {
                        ServerPlayNetworking.send(player, payload);
                    }
                }
            });
        });

        ExpressionMod.LOGGER.info("Server network receivers registered");
    }

    /**
     * 新規参加者に全プレイヤーの状態を送信
     */
    public static void sendAllStatesToPlayer(ServerPlayer newPlayer) {
        ExpressionStateManager manager = ExpressionStateManager.getInstance();

        for (ExpressionState state : manager.getAllStates().values()) {
            FullStateSyncPayload payload = new FullStateSyncPayload(
                    state.getPlayerUuid(),
                    state.getPlayerName(),
                    state.getCurrentEmote().getId(),
                    state.getEmoteState().getRemainingTimeMs(),
                    state.getEyeYaw(),
                    state.getEyePitch(),
                    state.getHeadYawOffset(),
                    state.getHeadPitchOffset(),
                    state.getBlinkState().isBlinking(),
                    state.getBlinkProgress());

            ServerPlayNetworking.send(newPlayer, payload);
        }
    }
}
