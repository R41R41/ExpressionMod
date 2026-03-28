package com.expression.network;

import com.expression.ExpressionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record EyeSyncPayload(
        UUID playerUuid,
        String playerName,
        float eyeYaw,
        float eyePitch,
        boolean hasTarget,
        double targetX,
        double targetY,
        double targetZ) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EyeSyncPayload> ID = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(ExpressionMod.MOD_ID, "eye_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EyeSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), EyeSyncPayload::playerUuid,
            ByteBufCodecs.STRING_UTF8, EyeSyncPayload::playerName,
            ByteBufCodecs.FLOAT, EyeSyncPayload::eyeYaw,
            ByteBufCodecs.FLOAT, EyeSyncPayload::eyePitch,
            ByteBufCodecs.BOOL, EyeSyncPayload::hasTarget,
            ByteBufCodecs.DOUBLE, EyeSyncPayload::targetX,
            ByteBufCodecs.DOUBLE, EyeSyncPayload::targetY,
            ByteBufCodecs.DOUBLE, EyeSyncPayload::targetZ,
            EyeSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
