#!/bin/bash
# Simple wrapper JAR download script for CI/CD environments
# Downloads pre-built gradle-wrapper.jar for offline Gradle wrapper initialization

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
WRAPPER_DIR="$SCRIPT_DIR/gradle/wrapper"
GRADLE_VERSION="8.5"

echo "Downloading gradle-wrapper.jar for Gradle $GRADLE_VERSION..."
mkdir -p "$WRAPPER_DIR"

TEMP_JAR=$(mktemp)
trap "rm -f $TEMP_JAR" EXIT

# Use a reliable mirror with fallbacks
# Source: Built by extracting from official gradle-bin.zip distribution
download_jar() {
  local url=$1
  local desc=$2
  echo "Trying $desc..."
  if curl -f -L --connect-timeout 10 -o "$TEMP_JAR" "$url" 2>/dev/null; then
    if [ $(wc -c < "$TEMP_JAR") -gt 1000000 ]; then
      cp "$TEMP_JAR" "$WRAPPER_DIR/gradle-wrapper.jar"
      echo "✓ Downloaded successfully"
      return 0
    fi
  fi
  return 1
}

# Try sources in order of preference
download_jar "https://github.com/gradle/gradle/raw/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar" "GitHub" && exit 0
download_jar "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" "Gradle CDN (will extract)" && {
  unzip -j "$WRAPPER_DIR/gradle-${GRADLE_VERSION}-bin.zip" "gradle-${GRADLE_VERSION}/lib/gradle-wrapper.jar" -d "$WRAPPER_DIR" 2>/dev/null || true
  if [ -f "$WRAPPER_DIR/gradle-wrapper.jar" ]; then
    echo "✓ Extracted from distribution"
    exit 0
  fi
}

# Last resort: generate using gradle itself
echo "Generating wrapper using Gradle..."
TEMP_BUILD=$(mktemp -d)
trap "rm -rf $TEMP_BUILD" EXIT

cd "$TEMP_BUILD"
unzip -q "$WRAPPER_DIR/gradle-${GRADLE_VERSION}-bin.zip" || true
GRADLE_BIN=$(find . -name "gradle" -type f -executable 2>/dev/null | head -1)

if [ -n "$GRADLE_BIN" ]; then
  mkdir -p project && cd project
  cat > build.gradle << 'EOF'
plugins { id 'java' }
repositories { mavenCentral() }
EOF
  cat > settings.gradle << 'EOF'
rootProject.name = 'temp'
EOF
  "$GRADLE_BIN" wrapper --gradle-version "$GRADLE_VERSION" 2>/dev/null && \
  cp gradle/wrapper/gradle-wrapper.jar "$WRAPPER_DIR/" && \
  echo "✓ Generated successfully" && exit 0
fi

echo "ERROR: Could not obtain gradle-wrapper.jar"
exit 1
