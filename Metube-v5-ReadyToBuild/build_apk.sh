#!/usr/bin/env bash
set -e
echo "Memulai kompilasi Metube Debug APK (ARM64)..."
chmod +x ./gradlew
./gradlew assembleDebug
echo "Build selesai! Lokasi APK: app/build/outputs/apk/debug/app-debug.apk"
