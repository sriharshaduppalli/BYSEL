#!/bin/bash
# Regenerate gradle-wrapper.jar using gradle wrapper task
# This script uses Gradle itself to generate a proper wrapper JAR

set -e

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
GRADLE_VERSION="8.5"

echo "Regenerating Gradle $GRADLE_VERSION wrapper JAR..."
echo "Target directory: $SCRIPT_DIR"

# Create temporary directory
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

cd "$TEMP_DIR"

# Download Gradle standalone distribution
GRADLE_ZIP_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

echo "Downloading Gradle $GRADLE_VERSION..."
if ! curl -f -L --connect-timeout 30 -o "gradle.zip" "$GRADLE_ZIP_URL"; then
  echo "ERROR: Failed to download Gradle from $GRADLE_ZIP_URL"
  exit 1
fi

DIST_SIZE=$(wc -c < "gradle.zip" 2>/dev/null || echo 0)
if [ "$DIST_SIZE" -lt 50000000 ]; then
  echo "ERROR: Downloaded file too small ($DIST_SIZE bytes)"
  exit 1
fi

echo "Extracting Gradle..."
unzip -q "gradle.zip"

# Find the gradle executable
GRADLE_BIN=$(find . -name "gradle" -type f -executable 2>/dev/null | head -1)
if [ -z "$GRADLE_BIN" ]; then
  echo "ERROR: Could not find gradle executable in distribution"
  exit 1
fi

echo "Found gradle at: $GRADLE_BIN"

# Initialize a minimal gradle project
mkdir -p "$TEMP_DIR/minimal-gradle-project"
cd "$TEMP_DIR/minimal-gradle-project"

# Create minimal build.gradle
cat > build.gradle << 'EOF'
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}
EOF

# Create settings.gradle
cat > settings.gradle << 'EOF'
rootProject.name = 'minimal'
EOF

# Run gradle wrapper task to generate wrapper files
echo "Generating wrapper files..."
if ! "$GRADLE_BIN" wrapper --gradle-version "$GRADLE_VERSION" --warning-mode none 2>/dev/null; then
  echo "ERROR: Failed to generate wrapper"
  exit 1
fi

# Verify wrapper.jar was created
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "ERROR: gradle-wrapper.jar not found after generation"
  exit 1
fi

JAR_SIZE=$(wc -c < "gradle/wrapper/gradle-wrapper.jar")
echo "Generated gradle-wrapper.jar: $JAR_SIZE bytes"

if [ "$JAR_SIZE" -lt 100000 ]; then
  echo "ERROR: Generated JAR too small ($JAR_SIZE bytes)"
  exit 1
fi

# Copy to target directory
echo "Copying wrapper JAR to $SCRIPT_DIR/gradle/wrapper/..."
mkdir -p "$SCRIPT_DIR/gradle/wrapper"
cp "gradle/wrapper/gradle-wrapper.jar" "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar"

# Verify copy
if [ -f "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
  FINAL_SIZE=$(wc -c < "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar")
  echo "✓ Gradle wrapper JAR regenerated successfully!"
  echo "  Location: $SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar"
  echo "  Size: $FINAL_SIZE bytes"
  exit 0
else
  echo "ERROR: Failed to copy gradle-wrapper.jar"
  exit 1
fi
