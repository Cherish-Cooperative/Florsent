#!/bin/sh
# Regenerates shared/test-fixtures/breathing_golden.json from the Android sources (read-only):
#   1. compiles app/.../domain/Breathing.kt with the Kotlin compiler from the Gradle cache and dumps
#      the timeline (BreathingGoldenExporter.kt);
#   2. merges in the copy (strings.xml, en + zh-rTW) and the sound constants (export_golden.py).
# Needs: a JDK (Android Studio's is fine), the Kotlin jars in ~/.gradle (run ./gradlew once), python3.
set -eu
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
CACHE="$HOME/.gradle/caches/modules-2/files-2.1"
KV=${KOTLIN_VERSION:-$(sed -n 's/^kotlin *= *"\(.*\)"/\1/p' "$ROOT/gradle/libs.versions.toml")}
jar() { find "$CACHE/$1" -name "$2-$3.jar" | head -1; }
JAVA=${JAVA:-java}
command -v "$JAVA" >/dev/null 2>&1 || JAVA="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java"
STDLIB=$(jar org.jetbrains.kotlin kotlin-stdlib "$KV")
CP="$(jar org.jetbrains.kotlin kotlin-compiler-embeddable "$KV"):$STDLIB:$(jar org.jetbrains.kotlin kotlin-script-runtime "$KV"):$(jar org.jetbrains.kotlin kotlin-reflect "$KV"):$(jar org.jetbrains.kotlin kotlin-daemon-embeddable "$KV"):$(find "$CACHE/org.jetbrains.intellij.deps/trove4j" -name 'trove4j-*.jar' ! -name '*sources*' | head -1):$(find "$CACHE/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm" -name '*.jar' ! -name '*sources*' | sort | tail -1):$(find "$CACHE/org.jetbrains/annotations" -name 'annotations-13.0.jar' | head -1)"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
"$JAVA" -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$STDLIB" -d "$WORK/classes" \
  "$ROOT/app/src/main/java/app/florsent/domain/Breathing.kt" "$ROOT/shared/tools/BreathingGoldenExporter.kt" 2>&1 | grep -v '^warning' || true
"$JAVA" -cp "$WORK/classes:$STDLIB" BreathingGoldenExporterKt > "$WORK/timeline.json"
python3 "$ROOT/shared/tools/export_golden.py" "$ROOT" "$WORK/timeline.json" "$ROOT/shared/test-fixtures/breathing_golden.json"
