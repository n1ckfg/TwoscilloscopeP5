#!/bin/bash
# Builds ../library/TwoscilloscopeP5.jar from twoscilloscopeP5/*.java, the
# Hershey fonts and the beam shader, and with --docs, the javadoc in
# ../reference. Finds Processing's core.jar and JDK on Linux and macOS; set
# CORE_JAR (and JAVA_HOME) to point at them yourself if it can't.

set -e
cd "$(dirname "${BASH_SOURCE[0]}")"

if [ -z "$CORE_JAR" ]; then
  for c in \
    /opt/processing/lib/app/resources/core/library/core-*.jar \
    /usr/share/processing/lib/app/resources/core/library/core-*.jar \
    /usr/share/processing/core/library/core.jar \
    "$HOME"/processing-4*/core/library/core.jar \
    /Applications/Processing.app/Contents/app/resources/core/library/core-*.jar \
    /Applications/Processing.app/Contents/Java/core/library/core.jar; do
    if [ -f "$c" ]; then CORE_JAR="$c"; break; fi
  done
fi
if [ ! -f "$CORE_JAR" ]; then
  echo "Couldn't find Processing's core.jar: run with CORE_JAR=/path/to/core.jar $0"
  exit 1
fi

# Processing's own JDK if it has one, so the classes match the Java it runs
if [ -z "$JAVA_HOME" ]; then
  CORE_DIR="$(cd "$(dirname "$CORE_JAR")" && pwd)"
  for j in "$CORE_DIR/../../jdk" "$CORE_DIR/../../../jdk" "$CORE_DIR/../../../Contents/PlugIns/jdk"*/Contents/Home; do
    if [ -x "$j/bin/javac" ]; then JAVA_HOME="$(cd "$j" && pwd)"; break; fi
  done
fi
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAR="${JAVA_HOME:+$JAVA_HOME/bin/}jar"
JAVADOC="${JAVA_HOME:+$JAVA_HOME/bin/}javadoc"

echo "core: $CORE_JAR"
echo "javac: $JAVAC"

rm -rf build/classes
mkdir -p build/classes/twoscilloscopeP5/hershey_fonts build/classes/twoscilloscopeP5/shaders ../library
"$JAVAC" --release 17 -encoding UTF-8 -Xlint:unchecked -cp "$CORE_JAR" -d build/classes twoscilloscopeP5/*.java
cp twoscilloscopeP5/hershey_fonts/* build/classes/twoscilloscopeP5/hershey_fonts/
cp twoscilloscopeP5/shaders/* build/classes/twoscilloscopeP5/shaders/
"$JAR" cfm ../library/TwoscilloscopeP5.jar build/manifest.txt -C build/classes .
rm -rf build/classes
echo "built ../library/TwoscilloscopeP5.jar"

if [ "$1" == "--docs" ]; then
  rm -rf ../reference
  "$JAVADOC" -quiet -Xdoclint:none -notimestamp -encoding UTF-8 -cp "$CORE_JAR" -d ../reference \
    -windowtitle "TwoscilloscopeP5" -doctitle "TwoscilloscopeP5" twoscilloscopeP5/*.java
  echo "wrote ../reference"
fi
