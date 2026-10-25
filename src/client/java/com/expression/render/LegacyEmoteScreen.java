package com.expression.render;

import com.expression.network.ClientNetworkHandler;
import com.expression.skin.LegacySkinTextures;
import com.expression.state.EmoteType;
import com.expression.state.ExpressionStateManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Five skin-authored slots; the existing authenticated emote packet carries the selection. */
public final class LegacyEmoteScreen extends Screen {
    private static final EmoteType[] SLOTS={EmoteType.SMILE,EmoteType.ANGRY,EmoteType.SAD,EmoteType.SURPRISED,EmoteType.THINK};
    public LegacyEmoteScreen() { super(Text.literal("スキンの表情")); }
    @Override protected void init() {
        if(client.player==null) return;
        var original=client.player.getSkin().body().texturePath();
        for(int slot=0;slot<5;slot++) {
            int index=slot;
            var button=ButtonWidget.builder(Text.literal("表情 "+(slot+1)),b->{
                var emote=SLOTS[index];
                long duration=com.expression.config.ModConfig.getInstance().defaultEmoteDurationMs;
                ExpressionStateManager.getInstance().getOrCreate(client.player.getUuid(),client.player.getName().getString()).setEmote(emote,duration);
                ClientNetworkHandler.sendEmoteChange(emote,duration);
                close();
            }).dimensions(width/2-60,height/2-70+slot*24,120,20).build();
            button.active=LegacySkinTextures.hasEmote(original,slot);
            addDrawableChild(button);
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("キャンセル"),b->close()).dimensions(width/2-60,height/2+58,120,20).build());
    }
    @Override public boolean shouldPause() { return false; }
}
