package com.expression.network;

import com.expression.ExpressionMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

/** Additive v1 conversation channel: UUID/name are always replaced by the C2S sender. */
public record DialogueSyncPayload(UUID playerUuid, String playerName, String activity, long durationMs,
                                  boolean hasTarget, float yaw, float pitch) implements CustomPayload {
    public static final Id<DialogueSyncPayload> ID = new Id<>(Identifier.of(ExpressionMod.MOD_ID, "dialogue_sync"));
    public static final PacketCodec<RegistryByteBuf, DialogueSyncPayload> CODEC = new PacketCodec<>() {
        public DialogueSyncPayload decode(RegistryByteBuf b) {
            return new DialogueSyncPayload(UUID.fromString(b.readString(36)), b.readString(64), b.readString(16),
                    b.readVarLong(), b.readBoolean(), b.readFloat(), b.readFloat());
        }
        public void encode(RegistryByteBuf b, DialogueSyncPayload p) {
            b.writeString(p.playerUuid().toString()); b.writeString(p.playerName()); b.writeString(p.activity());
            b.writeVarLong(p.durationMs()); b.writeBoolean(p.hasTarget()); b.writeFloat(p.yaw()); b.writeFloat(p.pitch());
        }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}
