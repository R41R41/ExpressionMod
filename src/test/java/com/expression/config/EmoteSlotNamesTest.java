package com.expression.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EmoteSlotNamesTest {
    @Test void customNamesReplaceTheFixedOnes() {
        String[] names = {"ウインク", "ジト目", "泣き", "びっくり", "ドヤ顔"};
        assertEquals("ウインク", EmoteSlotNames.name(names, 0));
        assertEquals("ドヤ顔", EmoteSlotNames.name(names, 4));
    }

    @Test void blankOrMissingFallsBackToTheSlotNumber() {
        assertEquals("表情 2", EmoteSlotNames.name(new String[]{"a", "   ", "c", "d", "e"}, 1));
        assertEquals("表情 5", EmoteSlotNames.name(new String[]{"a"}, 4));
        assertEquals("表情 1", EmoteSlotNames.name(null, 0));
    }

    @Test void anOldConfigWithoutNamesKeepsTheDefaults() {
        assertArrayEquals(EmoteSlotNames.defaults(), EmoteSlotNames.normalize(null));
        String[] partial = EmoteSlotNames.normalize(new String[]{" にっこり "});
        assertEquals(5, partial.length);
        assertEquals("にっこり", partial[0]);
        assertEquals("怒り", partial[1]);
    }

    @Test void namesAreBoundedAndControlCharactersDropped() {
        assertEquals(EmoteSlotNames.MAX_LENGTH, EmoteSlotNames.clean("あ".repeat(40)).length());
        assertEquals("笑う", EmoteSlotNames.clean("笑\n う".replace(" ", "")));
        assertEquals(5, EmoteSlotNames.normalize(new String[]{"1", "2", "3", "4", "5", "6"}).length);
    }
}
