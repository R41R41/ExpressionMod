package com.expression.network;

import com.expression.ExpressionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record EmoteSyncPayload(
        UUID playerUuid,
        String playerName,
        String emoteId,
        long durationMs) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EmoteSyncPayload> ID = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(ExpressionMod.MOD_ID, "emote_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EmoteSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), EmoteSyncPayload::playerUuid,
            ByteBufCodecs.STRING_UTF8, EmoteSyncPayload::playerName,
            ByteBufCodecs.STRING_UTF8, EmoteSyncPayload::emoteId,
            ByteBufCodecs.VAR_LONG, EmoteSyncPayload::durationMs,
            EmoteSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
