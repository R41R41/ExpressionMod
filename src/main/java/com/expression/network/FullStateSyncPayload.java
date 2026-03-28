package com.expression.network;

import com.expression.ExpressionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record FullStateSyncPayload(
        UUID playerUuid,
        String playerName,
        String emoteId,
        long emoteRemainingMs,
        float eyeYaw,
        float eyePitch,
        float headYawOffset,
        float headPitchOffset,
        boolean isBlinking,
        float blinkProgress) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FullStateSyncPayload> ID = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(ExpressionMod.MOD_ID, "full_state_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FullStateSyncPayload> CODEC = new StreamCodec<>() {
        @Override
        public FullStateSyncPayload decode(RegistryFriendlyByteBuf buf) {
            UUID uuid = UUID.fromString(buf.readUtf());
            String name = buf.readUtf();
            String emote = buf.readUtf();
            long remaining = buf.readVarLong();
            float eyeY = buf.readFloat();
            float eyeP = buf.readFloat();
            float headY = buf.readFloat();
            float headP = buf.readFloat();
            boolean blink = buf.readBoolean();
            float blinkProg = buf.readFloat();
            return new FullStateSyncPayload(uuid, name, emote, remaining, eyeY, eyeP, headY, headP, blink, blinkProg);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, FullStateSyncPayload payload) {
            buf.writeUtf(payload.playerUuid().toString());
            buf.writeUtf(payload.playerName());
            buf.writeUtf(payload.emoteId());
            buf.writeVarLong(payload.emoteRemainingMs());
            buf.writeFloat(payload.eyeYaw());
            buf.writeFloat(payload.eyePitch());
            buf.writeFloat(payload.headYawOffset());
            buf.writeFloat(payload.headPitchOffset());
            buf.writeBoolean(payload.isBlinking());
            buf.writeFloat(payload.blinkProgress());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
