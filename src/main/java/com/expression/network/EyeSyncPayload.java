package com.expression.network;

import com.expression.ExpressionMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * 視線同期用ペイロード
 */
public record EyeSyncPayload(
        UUID playerUuid,
        String playerName,
        float eyeYaw,
        float eyePitch,
        boolean hasTarget,
        double targetX,
        double targetY,
        double targetZ) implements CustomPayload {

    public static final CustomPayload.Id<EyeSyncPayload> ID = new CustomPayload.Id<>(
            Identifier.of(ExpressionMod.MOD_ID, "eye_sync"));

    public static final PacketCodec<RegistryByteBuf, EyeSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString), EyeSyncPayload::playerUuid,
            PacketCodecs.STRING, EyeSyncPayload::playerName,
            PacketCodecs.FLOAT, EyeSyncPayload::eyeYaw,
            PacketCodecs.FLOAT, EyeSyncPayload::eyePitch,
            PacketCodecs.BOOLEAN, EyeSyncPayload::hasTarget,
            PacketCodecs.DOUBLE, EyeSyncPayload::targetX,
            PacketCodecs.DOUBLE, EyeSyncPayload::targetY,
            PacketCodecs.DOUBLE, EyeSyncPayload::targetZ,
            EyeSyncPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
