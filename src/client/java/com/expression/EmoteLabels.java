package com.expression;

import com.expression.config.ModConfig;
import com.expression.skin.LegacySkinFrames;
import com.expression.skin.LegacySkinTextures;
import com.expression.state.EmoteType;
import net.minecraft.client.MinecraftClient;

/** What to call an expression on this player's screen. */
public final class EmoteLabels {
    /** True when the local player's skin carries painted expression slots (the format whose slots the player names). */
    public static boolean usesSkinSlots(MinecraftClient client) {
        if (client.player == null) return false;
        var texture = client.player.getSkin().body().texturePath();
        for (int slot = 0; slot < 5; slot++) if (LegacySkinTextures.hasEmote(texture, slot)) return true;
        return false;
    }

    /**
     * The action-bar text after an expression key. Painted-slot skins show the player's own name for the slot,
     * or say plainly that the skin has no face there; other skins keep the mod's expression names.
     */
    public static String feedback(MinecraftClient client, EmoteType emote) {
        if (emote == EmoteType.NEUTRAL || !usesSkinSlots(client)) return emote.getDisplayName();
        int slot = LegacySkinFrames.slot(emote);
        if (slot < 0) return "このスキンには描かれていない表情です";
        var texture = client.player.getSkin().body().texturePath();
        String name = ModConfig.getInstance().slotName(slot);
        return LegacySkinTextures.hasEmote(texture, slot) ? name : name + "（スキンの " + (slot + 1) + " 番は空です）";
    }

    private EmoteLabels() {}
}
