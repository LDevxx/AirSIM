#!/bin/sh
# Check known pre-33 incompatibilities in emitted method references, not source spelling.
# This is a targeted regression gate, not a complete Android API lint replacement.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSES=${1:-"$ROOT/build/android/classes"}
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/javap" ]; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
  fi
fi
JAVAP="${JAVA_HOME:-}/bin/javap"
if [ ! -x "$JAVAP" ]; then
  JAVAP=$(command -v javap 2>/dev/null || echo "javap")
fi
REPORT=$(mktemp -t airsim-io-api)
trap 'rm -f "$REPORT"' EXIT HUP INT TERM
find "$CLASSES" -name '*.class' -print0 | xargs -0 "$JAVAP" -c -p > "$REPORT"
if grep -E 'java/io/[^ ]+\.(toString:\(Ljava/nio/charset/Charset;|readNBytes:|writeBytes:\(\[B\)|nullInputStream:)|java/util/[^ ]+\.toArray:\(Ljava/util/function/IntFunction;|java/lang/(CharSequence|StringBuilder|StringBuffer)\.isEmpty:|java/net/(URLEncoder|URLDecoder)\.(encode|decode):\(Ljava/lang/String;Ljava/nio/charset/Charset;' "$REPORT"; then
    echo 'FAIL: emitted Java calls require newer Android APIs, but AirSIM supports API 29+' >&2
    exit 1
fi
echo 'Android pre-33 Java API regression check passed'
