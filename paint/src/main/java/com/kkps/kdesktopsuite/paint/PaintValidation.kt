package com.kkps.kdesktopsuite.paint

// Pure raster/storage operations report identities; the Android UI supplies the wording.
internal enum class PaintProblem {
    ImageDimensions,
    WorkingMemory,
    EmptySelection,
    TransformSize,
    TransformBounds,
    SkewRange,
    EncodedFileSize,
    ImageFormat,
    IncompleteWrite,
    ComparisonFileSize,
}

internal class PaintValidationException(val problem: PaintProblem) : IllegalArgumentException(problem.name)

internal fun validatePaint(condition: Boolean, problem: PaintProblem) {
    if (!condition) throw PaintValidationException(problem)
}
