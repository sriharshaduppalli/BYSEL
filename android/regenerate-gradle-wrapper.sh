#!/bin/bash
# Regenerate gradle-wrapper.jar from official Gradle distribution
# This script is used in GitHub Actions when the wrapper JAR is missing

set -e

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
WRAPPER_DIR="$SCRIPT_DIR/gradle/wrapper"
GRADLE_VERSION="8.5"

echo "Regenerating Gradle $GRADLE_VERSION wrapper JAR..."
echo "Target directory: $WRAPPER_DIR"

# Create temporary directory
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

cd "$TEMP_DIR"

# Download gradle distribution ZIP
GRADLE_ZIP_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

echo "Downloading Gradle $GRADLE_VERSION distribution..."
if ! curl -f -L --connect-timeout 30 -o "gradle-dist.zip" "$GRADLE_ZIP_URL"; then
  echo "ERROR: Failed to download Gradle distribution from $GRADLE_ZIP_URL"
  exit 1
fi

DIST_SIZE=$(wc -c < "gradle-dist.zip" 2>/dev/null || echo 0)
echo "Downloaded gradle-$GRADLE_VERSION-bin.zip: $DIST_SIZE bytes"

if [ "$DIST_SIZE" -lt 1000000 ]; then
  echo "ERROR: Downloaded file is too small! Expected ~100MB, got $DIST_SIZE bytes"
  exit 1
fi

# List contents to find gradle-wrapper.jar
echo "Searching for gradle-wrapper.jar in distribution..."
JAR_PATH=$(unzip -l "gradle-dist.zip" | grep -o 'gradle-[^/]*/lib/gradle-wrapper.jar' | head -1)

if [ -z "$JAR_PATH" ]; then
  echo "ERROR: gradle-wrapper.jar not found in distribution!"
  echo "Available JAR files in ZIP:"
  unzip -l "gradle-dist.zip" | grep '\.jar$' | head -10
  exit 1
fi

echo "Found: $JAR_PATH"

# Extract the JAR
if ! unzip -j "gradle-dist.zip" "$JAR_PATH" -d "."; then
  echo "ERROR: Failed to extract gradle-wrapper.jar from $JAR_PATH"
  exit 1
fi

# Verify the file was extracted
if [ ! -f "gradle-wrapper.jar" ]; then
  echo "ERROR: gradle-wrapper.jar not found after extraction"
  exit 1
fi

FILE_SIZE=$(wc -c < "gradle-wrapper.jar" 2>/dev/null || echo 0)
echo "Extracted gradle-wrapper.jar: $FILE_SIZE bytes"

if [ "$FILE_SIZE" -lt 1000000 ]; then
  echo "ERROR: Extracted file is too small! Expected ~1-2MB, got $FILE_SIZE bytes"
  exit 1
fi

# Verify it's actually a ZIP file (JAR is just a ZIP)
if ! file "gradle-wrapper.jar" | grep -q "Zip\|JAR"; then
  echo "ERROR: Extracted file doesn't appear to be a valid JAR/ZIP file!"
  file "gradle-wrapper.jar" || true
  exit 1
fi

# Create wrapper directory if it doesn't exist
mkdir -p "$WRAPPER_DIR"

# Copy to wrapper directory
echo "Copying wrapper JAR to $WRAPPER_DIR..."
cp "gradle-wrapper.jar" "$WRAPPER_DIR/gradle-wrapper.jar"

# Verify the file was copied successfully
if [ -f "$WRAPPER_DIR/gradle-wrapper.jar" ]; then
  FINAL_SIZE=$(wc -c < "$WRAPPER_DIR/gradle-wrapper.jar")
  echo "✓ Gradle wrapper JAR regenerated successfully!"
  echo "  Location: $WRAPPER_DIR/gradle-wrapper.jar"
  echo "  Size: $FINAL_SIZE bytes"
else
  echo "ERROR: Failed to copy gradle-wrapper.jar to $WRAPPER_DIR"
  exit 1
fi
