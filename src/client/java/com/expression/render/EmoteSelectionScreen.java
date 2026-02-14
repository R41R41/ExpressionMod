package com.expression.render;

import com.expression.ExpressionMod;
import com.expression.skin.EmoteManager;
import com.expression.skin.EmoteManager.EmoteTextureData;
import com.expression.network.ClientNetworkHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

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

    // UI設定
    private static final int EMOTE_SIZE = 32; // 8x5を4倍に拡大
    private static final int EMOTE_HEIGHT = 20; // 5ピクセル * 4
    private static final int PADDING = 4;
    private static final int HIGHLIGHT_COLOR = 0x80FFFFFF; // 半透明白
    private static final int BACKGROUND_COLOR = 0xC0000000; // 半透明黒

    public static class EmoteEntry {
        public final int index;
        public final Identifier previewTexture;
        public final int y; // メニュー内でのY位置

        public EmoteEntry(int index, Identifier previewTexture, int y) {
            this.index = index;
            this.previewTexture = previewTexture;
            this.y = y;
        }
    }

    /**
     * エモートメニューを開く
     */
    public static void open(int mouseX, int mouseY, NativeImage skinImage, EmoteTextureData emoteData) {
        if (emoteData == null || !emoteData.hasAnyEmote()) {
            return;
        }

        isActive = true;
        currentSkinImage = skinImage;
        availableEmotes.clear();
        clearPreviewTextures();

        // 有効なエモートを収集
        int currentY = PADDING;
        for (int i = 0; i < EmoteManager.MAX_EMOTES; i++) {
            if (emoteData.emoteEnabled[i]) {
                // プレビューテクスチャを作成
                NativeImage preview = EmoteManager.getEmotePreviewImage(skinImage, i);
                if (preview != null) {
                    Identifier previewId = Identifier.of(ExpressionMod.MOD_ID,
                            "emote_preview_" + System.currentTimeMillis() + "_" + i);
                    try {
                        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "expressionmod_preview", preview);
                        MinecraftClient.getInstance().getTextureManager().registerTexture(previewId, texture);
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

        // メニュー位置を計算（カーソルの右か左）
        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        int menuWidth = EMOTE_SIZE + PADDING * 2;
        int menuHeight = currentY;

        // 右側に十分なスペースがあれば右に、なければ左に
        if (mouseX + menuWidth + 10 < screenWidth) {
            menuX = mouseX + 10;
        } else {
            menuX = mouseX - menuWidth - 10;
        }
        menuY = mouseY - menuHeight / 2;

        // 画面外にならないよう調整
        int screenHeight = client.getWindow().getScaledHeight();
        if (menuY < 0)
            menuY = 0;
        if (menuY + menuHeight > screenHeight)
            menuY = screenHeight - menuHeight;

        hoveredEmote = -1;
        ExpressionMod.LOGGER.info("[EmoteSelectionScreen] Opened with " + availableEmotes.size() + " emotes");
    }

    /**
     * エモートメニューを閉じる
     */
    public static void close(boolean selectHovered) {
        if (!isActive)
            return;

        if (selectHovered && hoveredEmote >= 0) {
            // 選択されたエモートを適用
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                EmoteManager.startEmote(client.player.getUuid(), hoveredEmote);
                // サーバーに同期
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

    /**
     * プレビューテクスチャをクリア
     */
    private static void clearPreviewTextures() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Identifier id : previewTextures) {
            client.getTextureManager().destroyTexture(id);
        }
        previewTextures.clear();
    }

    /**
     * マウス移動時の更新
     */
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

    /**
     * メニューを描画
     */
    public static void render(DrawContext context) {
        if (!isActive || availableEmotes.isEmpty())
            return;

        int menuWidth = EMOTE_SIZE + PADDING * 2;
        int menuHeight = availableEmotes.size() * (EMOTE_HEIGHT + PADDING) + PADDING;

        // 背景
        context.fill(menuX, menuY, menuX + menuWidth, menuY + menuHeight, BACKGROUND_COLOR);

        // 各エモートを描画
        for (EmoteEntry entry : availableEmotes) {
            int entryY = menuY + entry.y;

            // ハイライト
            if (entry.index == hoveredEmote) {
                context.fill(menuX, entryY, menuX + menuWidth, entryY + EMOTE_HEIGHT, HIGHLIGHT_COLOR);
            }

            // プレビュー画像（8x5を拡大して描画）
            if (entry.previewTexture != null) {
                context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        entry.previewTexture,
                        menuX + PADDING, entryY,
                        0, 0,
                        EMOTE_SIZE, EMOTE_HEIGHT,
                        EMOTE_SIZE, EMOTE_HEIGHT);
            }
        }

        // 枠線
        context.drawStrokedRectangle(menuX, menuY, menuWidth, menuHeight, 0xFFFFFFFF);
    }

    /**
     * メニューがアクティブかどうか
     */
    public static boolean isActive() {
        return isActive;
    }

    /**
     * 現在ホバー中のエモートインデックスを取得
     */
    public static int getHoveredEmote() {
        return hoveredEmote;
    }
}
