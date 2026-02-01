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

# Extract wrapper JAR
echo "Extracting wrapper JAR..."
unzip -q "gradle-${GRADLE_VERSION}-bin.zip" "gradle-${GRADLE_VERSION}/lib/gradle-wrapper.jar"

# Copy to wrapper directory
echo "Copying wrapper JAR to $WRAPPER_DIR..."
cp "gradle-${GRADLE_VERSION}/lib/gradle-wrapper.jar" "$WRAPPER_DIR/"

echo "Gradle wrapper JAR regenerated successfully!"
ls -lh "$WRAPPER_DIR/gradle-wrapper.jar"
