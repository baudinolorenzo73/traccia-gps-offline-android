#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
: "${ANDROID_SDK_ROOT:?Imposta ANDROID_SDK_ROOT sul percorso del tuo Android SDK}"
build_tools="$ANDROID_SDK_ROOT/build-tools/35.0.0"
platform_jar="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
output_dir="$project_root/build/manual"
mkdir -p "$output_dir/classes" "$output_dir/dex" "$project_root/.signing"
"$build_tools/aapt2" compile --dir "$project_root/app/src/main/res" -o "$output_dir/resources.zip"
"$build_tools/aapt2" link -o "$output_dir/base.apk" -I "$platform_jar" --manifest "$project_root/app/src/main/AndroidManifest.xml" --java "$output_dir/generated" --min-sdk-version 26 --target-sdk-version 35 "$output_dir/resources.zip"
find "$project_root/app/src/main/java" "$output_dir/generated" -name '*.java' > "$output_dir/sources.txt"
javac -encoding UTF-8 --release 17 -classpath "$platform_jar" -d "$output_dir/classes" @"$output_dir/sources.txt"
jar cf "$output_dir/classes.jar" -C "$output_dir/classes" .
"$build_tools/d8" --lib "$platform_jar" --min-api 26 --output "$output_dir/dex" "$output_dir/classes.jar"
cp "$output_dir/base.apk" "$output_dir/unsigned.apk"
(cd "$output_dir/dex" && zip -q "$output_dir/unsigned.apk" classes*.dex)
"$build_tools/zipalign" -f -p 4 "$output_dir/unsigned.apk" "$output_dir/aligned.apk"
# Firma di prova da conservare per aggiornare la beta senza perdere i dati.
# NON usare questa chiave come chiave di pubblicazione su uno store.
if [[ ! -f "$project_root/.signing/beta.keystore" ]]; then
  keytool -genkeypair -keystore "$project_root/.signing/beta.keystore" -storepass android -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
fi
"$build_tools/apksigner" sign --ks "$project_root/.signing/beta.keystore" --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out "$project_root/Traccia-GPS-Offline-beta.apk" "$output_dir/aligned.apk"
"$build_tools/apksigner" verify --verbose "$project_root/Traccia-GPS-Offline-beta.apk"
