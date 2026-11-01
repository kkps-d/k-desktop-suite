package com.kkps.kdesktopsuite.notepad

import org.junit.Assert.*
import org.junit.Test

class LiteralSearchTest {
    @Test
    fun emptyAndMissingQueriesHaveNoMatches() {
        assertTrue(LiteralSearch.find("text", "").isEmpty())
        assertTrue(LiteralSearch.find("", "text").isEmpty())
        assertTrue(LiteralSearch.find("text", "longer query").isEmpty())
        assertTrue(LiteralSearch.find("text", "missing").isEmpty())
    }

    @Test
    fun matchingIsCaseInsensitiveUnlessRequested() {
        assertEquals(listOf(LiteralMatch(0, 3), LiteralMatch(4, 7)), LiteralSearch.find("Cat cAt", "cat"))
        assertEquals(listOf(LiteralMatch(4, 7)), LiteralSearch.find("Cat cAt", "cAt", true))
        assertEquals(listOf(LiteralMatch(0, 1)), LiteralSearch.find("Ä", "ä"))
        assertTrue(LiteralSearch.find("Ä", "ä", true).isEmpty())
    }

    @Test
    fun adjacentMatchesNeverOverlap() {
        assertEquals(listOf(LiteralMatch(0, 2), LiteralMatch(2, 4)), LiteralSearch.find("aaaaa", "aa"))
        assertEquals(listOf(LiteralMatch(0, 3)), LiteralSearch.find("banana", "ban"))
        assertEquals(listOf(LiteralMatch(1, 4)), LiteralSearch.find("banana", "ana"))
    }

    @Test
    fun punctuationIsLiteralAndTextIsNotNormalized() {
        assertEquals(listOf(LiteralMatch(2, 8)), LiteralSearch.find("x [a].*? y", "[a].*?"))
        assertTrue(LiteralSearch.find("e\u0301", "é").isEmpty())
        assertEquals(listOf(LiteralMatch(1, 4)), LiteralSearch.find("a\r\nbc", "\r\nb"))
    }

    @Test
    fun unicodeRangesUseUtf16IncludingBurmeseAndEmoji() {
        val text = "😀မြန်မာ😀မြန်မာ"
        assertEquals(listOf(LiteralMatch(0, 2), LiteralMatch(8, 10)), LiteralSearch.find(text, "😀"))
        assertEquals(listOf(LiteralMatch(2, 8), LiteralMatch(10, 16)), LiteralSearch.find(text, "မြန်မာ"))
    }

    @Test
    fun navigationWrapsAndSkipsTheCurrentSelection() {
        val matches = LiteralSearch.find("one one one", "one")
        assertEquals(matches[1], LiteralSearch.navigate(matches, 0, 3, false))
        assertEquals(matches[0], LiteralSearch.navigate(matches, 8, 11, false))
        assertEquals(matches[2], LiteralSearch.navigate(matches, 0, 3, true))
        assertEquals(matches[1], LiteralSearch.navigate(matches, 8, 11, true))
        assertEquals(matches[1], LiteralSearch.atOrAfter(matches, 1))
        assertEquals(matches[0], LiteralSearch.atOrAfter(matches, 12))
        assertNull(LiteralSearch.atOrAfter(emptyList(), 0))
        assertNull(LiteralSearch.navigate(emptyList(), 0, 0, false))
    }

    @Test
    fun originalRangesAllowReplacementContainingTheQuery() {
        val text = StringBuilder("a a")
        val matches = LiteralSearch.find(text.toString(), "a")
        matches.asReversed().forEach { text.replace(it.start, it.end, "aa") }
        assertEquals("aa aa", text.toString())
        assertEquals(2, matches.size)
    }
}
