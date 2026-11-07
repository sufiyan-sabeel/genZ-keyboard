package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Checks the emoji-suggestion rules: exact match, guarded prefix match, nothing on junk input. */
class EmojiSuggestTest {

    @Test
    fun exactMatchIsCaseInsensitive() {
        assertEquals("😂", EmojiSuggest.forWord("lol"))
        assertEquals("😂", EmojiSuggest.forWord("LOL"))
        assertEquals("🎉", EmojiSuggest.forWord("Party"))
    }

    @Test
    fun longKeysMayMatchTheStartOfAWord() {
        assertEquals("😂", EmojiSuggest.forWord("laughing"))
        assertEquals("🏋️", EmojiSuggest.forWord("workouts"))
    }

    @Test
    fun shortKeysNeverPrefixMatch() {
        // "ok" is a key, but it must not swallow "okra"
        assertNull(EmojiSuggest.forWord("okra"))
        assertEquals("👌", EmojiSuggest.forWord("ok"))
    }

    @Test
    fun ordinaryWordsGetNothing() {
        assertNull(EmojiSuggest.forWord("the"))
        assertNull(EmojiSuggest.forWord("paragraph"))
        assertNull(EmojiSuggest.forWord(""))
        assertNull(EmojiSuggest.forWord("a"))
    }

    @Test
    fun surroundingPunctuationIsIgnored() {
        assertEquals("🙏", EmojiSuggest.forWord("thanks!"))
        assertEquals("🔥", EmojiSuggest.forWord("\"fire\""))
    }
}
