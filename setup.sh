#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  ScrollBlocker — Build Script
#  Requires: Java 17+, Android SDK with platform-34 and build-tools;34.0.0
# ─────────────────────────────────────────────────────────────────────────────
set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

echo -e "${CYAN}  ====== ScrollBlocker Build ======${NC}"

# ── Step 1: Java ──────────────────────────────────────────────────────────────
echo -e "${YELLOW}[1/4] Checking Java 17+...${NC}"
if ! command -v java &>/dev/null; then
    echo -e "${RED}✗ Java not found. Install JDK 17:${NC}"
    echo "  Ubuntu/Debian: sudo apt install openjdk-17-jdk"
    echo "  macOS:         brew install openjdk@17"
    exit 1
fi
echo -e "${GREEN}✓ Java found: $(java -version 2>&1 | head -1)${NC}"

# ── Step 2: Android SDK ───────────────────────────────────────────────────────
echo -e "${YELLOW}[2/4] Checking Android SDK...${NC}"
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    for TRY in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/android-sdk"; do
        [ -d "$TRY" ] && export ANDROID_HOME="$TRY" && break
    done
fi
if [ -z "$ANDROID_HOME" ]; then
    echo -e "${RED}✗ Android SDK not found. Set ANDROID_HOME or install it:${NC}"
    echo "  1. Download: https://developer.android.com/studio#command-tools"
    echo "  2. Extract to: ~/android-sdk/cmdline-tools/latest/"
    echo "  3. Run: sdkmanager 'platforms;android-34' 'build-tools;34.0.0'"
    echo "  4. export ANDROID_HOME=~/android-sdk && re-run this script"
    exit 1
fi
echo -e "${GREEN}✓ ANDROID_HOME=$ANDROID_HOME${NC}"

# ── Step 3: Gradle wrapper ────────────────────────────────────────────────────
echo -e "${YELLOW}[3/4] Setting up Gradle wrapper...${NC}"
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar"
EXPECTED_MIN_SIZE=50000  # real jar is ~60KB

get_size() { stat -c%s "$1" 2>/dev/null || stat -f%z "$1" 2>/dev/null || echo 0; }

if [ ! -f "$WRAPPER_JAR" ] || [ "$(get_size $WRAPPER_JAR)" -lt "$EXPECTED_MIN_SIZE" ]; then
    echo "  Downloading gradle-wrapper.jar..."
    curl -sL "$WRAPPER_URL" -o "$WRAPPER_JAR" 2>/dev/null \
    || wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR" 2>/dev/null \
    || {
        echo -e "${YELLOW}  Network download failed. Trying system gradle...${NC}"
        if command -v gradle &>/dev/null; then
            gradle wrapper --gradle-version=8.4
        else
            echo -e "${RED}✗ Could not get gradle-wrapper.jar.${NC}"
            echo "  Install Gradle:  sudo apt install gradle  OR  brew install gradle"
            exit 1
        fi
    }
fi
chmod +x gradlew
echo -e "${GREEN}✓ Gradle wrapper ready ($(get_size $WRAPPER_JAR) bytes)${NC}"

# ── Step 4: Build ─────────────────────────────────────────────────────────────
echo -e "${YELLOW}[4/4] Building debug APK...${NC}"
./gradlew assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
    echo ""
    echo -e "${GREEN}✓ BUILD SUCCESSFUL!${NC}"
    echo -e "${CYAN}  APK → $(pwd)/$APK${NC}"
    echo ""
    echo "  Install (USB via ADB):"
    echo -e "${CYAN}    adb install $APK${NC}"
    echo ""
    echo "  Or copy APK to your phone and install manually."
else
    echo -e "${RED}✗ Build failed — APK not found${NC}"; exit 1
fi
