package com.expression.render;

import com.expression.ExpressionMod;
import com.expression.skin.EmoteManager;
import com.expression.skin.EmoteManager.EmoteTextureData;
import com.expression.network.ClientNetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * エモート選択UI
 * Ctrl+左クリックで表示し、ドラッグで選択
 */
public class EmoteSelectionScreen {

    private static boolean isActive = false;
    private static int menuX = 0;
    private static int menuY = 0;
    private static int hoveredEmote = -1;
    private static List<EmoteEntry> availableEmotes = new ArrayList<>();
    private static NativeImage currentSkinImage = null;
    private static final List<Identifier> previewTextures = new ArrayList<>();

    private static final int EMOTE_SIZE = 32;
    private static final int EMOTE_HEIGHT = 20;
    private static final int PADDING = 4;
    private static final int HIGHLIGHT_COLOR = 0x80FFFFFF;
    private static final int BACKGROUND_COLOR = 0xC0000000;

    public static class EmoteEntry {
        public final int index;
        public final Identifier previewTexture;
        public final int y;

        public EmoteEntry(int index, Identifier previewTexture, int y) {
            this.index = index;
            this.previewTexture = previewTexture;
            this.y = y;
        }
    }

    public static void open(int mouseX, int mouseY, NativeImage skinImage, EmoteTextureData emoteData) {
        if (emoteData == null || !emoteData.hasAnyEmote()) {
            return;
        }

        isActive = true;
        currentSkinImage = skinImage;
        availableEmotes.clear();
        clearPreviewTextures();

        int currentY = PADDING;
        for (int i = 0; i < EmoteManager.MAX_EMOTES; i++) {
            if (emoteData.emoteEnabled[i]) {
                NativeImage preview = EmoteManager.getEmotePreviewImage(skinImage, i);
                if (preview != null) {
                    Identifier previewId = Identifier.fromNamespaceAndPath(ExpressionMod.MOD_ID,
                            "emote_preview_" + System.currentTimeMillis() + "_" + i);
                    try {
                        DynamicTexture texture = new DynamicTexture(() -> "emote_icon", preview);
                        Minecraft.getInstance().getTextureManager().register(previewId, texture);
                        previewTextures.add(previewId);
                        availableEmotes.add(new EmoteEntry(i, previewId, currentY));
                        currentY += EMOTE_HEIGHT + PADDING;
                    } catch (Exception e) {
                        preview.close();
                    }
                }
            }
        }

        if (availableEmotes.isEmpty()) {
            isActive = false;
            return;
        }

        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int menuWidth = EMOTE_SIZE + PADDING * 2;
        int menuHeight = currentY;

        if (mouseX + menuWidth + 10 < screenWidth) {
            menuX = mouseX + 10;
        } else {
            menuX = mouseX - menuWidth - 10;
        }
        menuY = mouseY - menuHeight / 2;

        int screenHeight = client.getWindow().getGuiScaledHeight();
        if (menuY < 0)
            menuY = 0;
        if (menuY + menuHeight > screenHeight)
            menuY = screenHeight - menuHeight;

        hoveredEmote = -1;
        ExpressionMod.LOGGER.info("[EmoteSelectionScreen] Opened with " + availableEmotes.size() + " emotes");
    }

    public static void close(boolean selectHovered) {
        if (!isActive)
            return;

        if (selectHovered && hoveredEmote >= 0) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                EmoteManager.startEmote(client.player.getUUID(), hoveredEmote);
                ClientNetworkHandler.sendEmoteSync(hoveredEmote);
                ExpressionMod.LOGGER.info("[EmoteSelectionScreen] Selected emote " + hoveredEmote);
            }
        }

        isActive = false;
        hoveredEmote = -1;
        availableEmotes.clear();
        clearPreviewTextures();
        currentSkinImage = null;
    }

    private static void clearPreviewTextures() {
        Minecraft client = Minecraft.getInstance();
        for (Identifier id : previewTextures) {
            // TODO: Verify texture cleanup method for 26.1 Mojang (was: destroyTexture)
            client.getTextureManager().release(id);
        }
        previewTextures.clear();
    }

    public static void updateMousePosition(int mouseX, int mouseY) {
        if (!isActive)
            return;

        hoveredEmote = -1;
        int menuWidth = EMOTE_SIZE + PADDING * 2;

        for (EmoteEntry entry : availableEmotes) {
            int entryTop = menuY + entry.y;
            int entryBottom = entryTop + EMOTE_HEIGHT;
            int entryLeft = menuX;
            int entryRight = menuX + menuWidth;

            if (mouseX >= entryLeft && mouseX < entryRight &&
                    mouseY >= entryTop && mouseY < entryBottom) {
                hoveredEmote = entry.index;
                break;
            }
        }
    }

    public static void render(GuiGraphicsExtractor guiGraphics) {
        if (!isActive || availableEmotes.isEmpty())
            return;

        int menuWidth = EMOTE_SIZE + PADDING * 2;
        int menuHeight = availableEmotes.size() * (EMOTE_HEIGHT + PADDING) + PADDING;

        guiGraphics.fill(menuX, menuY, menuX + menuWidth, menuY + menuHeight, BACKGROUND_COLOR);

        for (EmoteEntry entry : availableEmotes) {
            int entryY = menuY + entry.y;

            if (entry.index == hoveredEmote) {
                guiGraphics.fill(menuX, entryY, menuX + menuWidth, entryY + EMOTE_HEIGHT, HIGHLIGHT_COLOR);
            }

            if (entry.previewTexture != null) {
                guiGraphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        entry.previewTexture,
                        menuX + PADDING, entryY,
                        0, 0,
                        EMOTE_SIZE, EMOTE_HEIGHT,
                        EMOTE_SIZE, EMOTE_HEIGHT);
            }
        }

        // Border (replacing drawStrokedRectangle which may not exist in 26.1)
        int borderColor = 0xFFFFFFFF;
        guiGraphics.fill(menuX, menuY, menuX + menuWidth, menuY + 1, borderColor);
        guiGraphics.fill(menuX, menuY + menuHeight - 1, menuX + menuWidth, menuY + menuHeight, borderColor);
        guiGraphics.fill(menuX, menuY, menuX + 1, menuY + menuHeight, borderColor);
        guiGraphics.fill(menuX + menuWidth - 1, menuY, menuX + menuWidth, menuY + menuHeight, borderColor);
    }

    public static boolean isActive() {
        return isActive;
    }

    public static int getHoveredEmote() {
        return hoveredEmote;
    }
}
