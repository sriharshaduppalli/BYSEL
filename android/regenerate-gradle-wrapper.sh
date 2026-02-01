#!/bin/bash
# Regenerate gradle-wrapper.jar from official source
# This script is used in GitHub Actions when the wrapper JAR is missing

set -e

WRAPPER_DIR="android/gradle/wrapper"
GRADLE_VERSION="8.5"

echo "Regenerating Gradle $GRADLE_VERSION wrapper JAR..."

# Create temporary directory
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

cd "$TEMP_DIR"

# Download gradle-wrapper.jar directly from Gradle's GitHub releases
# The wrapper JAR is distributed separately from the main Gradle distribution
echo "Downloading gradle-wrapper.jar from GitHub..."

# Try multiple sources
if curl -L -o "gradle-wrapper.jar" \
  "https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null; then
  echo "Downloaded from GitHub gradle repository"
elif curl -L -o "gradle-wrapper.jar" \
  "https://github.com/gradle/gradle/raw/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null; then
  echo "Downloaded from GitHub"
else
  # Fallback: Download from Maven Central (wrapper JARs are sometimes mirrored there)
  echo "GitHub download failed, trying Maven Central..."
  curl -L -o "gradle-wrapper.jar" \
    "https://repo.maven.apache.org/maven2/org/gradle/gradle-wrapper/${GRADLE_VERSION}/gradle-wrapper-${GRADLE_VERSION}.jar"
fi

# Verify the file was downloaded and has content
if [ ! -s "gradle-wrapper.jar" ]; then
  echo "ERROR: Failed to download gradle-wrapper.jar!"
  echo "The file is either missing or empty."
  exit 1
fi

FILE_SIZE=$(wc -c < "gradle-wrapper.jar")
echo "Downloaded gradle-wrapper.jar (size: $FILE_SIZE bytes)"

# Copy to wrapper directory
echo "Copying wrapper JAR to $WRAPPER_DIR..."
cp "gradle-wrapper.jar" "$WRAPPER_DIR/gradle-wrapper.jar"

echo "Gradle wrapper JAR regenerated successfully!"
ls -lh "$WRAPPER_DIR/gradle-wrapper.jar"
