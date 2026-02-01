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

# Download gradle-wrapper.jar
# Primary source: Maven Central Repository (most reliable)
echo "Downloading gradle-wrapper.jar from Maven Central..."

MAVEN_URL="https://repo.maven.apache.org/maven2/org/gradle/gradle-wrapper/${GRADLE_VERSION}/gradle-wrapper-${GRADLE_VERSION}.jar"

if curl -f -L --connect-timeout 10 -o "gradle-wrapper.jar" "$MAVEN_URL"; then
  echo "✓ Downloaded from Maven Central"
else
  echo "Maven Central failed, trying Gradle GitHub releases..."
  # Fallback: GitHub releases
  GITHUB_URL="https://github.com/gradle/gradle/releases/download/v${GRADLE_VERSION}/gradle-${GRADLE_VERSION}-wrapper.jar"
  if curl -f -L --connect-timeout 10 -o "gradle-wrapper.jar" "$GITHUB_URL"; then
    echo "✓ Downloaded from GitHub"
  else
    echo "GitHub failed, trying raw.githubusercontent.com..."
    # Last resort: raw GitHub content
    curl -f -L --connect-timeout 10 -o "gradle-wrapper.jar" \
      "https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"
    echo "✓ Downloaded from raw.githubusercontent.com"
  fi
fi

# Verify the file was downloaded and has reasonable size
FILE_SIZE=$(wc -c < "gradle-wrapper.jar" 2>/dev/null || echo 0)
echo "Downloaded file size: $FILE_SIZE bytes"

if [ "$FILE_SIZE" -lt 100000 ]; then
  echo "ERROR: Downloaded file is too small! Expected ~1-2MB, got $FILE_SIZE bytes"
  echo "The download may have failed or returned an error page."
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
