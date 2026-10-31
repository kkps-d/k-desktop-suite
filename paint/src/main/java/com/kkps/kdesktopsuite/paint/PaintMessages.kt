package com.kkps.kdesktopsuite.paint

import android.content.res.Resources
import androidx.annotation.StringRes

internal data class PaintMessage(@param:StringRes val resourceId: Int, val arguments: List<Any> = emptyList()) {
    fun resolve(resources: Resources): String = resources.getString(resourceId, *arguments.toTypedArray())
}

@StringRes
internal fun PaintProblem.messageResource(): Int = when (this) {
    PaintProblem.ImageDimensions -> R.string.error_image_dimensions
    PaintProblem.WorkingMemory -> R.string.error_working_memory
    PaintProblem.EmptySelection -> R.string.error_empty_selection
    PaintProblem.TransformSize -> R.string.error_transform_size
    PaintProblem.TransformBounds -> R.string.error_transform_bounds
    PaintProblem.SkewRange -> R.string.error_skew_range
    PaintProblem.EncodedFileSize -> R.string.error_encoded_file_size
    PaintProblem.ImageFormat -> R.string.error_image_format
    PaintProblem.IncompleteWrite -> R.string.error_incomplete_write
    PaintProblem.ComparisonFileSize -> R.string.error_compare_file_size
}
