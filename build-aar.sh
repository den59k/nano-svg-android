#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# build-aar.sh — compile svg-android into a release AAR
#
# Usage:
#   ./build-aar.sh              # builds release AAR
#   ./build-aar.sh --debug      # builds debug AAR instead
#   ./build-aar.sh --clean      # clean before building
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE=":svg-android"
VARIANT="Release"
CLEAN=false

for arg in "$@"; do
    case "$arg" in
        --debug) VARIANT="Debug" ;;
        --clean) CLEAN=true ;;
        *) echo "Unknown option: $arg"; exit 1 ;;
    esac
done

TASK="assemble${VARIANT}"
AAR_NAME="svg-android-$(echo "$VARIANT" | tr '[:upper:]' '[:lower:]').aar"
AAR_SRC="$SCRIPT_DIR/svg-android/build/outputs/aar/$AAR_NAME"
AAR_DEST="$SCRIPT_DIR/$AAR_NAME"

cd "$SCRIPT_DIR"

if [[ "$CLEAN" == true ]]; then
    echo ">>> Cleaning..."
    ./gradlew "$MODULE:clean"
fi

echo ">>> Building $VARIANT AAR..."
./gradlew "$MODULE:$TASK"

if [[ -f "$AAR_SRC" ]]; then
    cp "$AAR_SRC" "$AAR_DEST"
    echo ""
    echo "✓ AAR ready: $AAR_DEST"
    echo "  Size: $(du -h "$AAR_DEST" | cut -f1)"
else
    echo "ERROR: expected AAR not found at $AAR_SRC" >&2
    exit 1
fi
