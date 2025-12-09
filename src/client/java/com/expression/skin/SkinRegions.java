package com.expression.skin;

/**
 * スキンテクスチャの領域定義
 * 
 * ExpressionMod用のシンプルなフォーマット
 * 
 * ========== スキン領域マップ ==========
 * 
 * (0,0)-(7,7) の未使用領域を使用：
 * 
 * ┌────────────────────────────────┐
 * │ (0,0)-(7,0): まつ毛 (1px高) │ 行0
 * │ (0,1)-(7,2): 目 (2px高) │ 行1-2
 * │ (0,3)-(7,3): 肌色 (1px高) │ 行3
 * │ (0,4)-(7,7): 予備 │ 行4-7
 * └────────────────────────────────┘
 * 
 * 顔の目の位置は (8,8)-(15,15) 内の：
 * - まつ毛: 顔の上から指定位置（設定で変更可能）
 * - 目: まつ毛の下2px
 */
public class SkinRegions {

    // ========== まばたきテクスチャ領域 (0,0)-(7,7) ==========

    /**
     * まつ毛テクスチャ領域 (8x1)
     * 閉じた目の状態で顔に重ねる
     */
    public static final int EYELASH_X = 0;
    public static final int EYELASH_Y = 0;
    public static final int EYELASH_WIDTH = 8;
    public static final int EYELASH_HEIGHT = 1;

    /**
     * 目テクスチャ領域 (8x2)
     * 開いた状態の目（白目+瞳）
     */
    public static final int EYE_X = 0;
    public static final int EYE_Y = 1;
    public static final int EYE_WIDTH = 8;
    public static final int EYE_HEIGHT = 2;

    /**
     * 肌色領域 (8x1)
     * まつ毛が下がった時に見える肌色
     */
    public static final int SKIN_X = 0;
    public static final int SKIN_Y = 3;
    public static final int SKIN_WIDTH = 8;
    public static final int SKIN_HEIGHT = 1;

    // ========== 顔の目の位置 ==========

    /**
     * 顔テクスチャ内での目のY開始位置（顔の上端から）
     * デフォルト: 4（顔の真ん中あたり）
     * 
     * 顔テクスチャは (8,8)-(15,15) なので、
     * 目の絶対Y座標 = 8 + EYE_Y_IN_FACE
     */
    public static final int DEFAULT_EYE_Y_IN_FACE = 4;

    /**
     * 顔テクスチャの領域
     */
    public static final int FACE_X = 8;
    public static final int FACE_Y = 8;
    public static final int FACE_WIDTH = 8;
    public static final int FACE_HEIGHT = 8;

    // ========== 帽子/オーバーレイの目の位置 ==========

    /**
     * 帽子レイヤーテクスチャの領域
     */
    public static final int HAT_X = 40;
    public static final int HAT_Y = 8;
    public static final int HAT_WIDTH = 8;
    public static final int HAT_HEIGHT = 8;

    // ========== 機能検出マーカー ==========

    /**
     * ExpressionMod機能有効化マーカー
     * (7,7) が特定の色ならExpressionMod機能が有効
     * 
     * 色: マゼンタ (#FF00FF) = -65281 (ARGB)
     */
    public static final int MARKER_X = 7;
    public static final int MARKER_Y = 7;
    public static final int MARKER_COLOR = 0xFFFF00FF; // マゼンタ (ARGB)

    /**
     * 目のY位置設定ピクセル
     * (6,7) の色で目のY位置を指定（1-8）
     * 
     * 色コード:
     * - ピンク(1): Y=1
     * - シアン(2): Y=2
     * - 赤(3): Y=3
     * - 緑(4): Y=4 (デフォルト)
     * - 茶(5): Y=5
     * - 青(6): Y=6
     * - オレンジ(7): Y=7
     * - 黄(8): Y=8
     */
    public static final int EYE_POSITION_X = 6;
    public static final int EYE_POSITION_Y = 7;

    // ========== 色コード ==========

    public static final int COLOR_PINK = 0xFFFF00FF; // 1
    public static final int COLOR_CYAN = 0xFF00FFFF; // 2
    public static final int COLOR_RED = 0xFFFF0000; // 3
    public static final int COLOR_GREEN = 0xFF00FF00; // 4
    public static final int COLOR_BROWN = 0xFF7F3F00; // 5
    public static final int COLOR_BLUE = 0xFF0000FF; // 6
    public static final int COLOR_ORANGE = 0xFFFF7F00; // 7
    public static final int COLOR_YELLOW = 0xFFFFFF00; // 8

    /**
     * 色から数値に変換（1-8、それ以外は0）
     */
    public static int colorToNumber(int color) {
        // アルファを無視して比較
        int rgb = color & 0x00FFFFFF;
        return switch (rgb) {
            case 0xFF00FF -> 1; // ピンク
            case 0x00FFFF -> 2; // シアン
            case 0xFF0000 -> 3; // 赤
            case 0x00FF00 -> 4; // 緑
            case 0x7F3F00 -> 5; // 茶
            case 0x0000FF -> 6; // 青
            case 0xFF7F00 -> 7; // オレンジ
            case 0xFFFF00 -> 8; // 黄
            default -> 0;
        };
    }

    /**
     * マーカー色かどうかをチェック
     */
    public static boolean isMarkerColor(int color) {
        // アルファを無視して比較
        return (color & 0x00FFFFFF) == (MARKER_COLOR & 0x00FFFFFF);
    }
}
