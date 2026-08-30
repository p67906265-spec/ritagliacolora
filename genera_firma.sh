#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
# Da eseguire UNA SOLA VOLTA per creare la firma dell'app.
# Dopo questo, ogni build (Termux o GitHub Actions) potrà
# usare la stessa firma per gli aggiornamenti.
# ============================================================
set -e

mkdir -p ~/keystore
KEYSTORE_PATH="$HOME/keystore/ritagliacolora.keystore"

if [ -f "$KEYSTORE_PATH" ]; then
  echo "Esiste già una firma in $KEYSTORE_PATH"
  echo "Se la cancelli e ne crei una nuova, gli aggiornamenti futuri NON saranno più riconosciuti come tali."
  exit 1
fi

echo "=== Creazione della firma ==="
echo "Ti verranno chieste una password (usala sempre, ricordala) e alcuni dati (nome, ecc: puoi anche inventarli)"
keytool -genkeypair -v \
  -keystore "$KEYSTORE_PATH" \
  -alias ritagliacolora \
  -keyalg RSA -keysize 2048 -validity 10000

echo
echo "=== Fatto ==="
echo "Firma creata in: $KEYSTORE_PATH"
echo "NON perdere questo file e la password: senza, non potrai più aggiornare l'app in futuro."
echo
echo "Ora crea il file keystore.properties dentro ~/ritagliacolora con questo contenuto"
echo "(sostituisci LE_TUE_PASSWORD con quelle che hai appena scelto):"
echo
echo "storeFile=$KEYSTORE_PATH"
echo "storePassword=LA_TUA_PASSWORD"
echo "keyAlias=ritagliacolora"
echo "keyPassword=LA_TUA_PASSWORD"
