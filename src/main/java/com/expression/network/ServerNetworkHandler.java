package com.expression.network;

import com.expression.ExpressionMod;
import com.expression.state.EmoteType;
import com.expression.state.EmoteState;
import com.expression.state.ExpressionState;
import com.expression.state.ExpressionStateManager;
import com.expression.state.DialogueActivity;
import com.expression.state.DialogueState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

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
            ServerPlayerEntity sender = context.player();

            context.server().execute(() -> {
                // 状態を更新
                // A client may change only its authenticated player's face, regardless of the claimed UUID/name.
                EmoteSyncPayload trusted = new EmoteSyncPayload(sender.getUuid(), sender.getName().getString(),
                        EmoteType.fromId(payload.emoteId()).getId(), EmoteState.boundedDuration(payload.durationMs()));
                ExpressionState state = ExpressionStateManager.server()
                        .getOrCreate(trusted.playerUuid(), trusted.playerName());
                state.setEmote(EmoteType.fromId(trusted.emoteId()), trusted.durationMs());

                // 他の全プレイヤーに転送
                for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
                    if (!player.equals(sender) && ServerPlayNetworking.canSend(player, EmoteSyncPayload.ID)) {
                        ServerPlayNetworking.send(player, trusted);
                    }
                }
            });
        });

        // 視線同期受信
        ServerPlayNetworking.registerGlobalReceiver(EyeSyncPayload.ID, (payload, context) -> {
            ServerPlayerEntity sender = context.player();

            context.server().execute(() -> {
                if (!Float.isFinite(payload.eyeYaw()) || !Float.isFinite(payload.eyePitch())
                        || (payload.hasTarget() && (!Double.isFinite(payload.targetX())
                        || !Double.isFinite(payload.targetY()) || !Double.isFinite(payload.targetZ())))) return;
                EyeSyncPayload trusted = new EyeSyncPayload(sender.getUuid(), sender.getName().getString(),
                        Math.clamp(payload.eyeYaw(), -30, 30), Math.clamp(payload.eyePitch(), -30, 30),
                        payload.hasTarget(), payload.targetX(), payload.targetY(), payload.targetZ());
                // 状態を更新
                ExpressionState state = ExpressionStateManager.server()
                        .getOrCreate(trusted.playerUuid(), trusted.playerName());

                state.setLookAtTarget(trusted.hasTarget() ? new Vec3d(trusted.targetX(), trusted.targetY(), trusted.targetZ()) : null);
                state.setEyeAngles(trusted.eyeYaw(), trusted.eyePitch());

                // 他の全プレイヤーに転送
                for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
                    if (!player.equals(sender) && ServerPlayNetworking.canSend(player, EyeSyncPayload.ID)) {
                        ServerPlayNetworking.send(player, trusted);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DialogueSyncPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (!Float.isFinite(payload.yaw()) || !Float.isFinite(payload.pitch())) return;
                var sender = context.player();
                var activity = DialogueActivity.fromId(payload.activity());
                var trusted = new DialogueSyncPayload(sender.getUuid(), sender.getName().getString(), activity.id(),
                        Math.clamp(payload.durationMs(), 250, DialogueState.MAX_DURATION_MS),
                        activity != DialogueActivity.IDLE && payload.hasTarget(),
                        Math.clamp(payload.yaw(), -30, 30), Math.clamp(payload.pitch(), -30, 30));
                ExpressionStateManager.server().getOrCreate(sender.getUuid(), sender.getName().getString())
                        .getDialogueState().update(activity, trusted.durationMs(), trusted.hasTarget(), trusted.yaw(), trusted.pitch());
                for (var player : context.server().getPlayerManager().getPlayerList())
                    if (!player.equals(sender) && ServerPlayNetworking.canSend(player, DialogueSyncPayload.ID))
                        ServerPlayNetworking.send(player, trusted);
            });
        });
        ExpressionMod.LOGGER.info("Server network receivers registered");
    }

    /**
     * 新規参加者に全プレイヤーの状態を送信
     */
    public static void sendAllStatesToPlayer(ServerPlayerEntity newPlayer) {
        if (!ServerPlayNetworking.canSend(newPlayer, FullStateSyncPayload.ID)) return;
        ExpressionStateManager manager = ExpressionStateManager.server();

        for (ExpressionState state : manager.getAllStates().values()) {
            FullStateSyncPayload payload = new FullStateSyncPayload(
                    state.getPlayerUuid(),
                    state.getPlayerName(),
                    state.getCurrentEmote().getId(),
                    state.getEmoteState().getRemainingTimeMs(),
                    state.getEyeState().getTargetYaw(),
                    state.getEyeState().getTargetPitch(),
                    state.getHeadYawOffset(),
                    state.getHeadPitchOffset(),
                    state.getBlinkState().isBlinking(),
                    state.getBlinkProgress(),
                    state.hasEyeTarget());

            ServerPlayNetworking.send(newPlayer, payload);
        }
        sendDialogueStatesToPlayer(newPlayer);
    }

    public static void sendDialogueStatesToPlayer(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, DialogueSyncPayload.ID)) return;
        for (var state : ExpressionStateManager.server().getAllStates().values()) {
            var dialogue = state.getDialogueState();
            ServerPlayNetworking.send(player, new DialogueSyncPayload(state.getPlayerUuid(), state.getPlayerName(),
                    dialogue.activity().id(), dialogue.remainingMs(), dialogue.hasTarget(), dialogue.yaw(), dialogue.pitch()));
        }
    }

    public static void sendBridgeReady(ServerPlayerEntity player) {
        if (ServerPlayNetworking.canSend(player, BridgeReadyPayload.ID))
            ServerPlayNetworking.send(player, new BridgeReadyPayload(1));
    }
}
