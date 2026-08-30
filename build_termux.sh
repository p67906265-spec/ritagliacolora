#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
# Script per compilare RitagliaColora.apk direttamente in Termux
# Eseguire UNA SOLA VOLTA i passi 1-3, poi per ogni build
# basta rilanciare questo script (o solo il passo 5).
# ============================================================
set -e

echo "=== 1) Aggiornamento pacchetti e installazione strumenti base ==="
pkg update -y && pkg upgrade -y
pkg install -y git openjdk-17 wget unzip

echo "=== 2) Storage: consenti l'accesso se richiesto (termux-setup-storage) ==="
termux-setup-storage || true

echo "=== 3) Download Android SDK command line tools (se non già presenti) ==="
export ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"

if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
  cd "$ANDROID_HOME/cmdline-tools"
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdtools.zip
  unzip -q cmdtools.zip
  rm cmdtools.zip
  mv cmdline-tools latest
fi

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "=== 4) Installazione componenti SDK (accetta licenze automaticamente) ==="
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses > /dev/null || true
sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-34" "build-tools;34.0.0"

echo "=== 5) Compilazione APK ==="
cd "$(dirname "$0")"
echo "sdk.dir=$ANDROID_HOME" > local.properties

chmod +x ./gradlew 2>/dev/null || true
if [ ! -f "./gradlew" ]; then
  echo "gradlew non trovato: uso gradle installato via pkg (fallback)"
  pkg install -y gradle
  gradle wrapper
fi

./gradlew assembleDebug

echo "=== FATTO ==="
echo "APK generato in: app/build/outputs/apk/debug/app-debug.apk"
echo "Copialo con: cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/"
