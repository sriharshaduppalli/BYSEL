#!/bin/bash
# Regenerate gradle-wrapper.jar from Gradle distribution
# This script is used in GitHub Actions when the wrapper JAR is missing

set -e

WRAPPER_DIR="android/gradle/wrapper"
GRADLE_VERSION="8.5"

echo "Regenerating Gradle $GRADLE_VERSION wrapper JAR..."

# Create temporary directory
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

cd "$TEMP_DIR"

# Download Gradle distribution
echo "Downloading Gradle $GRADLE_VERSION..."
curl -L -o "gradle-${GRADLE_VERSION}-bin.zip" \
  "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

# Extract entire distribution
echo "Extracting Gradle distribution..."
unzip -q "gradle-${GRADLE_VERSION}-bin.zip"

# Find and copy the wrapper JAR (it could be in lib/ or lib/plugins/)
echo "Locating wrapper JAR..."
WRAPPER_JAR=$(find "gradle-${GRADLE_VERSION}" -name "gradle-wrapper.jar" -type f | head -1)

if [ -z "$WRAPPER_JAR" ]; then
  echo "ERROR: Could not find gradle-wrapper.jar in distribution!"
  echo "Available files:"
  find "gradle-${GRADLE_VERSION}" -type f -name "*.jar" | head -10
  exit 1
fi

echo "Found wrapper JAR at: $WRAPPER_JAR"

# Copy to wrapper directory
echo "Copying wrapper JAR to $WRAPPER_DIR..."
cp "$WRAPPER_JAR" "$WRAPPER_DIR/gradle-wrapper.jar"

echo "Gradle wrapper JAR regenerated successfully!"
ls -lh "$WRAPPER_DIR/gradle-wrapper.jar"
