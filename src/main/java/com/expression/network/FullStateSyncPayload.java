package com.expression.network;

import com.expression.ExpressionMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * 全状態同期用ペイロード（新規参加者への初期同期用）
 */
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
        float blinkProgress) implements CustomPayload {

    public static final CustomPayload.Id<FullStateSyncPayload> ID = new CustomPayload.Id<>(
            Identifier.of(ExpressionMod.MOD_ID, "full_state_sync"));

    public static final PacketCodec<RegistryByteBuf, FullStateSyncPayload> CODEC = new PacketCodec<>() {
        @Override
        public FullStateSyncPayload decode(RegistryByteBuf buf) {
            UUID uuid = UUID.fromString(buf.readString());
            String name = buf.readString();
            String emote = buf.readString();
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
        public void encode(RegistryByteBuf buf, FullStateSyncPayload payload) {
            buf.writeString(payload.playerUuid().toString());
            buf.writeString(payload.playerName());
            buf.writeString(payload.emoteId());
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
