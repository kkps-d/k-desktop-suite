package com.kkps.kdesktopsuite.notepad

import kotlin.math.ceil

/** Index in the destination after removing the dragged tab, measured from the logical start. */
internal fun tabInsertionIndex(position: Float, width: Float, stride: Float, count: Int, sourceIndex: Int): Int {
    val gap = ceil((position - width / 2f) / stride.coerceAtLeast(1f)).toInt().coerceIn(0, count)
    return if (sourceIndex >= 0 && gap > sourceIndex) gap - 1 else gap
}

internal fun previewTabOrder(documents: List<DocumentSession>, dragged: DocumentSession?, index: Int?): List<DocumentSession> {
    if (dragged == null || index == null) return documents
    return documents.filter { it.id != dragged.id }.toMutableList().apply {
        add(index.coerceIn(0, size), dragged)
    }
}
