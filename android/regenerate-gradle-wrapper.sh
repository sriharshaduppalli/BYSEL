#!/bin/bash
# Regenerate gradle-wrapper.jar from official source
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

# Download gradle-wrapper.jar directly
# The wrapper JAR is located in the gradle/wrapper directory of the gradle repository
echo "Downloading gradle-wrapper.jar..."

# Primary source: Gradle's official repository on GitHub
GITHUB_URL="https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"

if curl -f -L -o "gradle-wrapper.jar" "$GITHUB_URL" 2>/dev/null; then
  echo "Downloaded from GitHub"
else
  echo "GitHub download failed, trying alternate source..."
  # Alternate: Try jcenter/bintray mirror
  curl -f -L -o "gradle-wrapper.jar" \
    "https://repo1.maven.org/maven2/org/gradle/gradle-wrapper/${GRADLE_VERSION}/gradle-wrapper-${GRADLE_VERSION}.jar" 2>/dev/null || \
    curl -f -L -o "gradle-wrapper.jar" \
      "https://gradle-release-repository.gradle.org/gradle-release-repository/org/gradle/gradle-wrapper/${GRADLE_VERSION}/gradle-wrapper-${GRADLE_VERSION}.jar"
fi

# Verify the file was downloaded and has reasonable size
FILE_SIZE=$(wc -c < "gradle-wrapper.jar" 2>/dev/null || echo 0)
echo "Downloaded file size: $FILE_SIZE bytes"

if [ "$FILE_SIZE" -lt 100000 ]; then
  echo "ERROR: Downloaded file is too small! Expected ~1-2MB, got $FILE_SIZE bytes"
  echo "This usually means GitHub/Maven returned an error page instead of the JAR"
  echo "File content:"
  head -c 200 "gradle-wrapper.jar" || true
  exit 1
fi

# Verify it's actually a ZIP file (JAR is just a ZIP)
if ! file "gradle-wrapper.jar" | grep -q "Zip\|JAR"; then
  echo "ERROR: Downloaded file doesn't appear to be a valid JAR/ZIP file!"
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
  ls -lh "$WRAPPER_DIR/gradle-wrapper.jar"
else
  echo "ERROR: Failed to copy gradle-wrapper.jar to $WRAPPER_DIR"
  exit 1
fi
