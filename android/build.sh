#!/usr/bin/env bash
# Builds the Titan2 Keys APK (emoji keyboard + Super key service + KCM layout).
#
#   ./build.sh            build, then copy the APK to ../titan2-keys.apk
#   ./build.sh --setup    only download the toolchain
#   ./build.sh --install  build, then `adb install -r` to a connected phone
#
# Uses JAVA_HOME / ANDROID_HOME if they are set. Otherwise it downloads a JDK 17
# and the Android SDK into android/.toolchain (no root needed; delete the folder
# to remove them). The first run asks you to accept the Android SDK license.
set -euo pipefail
cd "$(dirname "$0")"

# The JDK and SDK tools downloaded below are x86-64 Linux builds (Google ships no
# ARM ones), so e.g. Termux on the phone can't run them.
if [[ "$(uname -m)" != x86_64 && ( -z "${JAVA_HOME:-}" || -z "${ANDROID_HOME:-}" ) ]]; then
  echo "error: this needs an x86-64 Linux PC; Google's Android SDK tools don't run on $(uname -m)." >&2
  echo "Build on your computer, then copy titan2-keys.apk to the phone (or use --install over adb)." >&2
  exit 1
fi

TC="$PWD/.toolchain"
JDK_URL='https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse'
CLT_URL='https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip'
GRADLE_VERSION='8.11.1'
SDK_PACKAGES=("platforms;android-35" "build-tools;34.0.0" "platform-tools")

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ ! -x "$TC/jdk/bin/javac" ]]; then
    echo ">> downloading JDK 17"
    mkdir -p "$TC/jdk"
    curl -fsSL "$JDK_URL" | tar -xz -C "$TC/jdk" --strip-components=1
  fi
  export JAVA_HOME="$TC/jdk"
fi
export PATH="$JAVA_HOME/bin:$PATH"

export ANDROID_HOME="${ANDROID_HOME:-$TC/sdk}"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -d "$ANDROID_HOME/platforms/android-35" ]]; then
  if [[ ! -x "$SDKMANAGER" ]]; then
    echo ">> downloading Android command-line tools"
    tmp="$(mktemp -d)"
    curl -fsSL -o "$tmp/clt.zip" "$CLT_URL"
    unzip -q "$tmp/clt.zip" -d "$tmp"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm -rf "$tmp"
  fi
  if [[ ! -f "$ANDROID_HOME/licenses/android-sdk-license" ]]; then
    echo ">> The Android SDK asks you to review and accept its license (one time)."
    "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses
  fi
  echo ">> installing SDK packages: ${SDK_PACKAGES[*]}"
  "$SDKMANAGER" --sdk_root="$ANDROID_HOME" "${SDK_PACKAGES[@]}"
fi
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# Keep Gradle's caches with the toolchain instead of ~/.gradle.
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$TC/gradle-home}"

if [[ ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
  echo ">> generating Gradle wrapper"
  if [[ ! -x "$TC/gradle-$GRADLE_VERSION/bin/gradle" ]]; then
    tmp="$(mktemp -d)"
    curl -fsSL -o "$tmp/gradle.zip" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    unzip -q "$tmp/gradle.zip" -d "$TC"
    rm -rf "$tmp"
  fi
  # Generate in an empty dir so Gradle doesn't need to configure this build.
  tmp="$(mktemp -d)"
  touch "$tmp/settings.gradle.kts"
  (cd "$tmp" && "$TC/gradle-$GRADLE_VERSION/bin/gradle" wrapper --gradle-version "$GRADLE_VERSION" --no-daemon -q)
  cp -r "$tmp/gradlew" "$tmp/gradlew.bat" "$tmp/gradle" .
  rm -rf "$tmp"
fi

[[ "${1:-}" == "--setup" ]] && { echo ">> toolchain ready in $TC"; exit 0; }

./gradlew --no-daemon -q assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ../titan2-keys.apk
echo ">> built $(cd .. && pwd)/titan2-keys.apk"

if [[ "${1:-}" == "--install" ]]; then
  adb install -r ../titan2-keys.apk
fi
