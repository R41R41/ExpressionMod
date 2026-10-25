package com.expression.skin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacySkinFramesTest {
    @Test void acceptsVmMarkersAndPreservesTheCloudFormats() {
        for(int x=0;x<3;x++) {
            int marker=x;
            var layout=LegacySkinFrames.detect(64,64,(px,y)->px==marker && y==0?0xFF00FFFF:0).orElseThrow();
            assertEquals(56,layout.sourceX());
            assertEquals(x==2?16:0,layout.sourceY());
            assertEquals(x==1?3:4,layout.eyeY());
        }
        assertEquals(24,LegacySkinFrames.detect(64,64,(x,y)->x==63&&y==1?0xFF00FFFF:0).orElseThrow().sourceX());
        assertTrue(LegacySkinFrames.detect(64,32,(x,y)->{fail();return 0;}).isEmpty());
    }
    @Test void authoredBlinkGazeAndLashesReplaceOnlyTheFourEyeColumns() {
        int[] skin=new int[4096];java.util.Arrays.fill(skin,0xFFABCDEF);
        var layout=new SkinLayout(56,16,4,1,true);
        for(int x:new int[]{1,2,5,6}) {
            skin[(16+7)*64+56+x]=0xFF222222;
            skin[(16+8)*64+56+x]=0xFF2244AA;
            skin[(16+9)*64+56+x]=0xFF4466CC;
            skin[(16+3)*64+56+x]=0xFFFFCCBB;
        }
        var left=LegacySkinFrames.eyes(skin,layout,1);
        assertEquals(0xFF222222,left[12*64+9]);
        assertEquals(0xFF2244AA,left[13*64+9]);
        assertEquals(0xFF4466CC,left[14*64+9]);
        var closed=LegacySkinFrames.eyes(skin,layout,6);
        assertEquals(0xFFFFCCBB,closed[12*64+9]);
        assertEquals(0xFFFFCCBB,closed[13*64+9]);
        for(int y=0;y<64;y++) for(int x=0;x<64;x++)
            if(y<11||y>14||!(x==9||x==10||x==13||x==14)) assertEquals(skin[y*64+x],left[y*64+x]);
        assertEquals(0xFFABCDEF,skin[12*64+9],"must not overwrite the original skin");
    }
    @Test void fiveAuthoredFacesKeepTransparentPixelsHairAndClothing() {
        int[] skin=new int[4096];java.util.Arrays.fill(skin,0xFFABCDEF);
        skin[0*64+24]=0xFF102030;skin[0*64+25]=0;
        var face=LegacySkinFrames.emote(skin,new SkinLayout(56,0,3,2,false),0);
        assertEquals(0xFF102030,face[11*64+8]);
        assertEquals(0xFFABCDEF,face[11*64+9]);
        for(int y=0;y<64;y++) for(int x=0;x<64;x++)
            if(x<8||x>15||y<11||y>15) assertEquals(skin[y*64+x],face[y*64+x]);
    }
    @Test void closedEyesOverrideGazeAndPitchAndBlinkUsesTheThreeFrames() {
        assertEquals(6,LegacySkinFrames.eyeFrame(true,0,30,-30));
        assertEquals(6,LegacySkinFrames.eyeFrame(false,1,30,-30));
        assertEquals(5,LegacySkinFrames.eyeFrame(false,.3f,30,-30));
        assertEquals(8,LegacySkinFrames.eyeFrame(false,0,-30,-30));
        assertEquals(0,LegacySkinFrames.eyeFrame(false,0,0,0));
    }
}
