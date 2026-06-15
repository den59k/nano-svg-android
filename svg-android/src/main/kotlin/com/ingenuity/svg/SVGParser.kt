package com.ingenuity.svg

import android.graphics.Paint
import android.graphics.Path

/**
 * Parses SVG content into [ParsedSVG] via a single JNI call.
 *
 * The native side walks all shapes and subpaths, flattens geometry and metadata
 * into primitive arrays, converts colors to Android 0xAARRGGBB, and hands
 * everything back in one [SVGRawData] object.  Kotlin then slices those arrays
 * to build [android.graphics.Path] objects and [SVGShape] instances.
 *
 * JNI boundary is crossed exactly ONCE per [parse] call.
 */
object SVGParser {

    init {
        System.loadLibrary("svg-android")
    }

    // Single native call — crosses JNI once per parse, regardless of shape count.
    private external fun nativeParse(svgString: String): SVGRawData?

    /**
     * Parses [svg] and returns a [ParsedSVG] with fully built [Path] objects.
     * Returns null on parse failure (malformed SVG, native OOM, etc.).
     * Never throws — exceptions from the native layer are caught and suppressed.
     */
    fun parse(svg: String): ParsedSVG? {
        val raw = try {
            nativeParse(svg)
        } catch (t: Throwable) {
            null
        } ?: return null

        val shapeCount = raw.shapeFillColor.size
        val shapes = ArrayList<SVGShape>(shapeCount)

        for (si in 0 until shapeCount) {
            val flags     = raw.shapeFlags[si]
            val hasFill   = (flags and 1) != 0
            val hasStroke = (flags and 2) != 0
            val evenOdd   = (flags and 4) != 0

            val cap = when (raw.shapeStrokeCap[si]) {
                1    -> Paint.Cap.ROUND
                2    -> Paint.Cap.SQUARE
                else -> Paint.Cap.BUTT
            }
            val join = when (raw.shapeStrokeJoin[si]) {
                1    -> Paint.Join.ROUND
                2    -> Paint.Join.BEVEL
                else -> Paint.Join.MITER
            }

            shapes.add(
                SVGShape(
                    path        = buildPath(raw, si, evenOdd),
                    hasFill     = hasFill,
                    fillColor   = raw.shapeFillColor[si],
                    hasStroke   = hasStroke,
                    strokeColor = raw.shapeStrokeColor[si],
                    strokeWidth = raw.shapeStrokeWidth[si],
                    cap         = cap,
                    join        = join,
                    miter       = raw.shapeMiterLimit[si],
                    evenOdd     = evenOdd
                )
            )
        }

        return ParsedSVG(raw.width, raw.height, shapes)
    }

    /**
     * Builds one [Path] from all subpaths belonging to [shapeIdx].
     *
     * nanosvg point layout (confirmed from nanosvg.h):
     *   pts[0..1]  = subpath start (moveTo anchor)
     *   pts[2..7]  = first cubic: (cp1x,cp1y, cp2x,cp2y, ex,ey)
     *   pts[8..13] = second cubic, etc.
     *   segments   = (npts - 1) / 3
     */
    internal fun buildPath(raw: SVGRawData, shapeIdx: Int, evenOdd: Boolean): Path {
        val path = Path()
        path.fillType = if (evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING

        val subStart = raw.shapeSubpathStart[shapeIdx]
        val subEnd   = subStart + raw.shapeSubpathCount[shapeIdx]

        for (spi in subStart until subEnd) {
            val ptPairOffset = raw.subpathPtOffset[spi]  // index of first point-pair
            val npts         = raw.subpathPtCount[spi]
            val closed       = raw.subpathClosed[spi].toInt() != 0

            if (npts < 1) continue

            val base = ptPairOffset * 2  // float index of x0

            path.moveTo(raw.points[base], raw.points[base + 1])

            val segments = (npts - 1) / 3
            for (seg in 0 until segments) {
                val i = base + 2 + seg * 6  // skip the start point (2 floats), then 6 per segment
                path.cubicTo(
                    raw.points[i],     raw.points[i + 1],  // cp1
                    raw.points[i + 2], raw.points[i + 3],  // cp2
                    raw.points[i + 4], raw.points[i + 5]   // end
                )
            }

            if (closed) path.close()
        }

        return path
    }
}
