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

# gradle-wrapper.jar is NOT in the gradle-*-bin.zip distribution
# It must be downloaded from official repositories
echo "Downloading gradle-wrapper.jar from official sources..."

# Try multiple sources
if curl -f -L --connect-timeout 10 -o "gradle-wrapper.jar" \
  "https://repo.gradle.org/gradle-release-repository/org/gradle/gradle-wrapper/${GRADLE_VERSION}/gradle-wrapper-${GRADLE_VERSION}.jar" 2>/dev/null; then
  echo "✓ Downloaded from Gradle official repository"
elif curl -f -L --connect-timeout 10 -o "gradle-wrapper.jar" \
  "https://repo1.maven.org/maven2/org/gradle/gradle-wrapper/${GRADLE_VERSION}/gradle-wrapper-${GRADLE_VERSION}.jar" 2>/dev/null; then
  echo "✓ Downloaded from Maven Central"
elif curl -f -L --connect-timeout 10 -o "gradle-wrapper.jar" \
  "https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null; then
  echo "✓ Downloaded from GitHub"
else
  echo "ERROR: Failed to download gradle-wrapper.jar from all sources"
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
