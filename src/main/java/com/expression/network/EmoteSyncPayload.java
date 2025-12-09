package com.expression.network;

import com.expression.ExpressionMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * エモート同期用ペイロード
 */
public record EmoteSyncPayload(
        UUID playerUuid,
        String playerName,
        String emoteId,
        long durationMs) implements CustomPayload {

    public static final CustomPayload.Id<EmoteSyncPayload> ID = new CustomPayload.Id<>(
            Identifier.of(ExpressionMod.MOD_ID, "emote_sync"));

    public static final PacketCodec<RegistryByteBuf, EmoteSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), EmoteSyncPayload::playerUuid,
            PacketCodecs.STRING, EmoteSyncPayload::playerName,
            PacketCodecs.STRING, EmoteSyncPayload::emoteId,
            PacketCodecs.VAR_LONG, EmoteSyncPayload::durationMs,
            EmoteSyncPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
