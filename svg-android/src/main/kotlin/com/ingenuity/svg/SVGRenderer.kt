package com.ingenuity.svg

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

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
                paint.color = tint ?: shape.fillColor
                canvas.drawPath(shape.path, paint)
            }

            if (shape.hasStroke && shape.strokeWidth > 0f) {
                paint.reset()
                paint.isAntiAlias = true
                paint.style       = Paint.Style.STROKE
                paint.color       = tint ?: shape.strokeColor
                paint.strokeWidth = shape.strokeWidth
                paint.strokeCap   = shape.cap
                paint.strokeJoin  = shape.join
                paint.strokeMiter = shape.miter
                canvas.drawPath(shape.path, paint)
            }
        }

        if (layerRestore != -1) canvas.restoreToCount(layerRestore)
        canvas.restore()
    }
}
