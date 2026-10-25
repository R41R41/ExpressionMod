package com.expression.network;

import com.expression.ExpressionMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Reply when an external body advertises this channel, even after joining the world. */
public record BridgeReadyPayload(int version) implements CustomPayload {
    public static final Id<BridgeReadyPayload> ID = new Id<>(Identifier.of(ExpressionMod.MOD_ID, "bridge_ready"));
    public static final PacketCodec<RegistryByteBuf, BridgeReadyPayload> CODEC = new PacketCodec<>() {
        public BridgeReadyPayload decode(RegistryByteBuf b) { return new BridgeReadyPayload(b.readUnsignedByte()); }
        public void encode(RegistryByteBuf b, BridgeReadyPayload p) { b.writeByte(p.version()); }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}
