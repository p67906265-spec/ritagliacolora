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
