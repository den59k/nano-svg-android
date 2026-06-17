package com.ingenuity.svg

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SVGParserTest {

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun bitmap(w: Int = 100, h: Int = 100) =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    private fun hasNonTransparentPixel(bmp: Bitmap): Boolean {
        for (y in 0 until bmp.height)
            for (x in 0 until bmp.width)
                if (bmp.getPixel(x, y) ushr 24 != 0) return true
        return false
    }

    private fun pixelAt(bmp: Bitmap, x: Int, y: Int) = bmp.getPixel(x, y)

    // ------------------------------------------------------------------
    // Viewport
    // ------------------------------------------------------------------

    @Test
    fun viewport_rect() {
        val svg = """<svg width="200" height="150" xmlns="http://www.w3.org/2000/svg">
                       <rect x="0" y="0" width="200" height="150" fill="red"/>
                     </svg>"""
        val parsed = SVGParser.parse(svg)
        assertNotNull(parsed)
        assertEquals(200f, parsed!!.width,  0.5f)
        assertEquals(150f, parsed.height, 0.5f)
    }

    // ------------------------------------------------------------------
    // Shape count
    // ------------------------------------------------------------------

    @Test
    fun multiShape_count() {
        val svg = """<svg width="200" height="100" xmlns="http://www.w3.org/2000/svg">
                       <rect x="0"   y="0" width="100" height="100" fill="red"/>
                       <rect x="100" y="0" width="100" height="100" fill="blue"/>
                     </svg>"""
        val parsed = SVGParser.parse(svg)!!
        assertEquals(2, parsed.shapes.size)
    }

    // ------------------------------------------------------------------
    // Colors (Android 0xAARRGGBB)
    // ------------------------------------------------------------------

    @Test
    fun fillColor_red() {
        val svg = """<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                       <rect x="0" y="0" width="100" height="100" fill="red"/>
                     </svg>"""
        val shape = SVGParser.parse(svg)!!.shapes[0]
        assertTrue(shape.hasFill)
        assertFalse(shape.hasStroke)
        assertEquals(0xFFFF0000.toInt(), shape.fillColor)
    }

    @Test
    fun fillColor_blue() {
        val svg = """<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                       <rect x="0" y="0" width="100" height="100" fill="blue"/>
                     </svg>"""
        val shape = SVGParser.parse(svg)!!.shapes[0]
        assertEquals(0xFF0000FF.toInt(), shape.fillColor)
    }

    @Test
    fun fillColor_hex() {
        // #12AB34 = R=0x12, G=0xAB, B=0x34  → Android 0xFF12AB34
        val svg = """<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                       <rect x="0" y="0" width="100" height="100" fill="#12AB34"/>
                     </svg>"""
        val shape = SVGParser.parse(svg)!!.shapes[0]
        assertEquals(0xFF12AB34.toInt(), shape.fillColor)
    }

    @Test
    fun strokeColor_green() {
        val svg = """<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                       <circle cx="50" cy="50" r="40" fill="none" stroke="green" stroke-width="4"/>
                     </svg>"""
        val shape = SVGParser.parse(svg)!!.shapes[0]
        assertFalse(shape.hasFill)
        assertTrue(shape.hasStroke)
        assertEquals(4f, shape.strokeWidth, 0.1f)
        // CSS "green" = #008000 → 0xFF008000
        assertEquals(0xFF008000.toInt(), shape.strokeColor)
    }

    // ------------------------------------------------------------------
    // Fill rule
    // ------------------------------------------------------------------

    @Test
    fun evenOdd_flag() {
        val svg = """<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                       <path d="M10 10 L90 10 L50 90 Z" fill="blue" fill-rule="evenodd"/>
                     </svg>"""
        val shape = SVGParser.parse(svg)!!.shapes[0]
        assertTrue(shape.evenOdd)
    }

    @Test
    fun nonZero_flag() {
        val svg = """<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                       <path d="M10 10 L90 10 L50 90 Z" fill="red"/>
                     </svg>"""
        val shape = SVGParser.parse(svg)!!.shapes[0]
        assertFalse(shape.evenOdd)
    }

    // ------------------------------------------------------------------
    // Stroke properties
    // ------------------------------------------------------------------

    @Test
    fun strokeCap_round() {
        val svg = """<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                       <line x1="10" y1="50" x2="90" y2="50" stroke="black" stroke-width="8"
                             stroke-linecap="round"/>
                     </svg>"""
        val shape = SVGParser.parse(svg)!!.shapes[0]
        assertEquals(android.graphics.Paint.Cap.ROUND, shape.cap)
    }

    @Test
    fun strokeJoin_bevel() {
        val svg = """<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                       <polyline points="10,10 50,90 90,10" fill="none" stroke="black"
                                 stroke-width="4" stroke-linejoin="bevel"/>
                     </svg>"""
        val shape = SVGParser.parse(svg)!!.shapes[0]
        assertEquals(android.graphics.Paint.Join.BEVEL, shape.join)
    }

    // ------------------------------------------------------------------
    // Curve / subpath geometry
    // ------------------------------------------------------------------

    @Test
    fun cubicBez_subpathCount() {
        // A rect is four line segments → nanosvg emits 4 cubic beziers with
        // degenerate control points, all in one closed subpath.
        val svg = """<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                       <rect x="10" y="10" width="80" height="80" fill="red"/>
                     </svg>"""
        val parsed = SVGParser.parse(svg)!!
        assertEquals(1, parsed.shapes.size)
        // Path must be non-empty
        assertFalse(parsed.shapes[0].path.isEmpty)
    }

    // ------------------------------------------------------------------
    // Rendering — fill produces non-empty bitmap
    // ------------------------------------------------------------------

    @Test
    fun draw_fillProducesPixels() {
        val svg = """<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                       <rect x="10" y="10" width="80" height="80" fill="blue"/>
                     </svg>"""
        val parsed = SVGParser.parse(svg)!!
        val bmp = bitmap()
        SVGRenderer.drawSvg(Canvas(bmp), 0, 0, 100, 100, parsed)
        assertTrue("Fill should produce non-transparent pixels", hasNonTransparentPixel(bmp))
    }

    // ------------------------------------------------------------------
    // Tint: color override, not presence override
    // ------------------------------------------------------------------

    @Test
    fun tint_blackIsValid() {
        // tint=0 (black) must be applied — not skipped because 0 == null is false in Kotlin
        val svg = """<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                       <rect x="0" y="0" width="100" height="100" fill="red"/>
                     </svg>"""
        val parsed = SVGParser.parse(svg)!!
        val bmp = bitmap()
        SVGRenderer.drawSvg(Canvas(bmp), 0, 0, 100, 100, parsed, tint = 0xFF000000.toInt())
        // Center pixel must be black (alpha=FF, RGB=000000)
        assertEquals(0xFF000000.toInt(), pixelAt(bmp, 50, 50))
    }

    @Test
    fun tint_strokeOnlyStaysOutline() {
        // Stroke-only circle tinted with red must NOT produce a solid fill at center.
        val svg = """<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                       <circle cx="50" cy="50" r="40" fill="none" stroke="green" stroke-width="4"/>
                     </svg>"""
        val parsed = SVGParser.parse(svg)!!
        val bmp = bitmap()
        SVGRenderer.drawSvg(Canvas(bmp), 0, 0, 100, 100, parsed, tint = 0xFFFF0000.toInt())
        // Center pixel must be transparent (no fill, only an outline around the circle)
        assertEquals("Center should be transparent for stroke-only shape", 0, pixelAt(bmp, 50, 50))
    }

    @Test
    fun tint_null_usesShapeColor() {
        val svg = """<svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                       <rect x="0" y="0" width="100" height="100" fill="blue"/>
                     </svg>"""
        val parsed = SVGParser.parse(svg)!!
        val bmp = bitmap()
        SVGRenderer.drawSvg(Canvas(bmp), 0, 0, 100, 100, parsed, tint = null)
        // Center pixel must be blue
        assertEquals(0xFF0000FF.toInt(), pixelAt(bmp, 50, 50))
    }

    // ------------------------------------------------------------------
    // Gradients
    // ------------------------------------------------------------------

    private val linearGradientSvg = """
        <svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="g" x1="0" y1="0" x2="100" y2="0">
              <stop offset="0" stop-color="#FF0000"/>
              <stop offset="1" stop-color="#0000FF"/>
            </linearGradient>
          </defs>
          <rect x="0" y="0" width="100" height="100" fill="url(#g)"/>
        </svg>
    """.trimIndent()

    private val radialGradientSvg = """
        <svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <radialGradient id="g" cx="50" cy="50" r="50" gradientUnits="userSpaceOnUse">
              <stop offset="0" stop-color="#FFFFFF"/>
              <stop offset="1" stop-color="#000000"/>
            </radialGradient>
          </defs>
          <rect x="0" y="0" width="100" height="100" fill="url(#g)"/>
        </svg>
    """.trimIndent()

    @Test
    fun linearGradient_parsedAsPaint() {
        val shape = SVGParser.parse(linearGradientSvg)!!.shapes[0]
        assertTrue(shape.hasFill)
        // Scalar color stays 0 for gradient paints; the paint carries the gradient.
        assertEquals(0, shape.fillColor)
        val fill = shape.fillPaint
        assertTrue("Expected linear gradient, got $fill", fill is SVGPaint.Linear)
        val g = (fill as SVGPaint.Linear).gradient
        assertEquals(2, g.stops.size)
        assertEquals(6, g.xform.size)
        assertEquals(0xFFFF0000.toInt(), g.stops.first().color)
        assertEquals(0xFF0000FF.toInt(), g.stops.last().color)
    }

    @Test
    fun radialGradient_parsedAsPaint() {
        val shape = SVGParser.parse(radialGradientSvg)!!.shapes[0]
        val fill = shape.fillPaint
        assertTrue("Expected radial gradient, got $fill", fill is SVGPaint.Radial)
        assertEquals(2, (fill as SVGPaint.Radial).gradient.stops.size)
    }

    @Test
    fun linearGradient_drawsVaryingColors() {
        val parsed = SVGParser.parse(linearGradientSvg)!!
        val bmp = bitmap()
        SVGRenderer.drawSvg(Canvas(bmp), 0, 0, 100, 100, parsed)
        // Left edge skews red, right edge skews blue — they must differ.
        val left  = pixelAt(bmp, 2, 50)
        val right = pixelAt(bmp, 97, 50)
        assertTrue("Left pixel should have alpha", left ushr 24 != 0)
        assertTrue("Right pixel should have alpha", right ushr 24 != 0)
        assertNotEquals("Gradient endpoints should differ in color", left, right)
        val leftRed  = (left  shr 16) and 0xFF
        val rightRed = (right shr 16) and 0xFF
        assertTrue("Left should be redder than right", leftRed > rightRed)
    }

    @Test
    fun radialGradient_drawsCenterDifferentFromEdge() {
        val parsed = SVGParser.parse(radialGradientSvg)!!
        val bmp = bitmap()
        SVGRenderer.drawSvg(Canvas(bmp), 0, 0, 100, 100, parsed)
        val center = pixelAt(bmp, 50, 50)
        val corner = pixelAt(bmp, 2, 2)
        assertNotEquals("Radial center and edge should differ", center, corner)
    }

    @Test
    fun gradient_tintOverridesToFlat() {
        val parsed = SVGParser.parse(linearGradientSvg)!!
        val bmp = bitmap()
        SVGRenderer.drawSvg(Canvas(bmp), 0, 0, 100, 100, parsed, tint = 0xFF000000.toInt())
        // tint collapses the gradient to flat black across the whole rect.
        assertEquals(0xFF000000.toInt(), pixelAt(bmp, 2, 50))
        assertEquals(0xFF000000.toInt(), pixelAt(bmp, 97, 50))
    }

    // ------------------------------------------------------------------
    // Malformed SVG should not crash
    // ------------------------------------------------------------------

    @Test
    fun malformedSVG_returnsNull() {
        val result = SVGParser.parse("not svg at all <<<")
        // Either null (parse failure) or empty shapes — must not crash
        assertTrue(result == null || result.shapes.isEmpty())
    }
}
