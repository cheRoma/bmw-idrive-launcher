#!/usr/bin/env bash
set -e
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_SDK_ROOT="$HOME/android-sdk"
cd "$(dirname "$0")/.."

VC=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
VN=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
NOTES="${1:-Обновление}"

./gradlew :app:assembleRelease --no-daemon --console=plain
APK=app/build/outputs/apk/release/app-release.apk

sudo docker cp "$APK" npm-app-1:/data/newBMW/bmw-launcher.apk
cat > /tmp/latest.json <<JSON
{"versionCode":$VC,"versionName":"$VN","apkUrl":"https://k73.online/newBMW/bmw-launcher.apk","notes":"$NOTES"}
JSON
sudo docker cp /tmp/latest.json npm-app-1:/data/newBMW/latest.json
sudo docker exec npm-app-1 sh -c 'chmod 644 /data/newBMW/bmw-launcher.apk /data/newBMW/latest.json'
echo "Published versionCode=$VC versionName=$VN"
curl -sk --resolve k73.online:443:127.0.0.1 https://k73.online/newBMW/latest.json
