#include <jni.h>
#include <string.h>
#include <math.h>
#include <vector>
#include <android/log.h>

#include "nanosvg.h"

#define LOG_TAG "SVGParser"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Cached JNI state — populated in JNI_OnLoad, never changes afterward.
// ---------------------------------------------------------------------------
static jclass    g_rawDataClass       = nullptr;
static jmethodID g_rawDataCtor        = nullptr;
static jfieldID  g_fWidth             = nullptr;
static jfieldID  g_fHeight            = nullptr;
static jfieldID  g_fPoints            = nullptr;
static jfieldID  g_fSubpathPtOffset   = nullptr;
static jfieldID  g_fSubpathPtCount    = nullptr;
static jfieldID  g_fSubpathClosed     = nullptr;
static jfieldID  g_fSubpathShapeIndex = nullptr;
static jfieldID  g_fShapeFillColor    = nullptr;
static jfieldID  g_fShapeStrokeColor  = nullptr;
static jfieldID  g_fShapeFlags        = nullptr;
static jfieldID  g_fShapeStrokeWidth  = nullptr;
static jfieldID  g_fShapeStrokeCap    = nullptr;
static jfieldID  g_fShapeStrokeJoin   = nullptr;
static jfieldID  g_fShapeMiterLimit   = nullptr;
static jfieldID  g_fShapeSubpathStart = nullptr;
static jfieldID  g_fShapeSubpathCount = nullptr;
static jfieldID  g_fShapeFillType     = nullptr;
static jfieldID  g_fShapeStrokeType   = nullptr;
static jfieldID  g_fShapeFillGradient   = nullptr;
static jfieldID  g_fShapeStrokeGradient = nullptr;
static jfieldID  g_fGradXform         = nullptr;
static jfieldID  g_fGradSpread        = nullptr;
static jfieldID  g_fGradFx            = nullptr;
static jfieldID  g_fGradFy            = nullptr;
static jfieldID  g_fGradStopStart     = nullptr;
static jfieldID  g_fGradStopCount     = nullptr;
static jfieldID  g_fGradStopColors    = nullptr;
static jfieldID  g_fGradStopOffsets   = nullptr;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass cls = env->FindClass("com/ingenuity/svg/SVGRawData");
    if (!cls) { LOGE("SVGRawData class not found"); return JNI_ERR; }
    g_rawDataClass = static_cast<jclass>(env->NewGlobalRef(cls));

    g_rawDataCtor        = env->GetMethodID(cls, "<init>", "()V");
    g_fWidth             = env->GetFieldID(cls, "width",             "F");
    g_fHeight            = env->GetFieldID(cls, "height",            "F");
    g_fPoints            = env->GetFieldID(cls, "points",            "[F");
    g_fSubpathPtOffset   = env->GetFieldID(cls, "subpathPtOffset",   "[I");
    g_fSubpathPtCount    = env->GetFieldID(cls, "subpathPtCount",    "[I");
    g_fSubpathClosed     = env->GetFieldID(cls, "subpathClosed",     "[B");
    g_fSubpathShapeIndex = env->GetFieldID(cls, "subpathShapeIndex", "[I");
    g_fShapeFillColor    = env->GetFieldID(cls, "shapeFillColor",    "[I");
    g_fShapeStrokeColor  = env->GetFieldID(cls, "shapeStrokeColor",  "[I");
    g_fShapeFlags        = env->GetFieldID(cls, "shapeFlags",        "[I");
    g_fShapeStrokeWidth  = env->GetFieldID(cls, "shapeStrokeWidth",  "[F");
    g_fShapeStrokeCap    = env->GetFieldID(cls, "shapeStrokeCap",    "[I");
    g_fShapeStrokeJoin   = env->GetFieldID(cls, "shapeStrokeJoin",   "[I");
    g_fShapeMiterLimit   = env->GetFieldID(cls, "shapeMiterLimit",   "[F");
    g_fShapeSubpathStart = env->GetFieldID(cls, "shapeSubpathStart", "[I");
    g_fShapeSubpathCount = env->GetFieldID(cls, "shapeSubpathCount", "[I");
    g_fShapeFillType       = env->GetFieldID(cls, "shapeFillType",       "[I");
    g_fShapeStrokeType     = env->GetFieldID(cls, "shapeStrokeType",     "[I");
    g_fShapeFillGradient   = env->GetFieldID(cls, "shapeFillGradient",   "[I");
    g_fShapeStrokeGradient = env->GetFieldID(cls, "shapeStrokeGradient", "[I");
    g_fGradXform         = env->GetFieldID(cls, "gradXform",         "[F");
    g_fGradSpread        = env->GetFieldID(cls, "gradSpread",        "[I");
    g_fGradFx            = env->GetFieldID(cls, "gradFx",            "[F");
    g_fGradFy            = env->GetFieldID(cls, "gradFy",            "[F");
    g_fGradStopStart     = env->GetFieldID(cls, "gradStopStart",     "[I");
    g_fGradStopCount     = env->GetFieldID(cls, "gradStopCount",     "[I");
    g_fGradStopColors    = env->GetFieldID(cls, "gradStopColors",    "[I");
    g_fGradStopOffsets   = env->GetFieldID(cls, "gradStopOffsets",   "[F");

    return JNI_VERSION_1_6;
}

