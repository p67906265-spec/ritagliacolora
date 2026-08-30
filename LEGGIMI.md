# Ritaglia e Colora

App Android semplice: carica un'immagine, ritagliala o coloraci sopra a mano libera ("a matita").

## Cosa fa
- **Carica**: apre la galleria e seleziona un'immagine
- **Ritaglia**: trascina per disegnare un rettangolo, poi "Conferma ritaglio"
- **Matita**: scegli colore e spessore, disegna col dito sull'immagine, poi "Applica colore"
- **Salva**: salva il risultato in Galleria > Pictures/RitagliaColora

## Come compilare l'APK con Termux

1. Copia questa cartella (`ImageEditorApp`) sul telefono, dentro Termux (es. `~/ImageEditorApp`)
2. Apri Termux ed esegui:
   ```
   cd ~/ImageEditorApp
   bash build_termux.sh
   ```
3. La prima volta scaricherà JDK e Android SDK (serve connessione internet, richiede qualche minuto)
4. Al termine trovi l'APK in `app/build/outputs/apk/debug/app-debug.apk`
5. Per copiarlo nella cartella Download del telefono:
   ```
   cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/
   ```
6. Installa l'APK da un file manager (serve consentire "sorgenti sconosciute" per Termux/il file manager usato)

## Firma dell'app (per aggiornare senza reinstallare)

**Perché serve**: Android permette di installare un aggiornamento sopra la versione esistente
solo se ha la stessa firma. Senza una firma stabile, ogni build ne genera una diversa (o casuale)
e devi disinstallare/reinstallare ogni volta.

### 1) Crea la firma (una volta sola)
In Termux:
```
cd ~/ritagliacolora
bash genera_firma.sh
```
Ti chiederà una password (scrivila da qualche parte al sicuro: senza non potrai più
pubblicare aggiornamenti) e alcuni dati anagrafici (puoi anche inventarli).

Alla fine ti mostra un blocco di testo: crea il file `keystore.properties` (nella cartella
`~/ritagliacolora`, quindi FUORI da git — è già escluso da `.gitignore`) incollandoci dentro
quel testo, sostituendo `LA_TUA_PASSWORD` con la password che hai scelto.

### 2) Build firmata in locale (Termux)
Da questo momento, `bash build_termux.sh` produce automaticamente un **APK release firmato**
in `app/build/outputs/apk/release/app-release.apk` invece della versione debug.

### 3) Build firmata su GitHub Actions (opzionale)
Se vuoi che anche le build automatiche online siano firmate, vai su GitHub:
`Settings > Secrets and variables > Actions > New repository secret` e crea questi 4 secret:
- `KEYSTORE_BASE64` → contenuto del file keystore convertito in base64: in Termux esegui
  `base64 -w0 ~/keystore/ritagliacolora.keystore` e copia l'output
- `KEYSTORE_PASSWORD` → la password scelta
- `KEY_ALIAS` → `ritagliacolora`
- `KEY_PASSWORD` → la password scelta (stessa di KEYSTORE_PASSWORD se non ne hai messa una diversa)

Senza questi secret, GitHub Actions continua a compilare la versione debug come prima
(nessun problema, funziona comunque).

## Struttura del progetto (per GitHub)
```
ImageEditorApp/
├── build.gradle.kts
├── settings.gradle.kts
├── build_termux.sh
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/it/paolo/ritagliacolora/MainActivity.kt
│       └── res/values/strings.xml
```

Puoi creare la repo su GitHub e caricare questa cartella così com'è (aggiungi un `.gitignore` per Android se vuoi escludere `build/` e `.gradle/`).
