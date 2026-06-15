# android-svg

A small Android library that parses SVG files with [nanosvg](https://github.com/memononen/nanosvg) (C, via JNI) and renders them straight onto a `Canvas` using native `Path`/`Paint` — no bitmap decode, no `WebView`.

- **Module:** `:svg-android` &nbsp;·&nbsp; **Package:** `com.ingenuity.svg`
- **minSdk** 21 &nbsp;·&nbsp; **compileSdk** 34 &nbsp;·&nbsp; NDK + CMake, C++17
- **ABIs:** `arm64-v8a`, `armeabi-v7a`, `x86_64`
- **iOS counterpart:** [NanoSVG-IOS](https://github.com/den59k/NanoSVG-IOS)

## Usage

```kotlin
// Parse once (e.g. off the main thread), then reuse the result.
val svg: ParsedSVG = SVGParser.parse(svgString) ?: return  // null on malformed input

// Draw, aspect-fitted and centered into the target rect.
SVGRenderer.drawSvg(canvas, left, top, right, bottom, svg)

// Optional: recolor every shape, and/or apply a shared opacity (0..255).
SVGRenderer.drawSvg(canvas, left, top, right, bottom, svg, tint = Color.RED, alpha = 128)
```

- `tint` (nullable `Int`) overrides **all** fill/stroke colors while preserving which shapes are filled vs. stroked. `0` (black) is a valid tint — nullability, not zero-ness, controls the override.
- `alpha` (`0..255`) is applied to the SVG **as a whole** via an offscreen layer, so overlapping shapes don't accumulate opacity (matches `View.setAlpha`).

## How it works

`SVGParser.parse` crosses the JNI boundary **exactly once**. The native layer walks every shape and subpath, flattens geometry and metadata into primitive arrays, converts colors from nanosvg's `0xAABBGGRR` to Android `0xAARRGGBB` (folding in shape opacity), and returns a single `SVGRawData`. Kotlin then slices those arrays into `android.graphics.Path` objects and immutable `SVGShape`s.

```
SVG string ──JNI──▶ nanosvg (C) ──▶ SVGRawData (primitive arrays)
                                          │
                              SVGParser builds Paths
                                          ▼
                          ParsedSVG ──▶ SVGRenderer.drawSvg ──▶ Canvas
```

`SVGRenderer` is a stateless object that reuses one `Paint` across draws (no allocation in the hot path); do not call `drawSvg` from multiple threads concurrently.

## Limitations

Inherited from nanosvg: cubic-Bézier path geometry, solid fills/strokes, fill rules, caps/joins/miter. **Gradients, patterns, and text are not rendered** — a gradient fill/stroke surfaces as "absent" (color `0`).

## Build

```bash
./gradlew :svg-android:assembleRelease
```

Requires the Android NDK (`ndkVersion` is pinned in [svg-android/build.gradle.kts](svg-android/build.gradle.kts)). The native build (CMake) compiles the bundled nanosvg sources under [nanosvg/](nanosvg/) into `libsvg-android.so`.

## Layout

| Path | Purpose |
|------|---------|
| [svg-android/src/main/kotlin/com/ingenuity/svg/](svg-android/src/main/kotlin/com/ingenuity/svg/) | Public API: `SVGParser`, `SVGRenderer`, `ParsedSVG`/`SVGShape`, `SVGRawData` |
| [svg-android/src/main/cpp/](svg-android/src/main/cpp/) | JNI bridge (`svg_jni.cpp`) + nanosvg impl unit |
| [nanosvg/](nanosvg/) | Vendored upstream nanosvg (see its own `LICENSE.txt`) |

## License

The vendored nanosvg sources are under their upstream license ([nanosvg/LICENSE.txt](nanosvg/LICENSE.txt)).
