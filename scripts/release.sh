#!/usr/bin/env bash
set -e
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_SDK_ROOT="$HOME/android-sdk"
cd "$(dirname "$0")/.."

VC=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
VN=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
NOTES="${1:-Обновление}"

# Web root of k73.online/newBMW/. Since the 2026-07-25 move to amsterbase these are plain
# files served by the system nginx — no more `docker cp` into the NPM container.
OTA_DIR="${OTA_DIR:-$HOME/k73-web/newBMW}"

./gradlew :app:assembleRelease --no-daemon --console=plain
APK=app/build/outputs/apk/release/app-release.apk

install -m 644 "$APK" "$OTA_DIR/bmw-launcher.apk"
cat > "$OTA_DIR/latest.json" <<JSON
{"versionCode":$VC,"versionName":"$VN","apkUrl":"https://k73.online/newBMW/bmw-launcher.apk","notes":"$NOTES"}
JSON
chmod 644 "$OTA_DIR/latest.json"
echo "Published versionCode=$VC versionName=$VN"
curl -s https://k73.online/newBMW/latest.json
