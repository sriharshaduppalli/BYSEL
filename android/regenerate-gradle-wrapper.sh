#!/bin/bash
# Regenerate gradle-wrapper.jar from official Gradle distribution
# This script is used in GitHub Actions when the wrapper JAR is missing
# The gradle-wrapper.jar is included in the gradle-*-bin.zip distribution

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

# Download gradle distribution ZIP - this contains gradle-wrapper.jar in lib/
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

# Extract gradle-wrapper.jar from the distribution
# The JAR is located at: gradle-8.5/lib/gradle-wrapper.jar
echo "Extracting gradle-wrapper.jar from distribution..."

# First, list what's in the ZIP to verify structure
echo "ZIP contents (first 20 files):"
unzip -l "gradle-dist.zip" | head -20

# Extract just the wrapper JAR
if ! unzip -j "gradle-dist.zip" "gradle-${GRADLE_VERSION}/lib/gradle-wrapper.jar" -d "."; then
  echo "ERROR: Failed to extract gradle-wrapper.jar from distribution"
  echo "The expected file is: gradle-${GRADLE_VERSION}/lib/gradle-wrapper.jar"
  exit 1
fi

# Verify the file was extracted and has reasonable size
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