// ---------------------------------------------------------------------------
// Color conversion: nanosvg 0xAABBGGRR -> Android 0xAARRGGBB.
// Shape opacity (0.0–1.0) is multiplied into the alpha byte.
// ---------------------------------------------------------------------------
static jint convertColor(unsigned int c, float opacity) {
    jint r = static_cast<jint>(c & 0xFF);
    jint g = static_cast<jint>((c >> 8) & 0xFF);
    jint b = static_cast<jint>((c >> 16) & 0xFF);
    float af = static_cast<float>((c >> 24) & 0xFF) * opacity;
    jint a = static_cast<jint>(af + 0.5f);
    if (a > 255) a = 255;
    return (a << 24) | (r << 16) | (g << 8) | b;
}

static jfloatArray makeFloatArray(JNIEnv* env, const std::vector<float>& v) {
    jfloatArray arr = env->NewFloatArray(static_cast<jsize>(v.size()));
    if (!v.empty())
        env->SetFloatArrayRegion(arr, 0, static_cast<jsize>(v.size()), v.data());
    return arr;
}

static jintArray makeIntArray(JNIEnv* env, const std::vector<int>& v) {
    jintArray arr = env->NewIntArray(static_cast<jsize>(v.size()));
    if (!v.empty())
        env->SetIntArrayRegion(arr, 0, static_cast<jsize>(v.size()),
                               reinterpret_cast<const jint*>(v.data()));
    return arr;
}

static jbyteArray makeByteArray(JNIEnv* env, const std::vector<jbyte>& v) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(v.size()));
    if (!v.empty())
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(v.size()), v.data());
    return arr;
}

