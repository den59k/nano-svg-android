package com.ingenuity.svg

import android.graphics.Paint
import android.graphics.Path

/**
 * One shape from a parsed SVG.  [path] is built once at parse time and must not
 * be modified.  Colors are in Android 0xAARRGGBB format.
 */
data class SVGShape(
    val path: Path,
    val hasFill: Boolean,
    val fillColor: Int,
    val hasStroke: Boolean,
    val strokeColor: Int,
    val strokeWidth: Float,
    val cap: Paint.Cap,
    val join: Paint.Join,
    val miter: Float,
    val evenOdd: Boolean
)

/** Result of [SVGParser.parse]. Shapes are in document order. */
data class ParsedSVG(
    val width: Float,
    val height: Float,
    val shapes: List<SVGShape>
)
