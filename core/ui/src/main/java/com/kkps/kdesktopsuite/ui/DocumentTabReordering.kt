package com.kkps.kdesktopsuite.ui

import kotlin.math.ceil

/** Index in the destination after removing the dragged tab, measured from the logical start. */
fun tabInsertionIndex(position: Float, width: Float, stride: Float, count: Int, sourceIndex: Int): Int {
    val gap = ceil((position - width / 2f) / stride.coerceAtLeast(1f)).toInt().coerceIn(0, count)
    return if (sourceIndex >= 0 && gap > sourceIndex) gap - 1 else gap
}

fun <T> previewTabOrder(documents: List<T>, dragged: T?, index: Int?, id: (T) -> String): List<T> {
    if (dragged == null || index == null) return documents
    return documents.filter { id(it) != id(dragged) }.toMutableList().apply {
        add(index.coerceIn(0, size), dragged)
    }
}
