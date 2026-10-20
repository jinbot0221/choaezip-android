#!/usr/bin/env bash
# Source this file in each cloud shell; no credentials are stored here.
export JAVA_HOME=/workspace/toolchains/java
export ANDROID_HOME=/workspace/toolchains/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME=/workspace/.android
export GRADLE_USER_HOME=/workspace/.gradle
export FIREBASE_EMULATORS_PATH=/workspace/.cache/firebase/emulators
export XDG_CONFIG_HOME=/workspace/.config
export npm_config_cache=/workspace/.npm
export PATH="/workspace/toolchains/node-v22.16.0-linux-x64/bin:$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
