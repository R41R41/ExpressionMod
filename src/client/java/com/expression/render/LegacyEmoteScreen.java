package com.expression.render;

import com.expression.config.EmoteSlotNames;
import com.expression.config.ModConfig;
import com.expression.network.ClientNetworkHandler;
import com.expression.skin.LegacySkinTextures;
import com.expression.state.EmoteType;
import com.expression.state.ExpressionStateManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Five skin-authored slots, labelled with the names this player gave them; the existing authenticated
 * emote packet carries the selection, so other players see the painted face whatever it is called here.
 */
public final class LegacyEmoteScreen extends Screen {
    private static final EmoteType[] SLOTS={EmoteType.SMILE,EmoteType.ANGRY,EmoteType.SAD,EmoteType.SURPRISED,EmoteType.THINK};
    private static final int WIDTH=180, ROW=24;
    private final boolean editing;
    private final TextFieldWidget[] fields=new TextFieldWidget[EmoteSlotNames.SLOTS];
    /** The skin whose painted slots are offered; null means the local player's own skin. */
    private final Identifier skin;

    public LegacyEmoteScreen() { this(false); }
    public LegacyEmoteScreen(boolean editing) { this(editing,null); }
    public LegacyEmoteScreen(boolean editing, Identifier skin) {
        super(Text.literal(editing ? "表情の名前を編集" : "スキンの表情"));
        this.editing=editing;
        this.skin=skin;
    }

    private int top() { return height/2-80; }

    @Override protected void init() {
        if(client.player==null) return;
        var config=ModConfig.getInstance();
        var original=skin!=null ? skin : client.player.getSkin().body().texturePath();
        int left=width/2-WIDTH/2;
        for(int slot=0;slot<EmoteSlotNames.SLOTS;slot++) {
            int index=slot, y=top()+slot*ROW;
            boolean painted=LegacySkinTextures.hasEmote(original,slot);
            if(editing) {
                var field=new TextFieldWidget(textRenderer,left,y,WIDTH,20,Text.literal("表情 "+(slot+1)+" の名前"));
                field.setMaxLength(EmoteSlotNames.MAX_LENGTH);
                field.setText(config.emoteSlotNames[slot]);
                field.setPlaceholder(Text.literal("表情 "+(slot+1)).formatted(Formatting.DARK_GRAY));
                fields[slot]=field;
                addDrawableChild(field);
            } else {
                var label=Text.literal(config.slotName(slot));
                if(!painted) label.append(Text.literal("（スキンに無し）").formatted(Formatting.GRAY));
                var button=ButtonWidget.builder(label,b->choose(SLOTS[index])).dimensions(left,y,WIDTH,20).build();
                button.active=painted;
                addDrawableChild(button);
            }
        }
        int y=top()+EmoteSlotNames.SLOTS*ROW+6, half=WIDTH/2-2;
        if(editing) {
            addDrawableChild(ButtonWidget.builder(Text.literal("保存"),b->saveNames()).dimensions(left,y,half,20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("戻る"),b->client.setScreen(new LegacyEmoteScreen(false,skin))).dimensions(left+WIDTH-half,y,half,20).build());
            setInitialFocus(fields[0]);
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("通常顔に戻す"),b->choose(EmoteType.NEUTRAL)).dimensions(left,y,half,20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("名前を編集"),b->client.setScreen(new LegacyEmoteScreen(true,skin))).dimensions(left+WIDTH-half,y,half,20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("閉じる"),b->close()).dimensions(left,y+ROW,WIDTH,20).build());
        }
    }

    private void choose(EmoteType emote) {
        long duration=ModConfig.getInstance().defaultEmoteDurationMs;
        ExpressionStateManager.getInstance().getOrCreate(client.player.getUuid(),client.player.getName().getString()).setEmote(emote,duration);
        ClientNetworkHandler.sendEmoteChange(emote,duration);
        client.player.sendMessage(Text.literal("[ExpressionMod] "+com.expression.EmoteLabels.feedback(client,emote)),true);
        close();
    }

    /** Saves the edited names and returns to the picker (also the 保存 button). */
    public void saveNames() {
        var config=ModConfig.getInstance();
        for(int slot=0;slot<fields.length;slot++) if(fields[slot]!=null) config.setSlotName(slot,fields[slot].getText());
        config.save();
        client.setScreen(new LegacyEmoteScreen(false,skin));
    }

    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        super.render(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,top()-28,0xFFFFFFFF);
        var hint=editing ? "スキンに描いた顔に合わせて呼び名を付けます（空欄は「表情 N」）"
                : "テンキー1〜5でも選べます";
        context.drawCenteredTextWithShadow(textRenderer,Text.literal(hint),width/2,top()-14,0xFFA0A0A0);
        int left=width/2-WIDTH/2;
        for(int slot=0;slot<EmoteSlotNames.SLOTS;slot++)
            context.drawTextWithShadow(textRenderer,Text.literal(String.valueOf(slot+1)),left-12,top()+slot*ROW+6,0xFFA0A0A0);
    }

    @Override public boolean shouldPause() { return false; }
}
