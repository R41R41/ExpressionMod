package com.expression.skin;

import com.expression.state.EmoteType;
import java.util.Optional;
import java.util.function.IntBinaryOperator;

/** Authored skin pixels: the VM format and the earlier right-edge marker format. */
public final class LegacySkinFrames {
    private static final int[][] EMOTES = {{24,0},{32,0},{56,32},{56,37},{56,42}};
    public static Optional<SkinLayout> detect(int width, int height, IntBinaryOperator pixel) {
        var original = SkinLayout.detect(width, height, pixel);
        if (original.isPresent() || width != 64 || height != 64) return original;
        if (SkinLayout.cyan(pixel.applyAsInt(0,0))) return Optional.of(new SkinLayout(56,0,4,2,false));
        if (SkinLayout.cyan(pixel.applyAsInt(1,0))) return Optional.of(new SkinLayout(56,0,3,2,false));
        if (SkinLayout.cyan(pixel.applyAsInt(2,0))) return Optional.of(new SkinLayout(56,16,4,1,true));
        return Optional.empty();
    }
    public static boolean hasEmote(IntBinaryOperator pixel, int slot) {
        return slot >= 0 && slot < 5 && SkinLayout.cyan(pixel.applyAsInt(slot,1));
    }
    public static int slot(EmoteType emote) {
        return switch (emote) { case SMILE -> 0; case ANGRY -> 1; case SAD -> 2;
            case SURPRISED -> 3; case THINK -> 4; default -> -1; };
    }
    /** NORMAL/HALF/CLOSED/WIDE, each with the authored left/centre/right gaze. */
    public static int eyeFrame(boolean closed, float blink, float yaw, float pitch) {
        if (closed || blink > .65f) return 6;
        int direction = yaw > 5 ? 2 : yaw < -5 ? 1 : 0;
        if (blink > .05f || pitch > 25) return 3 + direction;
        if (pitch < -20) return 7 + direction;
        return direction;
    }
    public static int[] eyes(int[] original, SkinLayout layout, int frame) {
        int[] pixels = original.clone();
        int state = frame == 6 ? 2 : frame >= 7 ? 3 : frame >= 3 ? 1 : 0;
        int direction = frame == 6 ? 0 : (frame >= 7 ? frame-7 : frame >= 3 ? frame-3 : frame);
        int gaze = direction == 1 ? -1 : direction == 2 ? 1 : 0;
        for (int x : new int[]{1,2,5,6}) {
            int lash = original[(layout.sourceY()+layout.lashRow(gaze))*64+layout.sourceX()+x];
            if ((lash >>> 24) == 0) continue;
            int skin = original[(layout.sourceY()+3)*64+layout.sourceX()+x];
            int top = original[(layout.sourceY()+layout.eyeRow(gaze))*64+layout.sourceX()+x];
            int bottom = original[(layout.sourceY()+layout.eyeRow(gaze)+1)*64+layout.sourceX()+x];
            int y = 8 + layout.eyeY(), destX = 8+x;
            if (state == 3) {
                pixels[y*64+destX] = top; pixels[(y+1)*64+destX] = top; pixels[(y+2)*64+destX] = bottom;
            }
            int offset = state == 1 ? 1 : state == 2 ? 2 : state == 3 ? -1 : 0;
            pixels[(y+offset)*64+destX] = lash;
            if (state == 1 || state == 2) pixels[y*64+destX] = skin;
            if (state == 2) pixels[(y+1)*64+destX] = skin;
            if (state == 0) pixels[(y+1)*64+destX] = top;
            if (state == 0 || state == 1) pixels[(y+2)*64+destX] = bottom;
        }
        return pixels;
    }
    public static int[] emote(int[] original, SkinLayout layout, int slot) {
        int[] pixels = original.clone();
        int[] source = EMOTES[slot];
        // VM format's 8x5 patch ends at the face's chin. Preserve the upper hair and all clothing.
        int start = 3;
        for (int y=0; y<5; y++) for (int x=0; x<8; x++)
            if ((original[(source[1]+y)*64+source[0]+x] >>> 24) != 0)
                pixels[(8+start+y)*64+8+x] = original[(source[1]+y)*64+source[0]+x];
        return pixels;
    }
    private LegacySkinFrames() {}
}
