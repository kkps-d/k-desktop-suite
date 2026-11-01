package com.kkps.kdesktopsuite.notepad

internal data class LiteralMatch(val start: Int, val end: Int)

internal object LiteralSearch {
    fun find(text: String, query: String, matchCase: Boolean = false, checkpoint: () -> Unit = {}): List<LiteralMatch> {
        if (query.isEmpty()) return emptyList()
        val matches = ArrayList<LiteralMatch>()
        var offset = 0
        while (offset <= text.length - query.length) {
            if (offset % 1024 == 0) checkpoint()
            if (text.regionMatches(offset, query, 0, query.length, ignoreCase = !matchCase)) {
                matches.add(LiteralMatch(offset, offset + query.length))
                offset += query.length
                checkpoint()
            } else offset++
        }
        return matches
    }

    fun atOrAfter(matches: List<LiteralMatch>, offset: Int): LiteralMatch? =
        matches.firstOrNull { it.start >= offset } ?: matches.firstOrNull()

    fun navigate(matches: List<LiteralMatch>, start: Int, end: Int, backwards: Boolean): LiteralMatch? =
        if (backwards) matches.lastOrNull { it.end <= start && it.start < start } ?: matches.lastOrNull()
        else matches.firstOrNull { it.start >= end && it.end > end } ?: matches.firstOrNull()
}
