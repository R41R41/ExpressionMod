package com.expression.network;

import com.expression.ExpressionMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;

/**
 * ネットワークパケットの登録を行うクラス
 */
public class NetworkHandler {
    // パケット識別子
    public static final Identifier EMOTE_SYNC_ID = Identifier.of(ExpressionMod.MOD_ID, "emote_sync");
    public static final Identifier EYE_SYNC_ID = Identifier.of(ExpressionMod.MOD_ID, "eye_sync");
    public static final Identifier FULL_STATE_SYNC_ID = Identifier.of(ExpressionMod.MOD_ID, "full_state_sync");

    /**
     * パケットタイプを登録
     */
    public static void registerPackets() {
        // Client -> Server (C2S)
        PayloadTypeRegistry.playC2S().register(
                EmoteSyncPayload.ID,
                EmoteSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                EyeSyncPayload.ID,
                EyeSyncPayload.CODEC);

        // Server -> Client (S2C)
        PayloadTypeRegistry.playS2C().register(
                EmoteSyncPayload.ID,
                EmoteSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                EyeSyncPayload.ID,
                EyeSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                FullStateSyncPayload.ID,
                FullStateSyncPayload.CODEC);

        ExpressionMod.LOGGER.info("Network packets registered");
    }
}
