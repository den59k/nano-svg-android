package com.ingenuity.svg

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/** How a gradient repeats outside its [0,1] range (mirrors nanosvg spread). */
enum class SVGSpread { PAD, REFLECT, REPEAT }

/** One gradient color stop. [color] is Android 0xAARRGGBB; [offset] is 0..1. */
data class SVGGradientStop(val offset: Float, val color: Int)

/**
 * A linear gradient in SVG user space.
 *
 * [xform] is nanosvg's column-major 3×2 matrix `[a, b, c, d, tx, ty]` mapping
 * gradient space to user space: the gradient axis runs from `(tx, ty)` (offset 0)
 * to `(a + tx, b + ty)` (offset 1).
 */
data class SVGLinearGradient(
    val xform: FloatArray,
    val stops: List<SVGGradientStop>,
    val spread: SVGSpread
) {
    /**
     * Renderer-side cache of the built shader.  Defined in SVG user space, so it
     * is reusable across target rects (the canvas matrix is applied on top).
     * Excluded from the generated equals/hashCode/copy.
     */
    @JvmField internal var shader: Shader? = null
}

/**
 * A radial gradient in SVG user space.
 *
 * [xform] is nanosvg's column-major 3×2 matrix; the center is `(xform[4], xform[5])`
 * and the radius is `xform[0]`.  [fx]/[fy] are the focal point in normalized gradient
 * space (0..1).  Android's [android.graphics.RadialGradient] is concentric only, so
 * the focal point is not applied during rendering.
 */
data class SVGRadialGradient(
    val xform: FloatArray,
    val fx: Float,
    val fy: Float,
    val stops: List<SVGGradientStop>,
    val spread: SVGSpread
) {
    /** Renderer-side shader cache; see [SVGLinearGradient.shader]. */
    @JvmField internal var shader: Shader? = null
}

/** The paint applied to a shape's fill or stroke. */
sealed class SVGPaint {
    object None : SVGPaint()
    data class Color(val color: Int) : SVGPaint()
    data class Linear(val gradient: SVGLinearGradient) : SVGPaint()
    data class Radial(val gradient: SVGRadialGradient) : SVGPaint()
}

/**
 * One shape from a parsed SVG.  [path] is built once at parse time and must not
 * be modified.  Colors are in Android 0xAARRGGBB format.
 *
 * [fillPaint]/[strokePaint] carry the full paint (solid color or gradient).  The
 * scalar [fillColor]/[strokeColor] and [hasFill]/[hasStroke] fields remain for
 * convenience and backward compatibility; for a gradient paint the scalar color
 * is `0` — inspect [fillPaint]/[strokePaint] to render gradients.
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
    val evenOdd: Boolean,
    val fillPaint: SVGPaint = SVGPaint.None,
    val strokePaint: SVGPaint = SVGPaint.None
)

/** Result of [SVGParser.parse]. Shapes are in document order. */
data class ParsedSVG(
    val width: Float,
    val height: Float,
    val shapes: List<SVGShape>
)
