#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
# Da eseguire UNA SOLA VOLTA per creare la firma dell'app.
# Usa openssl invece di keytool per evitare il crash
# "iawareperf/UniPerf" presente su alcuni telefoni Huawei/Honor.
# ============================================================
set -e

pkg install -y openssl-tool 2>/dev/null || pkg install -y openssl

mkdir -p ~/keystore
KEYSTORE_PATH="$HOME/keystore/ritagliacolora.keystore"

if [ -f "$KEYSTORE_PATH" ]; then
  echo "Esiste già una firma in $KEYSTORE_PATH"
  echo "Se la cancelli e ne crei una nuova, gli aggiornamenti futuri NON saranno più riconosciuti come tali."
  exit 1
fi

echo "=== Creazione della firma (con openssl) ==="
echo -n "Scegli una password per la firma (la userai sempre, ricordala): "
read -s PASSWORD
echo

TMP_DIR=$(mktemp -d)
openssl req -x509 -newkey rsa:2048 \
  -keyout "$TMP_DIR/key.pem" \
  -out "$TMP_DIR/cert.pem" \
  -days 10000 -nodes \
  -subj "/CN=ritagliacolora/O=RitagliaColora/C=IT"

openssl pkcs12 -export \
  -in "$TMP_DIR/cert.pem" \
  -inkey "$TMP_DIR/key.pem" \
  -out "$KEYSTORE_PATH" \
  -name ritagliacolora \
  -password "pass:$PASSWORD"

rm -rf "$TMP_DIR"

echo
echo "=== Fatto ==="
echo "Firma creata in: $KEYSTORE_PATH"
echo "NON perdere questo file e la password: senza, non potrai più aggiornare l'app in futuro."
echo
echo "Ora crea il file keystore.properties dentro ~/ritagliacolora con questo contenuto:"
echo
echo "storeFile=$KEYSTORE_PATH"
echo "storePassword=$PASSWORD"
echo "keyAlias=ritagliacolora"
echo "keyPassword=$PASSWORD"