// ---------------------------------------------------------------------------
// Single JNI entry point. Parses the whole SVG, flattens all geometry and
// metadata into primitive arrays, frees the nanosvg image, then constructs
// ONE SVGRawData Java object. Crossing the JNI boundary is O(1) w.r.t.
// shape/curve count.
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jobject JNICALL
Java_com_ingenuity_svg_SVGParser_nativeParse(JNIEnv* env, jobject /*thiz*/, jstring svgJStr) {
    if (!svgJStr) return nullptr;

    // GetStringUTFChars may return a pointer into JVM-internal memory (immutable).
    // nsvgParse mutates its input, so we copy into a private heap buffer.
    const char* svgUtf8 = env->GetStringUTFChars(svgJStr, nullptr);
    if (!svgUtf8) return nullptr;
    const size_t len = strlen(svgUtf8);
    char* buf = new char[len + 1];
    memcpy(buf, svgUtf8, len + 1);
    env->ReleaseStringUTFChars(svgJStr, svgUtf8);

    NSVGimage* image = nsvgParse(buf, "px", 96.0f);
    delete[] buf;

    if (!image) return nullptr;

    // -----------------------------------------------------------------------
    // Walk the shape/path linked lists and flatten everything into vectors.
    // No nanosvg pointer will outlive this block — nsvgDelete is called below.
    // -----------------------------------------------------------------------
    std::vector<float>  points;
    std::vector<int>    subpathPtOffset, subpathPtCount, subpathShapeIndex;
    std::vector<jbyte>  subpathClosed;
    std::vector<int>    shapeFillColor, shapeStrokeColor, shapeFlags;
    std::vector<float>  shapeStrokeWidth, shapeMiterLimit;
    std::vector<int>    shapeStrokeCap, shapeStrokeJoin;
    std::vector<int>    shapeSubpathStart, shapeSubpathCount;
    std::vector<int>    shapeFillType, shapeStrokeType;
    std::vector<int>    shapeFillGradient, shapeStrokeGradient;

    // Gradient table — appended to as gradient paints are encountered.
    std::vector<float>  gradXform;        // 6 floats per gradient
    std::vector<int>    gradSpread;
    std::vector<float>  gradFx, gradFy;
    std::vector<int>    gradStopStart, gradStopCount;
    std::vector<int>    gradStopColors;   // flattened stops
    std::vector<float>  gradStopOffsets;

    // Flattens one nanosvg gradient into the table and returns its index.
    auto appendGradient = [&](const NSVGgradient* g) -> int {
        const int gi = static_cast<int>(gradSpread.size());
        for (int i = 0; i < 6; i++) gradXform.push_back(g->xform[i]);
        gradSpread.push_back(static_cast<int>(g->spread));
        gradFx.push_back(g->fx);
        gradFy.push_back(g->fy);
        gradStopStart.push_back(static_cast<int>(gradStopColors.size()));
        gradStopCount.push_back(g->nstops);
        for (int i = 0; i < g->nstops; i++) {
            // Gradient stops carry their own alpha; shape opacity is not folded in
            // (matching the iOS implementation).
            gradStopColors.push_back(convertColor(g->stops[i].color, 1.0f));
            gradStopOffsets.push_back(g->stops[i].offset);
        }
        return gi;
    };

    // Resolves a paint into a type code (0=none,1=color,2=linear,3=radial),
    // its solid color (0 unless type==color), and a gradient-table index (-1
    // unless the paint is a gradient).
    auto resolvePaint = [&](const NSVGpaint& paint, float opacity,
                            int& outType, jint& outColor, int& outGradIdx) {
        outColor   = 0;
        outGradIdx = -1;
        switch (paint.type) {
            case NSVG_PAINT_COLOR:
                outType  = 1;
                outColor = convertColor(paint.color, opacity);
                break;
            case NSVG_PAINT_LINEAR_GRADIENT:
                outType    = paint.gradient ? 2 : 0;
                if (paint.gradient) outGradIdx = appendGradient(paint.gradient);
                break;
            case NSVG_PAINT_RADIAL_GRADIENT:
                outType    = paint.gradient ? 3 : 0;
                if (paint.gradient) outGradIdx = appendGradient(paint.gradient);
                break;
            default: // NSVG_PAINT_NONE / NSVG_PAINT_UNDEF
                outType = 0;
                break;
        }
    };

    int shapeIdx = 0;
    for (NSVGshape* shape = image->shapes; shape != nullptr; shape = shape->next) {
        const int firstSubpath   = static_cast<int>(subpathPtOffset.size());
        int       subpathsInShape = 0;

        for (NSVGpath* path = shape->paths; path != nullptr; path = path->next) {
            // subpathPtOffset is stored as point-pair index (divide by 2 to convert
            // from flat float index), so Kotlin multiplies by 2 to index into points[].
            subpathPtOffset.push_back(static_cast<int>(points.size() / 2));
            subpathPtCount.push_back(path->npts);
            subpathClosed.push_back(path->closed ? 1 : 0);
            subpathShapeIndex.push_back(shapeIdx);

            // path->pts has path->npts points × 2 floats each.
            for (int i = 0; i < path->npts * 2; i++) {
                points.push_back(path->pts[i]);
            }
            subpathsInShape++;
        }

        // Resolve fill and stroke paints (solid color or gradient). NONE and
        // UNDEF both yield type 0; any other type marks the paint as present.
        int  fillType, strokeType, fillGradIdx, strokeGradIdx;
        jint fillColor, strokeColor;
        resolvePaint(shape->fill,   shape->opacity, fillType,   fillColor,   fillGradIdx);
        resolvePaint(shape->stroke, shape->opacity, strokeType, strokeColor, strokeGradIdx);

        const bool hasFill   = (fillType   != 0);
        const bool hasStroke = (strokeType != 0);

        const int flags =
            (hasFill   ? 1 : 0) |
            (hasStroke ? 2 : 0) |
            (shape->fillRule == NSVG_FILLRULE_EVENODD ? 4 : 0);

        shapeFillColor.push_back(static_cast<int>(fillColor));
        shapeStrokeColor.push_back(static_cast<int>(strokeColor));
        shapeFlags.push_back(flags);
        shapeFillType.push_back(fillType);
        shapeStrokeType.push_back(strokeType);
        shapeFillGradient.push_back(fillGradIdx);
        shapeStrokeGradient.push_back(strokeGradIdx);
        shapeStrokeWidth.push_back(shape->strokeWidth);
        shapeStrokeCap.push_back(static_cast<int>(shape->strokeLineCap));
        shapeStrokeJoin.push_back(static_cast<int>(shape->strokeLineJoin));
        shapeMiterLimit.push_back(shape->miterLimit);
        shapeSubpathStart.push_back(firstSubpath);
        shapeSubpathCount.push_back(subpathsInShape);
        shapeIdx++;
    }

    const float width  = image->width;
    const float height = image->height;
    // All nanosvg data has been copied into our vectors — safe to free.
    nsvgDelete(image);

    // -----------------------------------------------------------------------
    // Create Java arrays (bulk copy via Set*ArrayRegion — O(n) total, but a
    // constant number of JNI calls regardless of shape/curve count).
    // -----------------------------------------------------------------------
    jobject result = env->NewObject(g_rawDataClass, g_rawDataCtor);
    if (!result) return nullptr;

    env->SetFloatField(result, g_fWidth,  static_cast<jfloat>(width));
    env->SetFloatField(result, g_fHeight, static_cast<jfloat>(height));
    env->SetObjectField(result, g_fPoints,            makeFloatArray(env, points));
    env->SetObjectField(result, g_fSubpathPtOffset,   makeIntArray(env, subpathPtOffset));
    env->SetObjectField(result, g_fSubpathPtCount,    makeIntArray(env, subpathPtCount));
    env->SetObjectField(result, g_fSubpathClosed,     makeByteArray(env, subpathClosed));
    env->SetObjectField(result, g_fSubpathShapeIndex, makeIntArray(env, subpathShapeIndex));
    env->SetObjectField(result, g_fShapeFillColor,    makeIntArray(env, shapeFillColor));
    env->SetObjectField(result, g_fShapeStrokeColor,  makeIntArray(env, shapeStrokeColor));
    env->SetObjectField(result, g_fShapeFlags,        makeIntArray(env, shapeFlags));
    env->SetObjectField(result, g_fShapeStrokeWidth,  makeFloatArray(env, shapeStrokeWidth));
    env->SetObjectField(result, g_fShapeStrokeCap,    makeIntArray(env, shapeStrokeCap));
    env->SetObjectField(result, g_fShapeStrokeJoin,   makeIntArray(env, shapeStrokeJoin));
    env->SetObjectField(result, g_fShapeMiterLimit,   makeFloatArray(env, shapeMiterLimit));
    env->SetObjectField(result, g_fShapeSubpathStart, makeIntArray(env, shapeSubpathStart));
    env->SetObjectField(result, g_fShapeSubpathCount, makeIntArray(env, shapeSubpathCount));
    env->SetObjectField(result, g_fShapeFillType,       makeIntArray(env, shapeFillType));
    env->SetObjectField(result, g_fShapeStrokeType,     makeIntArray(env, shapeStrokeType));
    env->SetObjectField(result, g_fShapeFillGradient,   makeIntArray(env, shapeFillGradient));
    env->SetObjectField(result, g_fShapeStrokeGradient, makeIntArray(env, shapeStrokeGradient));
    env->SetObjectField(result, g_fGradXform,         makeFloatArray(env, gradXform));
    env->SetObjectField(result, g_fGradSpread,        makeIntArray(env, gradSpread));
    env->SetObjectField(result, g_fGradFx,            makeFloatArray(env, gradFx));
    env->SetObjectField(result, g_fGradFy,            makeFloatArray(env, gradFy));
    env->SetObjectField(result, g_fGradStopStart,     makeIntArray(env, gradStopStart));
    env->SetObjectField(result, g_fGradStopCount,     makeIntArray(env, gradStopCount));
    env->SetObjectField(result, g_fGradStopColors,    makeIntArray(env, gradStopColors));
    env->SetObjectField(result, g_fGradStopOffsets,   makeFloatArray(env, gradStopOffsets));

    return result;
}
