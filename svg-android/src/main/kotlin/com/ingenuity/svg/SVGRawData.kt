package com.ingenuity.svg

/**
 * Plain holder populated by native code via JNI field assignment (one object creation,
 * constant JNI overhead regardless of shape or curve count).
 *
 * Point layout (matches nanosvg NSVGpath):
 *   points[subpathPtOffset[i]*2 .. ] holds npts×2 floats.
 *   Point 0 is the subpath start (moveTo). Every subsequent group of 3 points is
 *   (control1, control2, end) of one cubic Bézier.  Segments = (npts-1)/3.
 *
 * Color encoding: 0xAARRGGBB (Android convention). Converted from nanosvg's
 * 0xAABBGGRR on the native side; shape opacity is folded into alpha.
 *
 * shapeFlags bit field:
 *   bit 0 (1) = hasFill
 *   bit 1 (2) = hasStroke
 *   bit 2 (4) = evenOdd fill rule
 *
 * Paint type codes (shapeFillType / shapeStrokeType):
 *   0 = none, 1 = solid color, 2 = linear gradient, 3 = radial gradient
 *
 * Gradients are flattened into a side table: each fill/stroke that is a gradient
 * stores an index into the per-gradient parallel arrays via shapeFillGradient /
 * shapeStrokeGradient (-1 when the paint is not a gradient).  Variable-length stop
 * lists are concatenated into gradStopColors / gradStopOffsets and sliced per
 * gradient with gradStopStart / gradStopCount.
 */
class SVGRawData {
    @JvmField var width: Float = 0f
    @JvmField var height: Float = 0f

    // All cubic bezier points for all subpaths, concatenated (x,y pairs).
    @JvmField var points: FloatArray = FloatArray(0)

    // Per-subpath parallel arrays.
    @JvmField var subpathPtOffset: IntArray = IntArray(0)   // index of first point-pair in points[]
    @JvmField var subpathPtCount: IntArray = IntArray(0)    // npts (total bezier control points)
    @JvmField var subpathClosed: ByteArray = ByteArray(0)   // 1 = closed, 0 = open
    @JvmField var subpathShapeIndex: IntArray = IntArray(0) // which shape owns this subpath

    // Per-shape parallel arrays.
    @JvmField var shapeFillColor: IntArray = IntArray(0)    // 0xAARRGGBB; 0 if gradient/absent
    @JvmField var shapeStrokeColor: IntArray = IntArray(0)  // 0xAARRGGBB; 0 if gradient/absent
    @JvmField var shapeFlags: IntArray = IntArray(0)        // bit0=hasFill, bit1=hasStroke, bit2=evenOdd
    @JvmField var shapeStrokeWidth: FloatArray = FloatArray(0)
    @JvmField var shapeStrokeCap: IntArray = IntArray(0)    // NSVG_CAP_* values
    @JvmField var shapeStrokeJoin: IntArray = IntArray(0)   // NSVG_JOIN_* values
    @JvmField var shapeMiterLimit: FloatArray = FloatArray(0)
    @JvmField var shapeSubpathStart: IntArray = IntArray(0) // first subpath index for this shape
    @JvmField var shapeSubpathCount: IntArray = IntArray(0) // number of subpaths for this shape

    // Per-shape paint kind: 0=none, 1=color, 2=linear gradient, 3=radial gradient.
    @JvmField var shapeFillType: IntArray = IntArray(0)
    @JvmField var shapeStrokeType: IntArray = IntArray(0)
    // Index into the gradient table below, or -1 when the paint is not a gradient.
    @JvmField var shapeFillGradient: IntArray = IntArray(0)
    @JvmField var shapeStrokeGradient: IntArray = IntArray(0)

    // Gradient table (parallel arrays, one entry per gradient referenced by a paint).
    @JvmField var gradXform: FloatArray = FloatArray(0)     // 6 floats per gradient [a,b,c,d,tx,ty]
    @JvmField var gradSpread: IntArray = IntArray(0)        // 0=pad, 1=reflect, 2=repeat
    @JvmField var gradFx: FloatArray = FloatArray(0)        // radial focal x (gradient space, 0..1)
    @JvmField var gradFy: FloatArray = FloatArray(0)        // radial focal y (gradient space, 0..1)
    @JvmField var gradStopStart: IntArray = IntArray(0)     // first stop index in gradStop* arrays
    @JvmField var gradStopCount: IntArray = IntArray(0)     // number of stops for this gradient

    // Concatenated gradient stops across all gradients.
    @JvmField var gradStopColors: IntArray = IntArray(0)    // 0xAARRGGBB per stop
    @JvmField var gradStopOffsets: FloatArray = FloatArray(0) // 0..1 position per stop
}
