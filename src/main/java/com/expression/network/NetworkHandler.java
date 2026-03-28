package com.expression.network;

import com.expression.ExpressionMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;

public class NetworkHandler {
    public static final Identifier EMOTE_SYNC_ID = Identifier.fromNamespaceAndPath(ExpressionMod.MOD_ID, "emote_sync");
    public static final Identifier EYE_SYNC_ID = Identifier.fromNamespaceAndPath(ExpressionMod.MOD_ID, "eye_sync");
    public static final Identifier FULL_STATE_SYNC_ID = Identifier.fromNamespaceAndPath(ExpressionMod.MOD_ID, "full_state_sync");

    public static void registerPackets() {
        PayloadTypeRegistry.serverboundPlay().register(
                EmoteSyncPayload.ID,
                EmoteSyncPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                EyeSyncPayload.ID,
                EyeSyncPayload.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(
                EmoteSyncPayload.ID,
                EmoteSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                EyeSyncPayload.ID,
                EyeSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                FullStateSyncPayload.ID,
                FullStateSyncPayload.CODEC);

        ExpressionMod.LOGGER.info("Network packets registered");
    }
}
