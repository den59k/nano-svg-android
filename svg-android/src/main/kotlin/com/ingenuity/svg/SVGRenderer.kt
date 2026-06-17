package com.ingenuity.svg

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * Stateless renderer that aspect-fits a [ParsedSVG] into a target rectangle.
 *
 * [paint] is reused across all draw calls — no allocation in the hot path.
 * Callers must not invoke [drawSvg] from multiple threads concurrently.
 */
object SVGRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Reused bounds for the shared-alpha layer — no allocation in the hot path. */
    private val layerBounds = RectF()

    /**
     * Draws [svg] onto [canvas], aspect-fitting the SVG viewport into
     * [left]..[right] × [top]..[bottom] (centered, preserving aspect ratio).
     *
     * [tint]: when non-null, overrides ALL fill and stroke colors while
     * preserving which shapes are filled vs stroked.  A tint of 0 (black) is
     * valid — nullability, not zero-ness, controls the override.
     *
     * [alpha]: 0..255 opacity applied to the SVG as a whole — the shapes are
     * flattened into an offscreen layer first, then that single layer is
     * composited with [alpha].  This matches [android.view.View.setAlpha]
     * (in int units): overlapping shapes do NOT accumulate opacity, because
     * the alpha is applied once to the finished image rather than per shape.
     */
    fun drawSvg(
        canvas: Canvas,
        left: Int, top: Int, right: Int, bottom: Int,
        svg: ParsedSVG,
        tint: Int? = null,
        alpha: Int = 255
    ) {
        if (alpha <= 0) return
        val W = svg.width
        val H = svg.height
        if (W <= 0f || H <= 0f) return

        val dstW  = (right - left).toFloat()
        val dstH  = (bottom - top).toFloat()
        val scale = minOf(dstW / W, dstH / H)
        val tx    = left + (dstW - W * scale) / 2f
        val ty    = top  + (dstH - H * scale) / 2f

        canvas.save()
        canvas.translate(tx, ty)
        canvas.scale(scale, scale)

        // Apply the shared alpha to the flattened image rather than to each
        // shape, so overlapping shapes don't accumulate opacity.  The layer is
        // bounded to the SVG viewport (current coordinate space is post-scale).
        val layerRestore = if (alpha < 255) {
            layerBounds.set(0f, 0f, W, H)
            canvas.saveLayerAlpha(layerBounds, alpha)
        } else {
            -1
        }

        for (shape in svg.shapes) {
            if (shape.hasFill) {
                paint.reset()
                paint.isAntiAlias = true
                paint.style = Paint.Style.FILL
                applyPaint(paint, shape.fillPaint, shape.fillColor, tint)
                canvas.drawPath(shape.path, paint)
            }

            if (shape.hasStroke && shape.strokeWidth > 0f) {
                paint.reset()
                paint.isAntiAlias = true
                paint.style       = Paint.Style.STROKE
                paint.strokeWidth = shape.strokeWidth
                paint.strokeCap   = shape.cap
                paint.strokeJoin  = shape.join
                paint.strokeMiter = shape.miter
                applyPaint(paint, shape.strokePaint, shape.strokeColor, tint)
                canvas.drawPath(shape.path, paint)
            }
        }

        if (layerRestore != -1) canvas.restoreToCount(layerRestore)
        canvas.restore()
    }

    /**
     * Configures [paint] for one fill or stroke.  When [tint] is non-null every
     * paint collapses to that flat color (gradients included), preserving the
     * tint contract.  Otherwise a solid color sets [Paint.color] and a gradient
     * installs a [Shader] (built once per gradient and cached).
     */
    private fun applyPaint(paint: Paint, svgPaint: SVGPaint, fallbackColor: Int, tint: Int?) {
        paint.shader = null
        if (tint != null) {
            paint.color = tint
            return
        }
        when (svgPaint) {
            is SVGPaint.Color  -> paint.color = svgPaint.color
            is SVGPaint.Linear -> applyShader(paint, linearShader(svgPaint.gradient), svgPaint.gradient.stops)
            is SVGPaint.Radial -> applyShader(paint, radialShader(svgPaint.gradient), svgPaint.gradient.stops)
            SVGPaint.None      -> paint.color = fallbackColor
        }
    }

    /**
     * Installs [shader], or — for a degenerate gradient that can't form a shader
     * (a single stop, or a zero-radius radial) — falls back to a flat fill using
     * the first stop's color so the region is still painted.
     */
    private fun applyShader(paint: Paint, shader: Shader?, stops: List<SVGGradientStop>) {
        if (shader != null) {
            paint.shader = shader
        } else if (stops.isNotEmpty()) {
            paint.color = stops.last().color
        } else {
            paint.color = 0
        }
    }

    private fun tileMode(spread: SVGSpread): Shader.TileMode = when (spread) {
        SVGSpread.REFLECT -> Shader.TileMode.MIRROR
        SVGSpread.REPEAT  -> Shader.TileMode.REPEAT
        SVGSpread.PAD     -> Shader.TileMode.CLAMP
    }

    private fun linearShader(g: SVGLinearGradient): Shader? {
        g.shader?.let { return it }
        if (g.stops.size < 2) return null  // Android needs ≥2 colors; caller falls back to flat.
        // nanosvg column-major 3×2 xform [a, b, c, d, tx, ty]: the gradient axis
        // runs from (tx, ty) at offset 0 to (a + tx, b + ty) at offset 1.
        val x0 = g.xform[4]
        val y0 = g.xform[5]
        val x1 = g.xform[0] + g.xform[4]
        val y1 = g.xform[1] + g.xform[5]
        val shader = LinearGradient(
            x0, y0, x1, y1,
            stopColors(g.stops), stopOffsets(g.stops), tileMode(g.spread)
        )
        g.shader = shader
        return shader
    }

    private fun radialShader(g: SVGRadialGradient): Shader? {
        g.shader?.let { return it }
        if (g.stops.size < 2) return null  // Android needs ≥2 colors; caller falls back to flat.
        // Center is (tx, ty); radius is xform[0]. Android's RadialGradient is
        // concentric only, so the focal point (fx, fy) is not applied.
        val cx     = g.xform[4]
        val cy     = g.xform[5]
        val radius = g.xform[0]
        if (radius <= 0f) return null
        val shader = RadialGradient(
            cx, cy, radius,
            stopColors(g.stops), stopOffsets(g.stops), tileMode(g.spread)
        )
        g.shader = shader
        return shader
    }

    private fun stopColors(stops: List<SVGGradientStop>): IntArray =
        IntArray(stops.size) { stops[it].color }

    private fun stopOffsets(stops: List<SVGGradientStop>): FloatArray =
        FloatArray(stops.size) { stops[it].offset }
}
