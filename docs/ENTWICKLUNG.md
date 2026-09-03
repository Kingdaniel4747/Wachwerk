# Wachwerk selbst bauen

Du möchtest nur den Code bearbeiten und den APK-Build GitHub überlassen? Nutze den vorbereiteten Workflow: [APK mit GitHub Actions bauen](GITHUB-ACTIONS.md). Die folgenden Schritte sind für lokale Builds gedacht.

## Architektur

Die Oberfläche ist eine lokale React-/TypeScript-Anwendung in einer Android-WebView. Sie liegt vollständig gebündelt in `wachwerk-android/app/src/main/assets/site/index.html`. Über die lokale JavaScript-Brücke `WachwerkAndroid` spricht sie mit den nativen Java-Komponenten.

Wichtige Bausteine:

- `AlarmScheduler`, `AlarmReceiver`, `AlarmRingingService`: Planung, Auslösung und vom Bildschirm unabhängiger Alarmton.
- `RingingLedger`, `AlarmSessionStore`: lokal gespeicherte Alarmereignisse.
- `AlarmActivity`: Aufwachaufgaben und NFC-Leser.
- `UsageTimeline`, `DailyUsageStore`: begrenzte Auswertung der täglichen Android-Nutzung.
- `AppBlockAccessibilityService`, `BlockPolicy`, `MorningBlockStore`: App-Erkennung und voneinander unabhängige Sperrregeln.
- `App.tsx`, `workflow.ts`: Oberfläche und Web-Logik.
- `palettes.css`, `UiPalette.java`: Farbpaletten in Web-Oberfläche und nativen Ansichten.

## Voraussetzungen

- Android Studio oder eine passende Android-Kommandozeilenumgebung
- JDK 17
- Android SDK Platform 35 und Build Tools 35.0.0
- Gradle 8.13 über den enthaltenen Wrapper
- Für Änderungen an der Oberfläche: Node.js 24 und pnpm 11.19.0 (in `package.json` festgelegt); der Lockfile ist enthalten

Werkzeuge und Abhängigkeiten benötigen beim ersten Download Internet. Die fertige App benötigt keinen eigenen Server.

## Android bauen

In Android Studio **nur den Ordner `wachwerk-android`** als Projekt öffnen, SDK und JDK einstellen, dann Gradle synchronisieren.

Windows PowerShell:

```powershell
cd wachwerk-android
.\gradlew.bat testDebugUnitTest assembleDebug
```

macOS/Linux:

```sh
cd wachwerk-android
sh gradlew testDebugUnitTest assembleDebug
```

Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`.

Die Datei `local.properties` wird von Android Studio lokal erzeugt und gehört nicht in Git. Bei Kommandozeilen-Builds muss der SDK-Pfad lokal beziehungsweise über `ANDROID_HOME` bekannt sein.

## Oberfläche bearbeiten

Die Ordner `wachwerk-local-web` und `wachwerk-android` müssen nebeneinander bleiben.

```sh
cd wachwerk-local-web
pnpm install --frozen-lockfile
pnpm exec tsc --noEmit
pnpm test
pnpm exec vite --host 127.0.0.1
```

Die Vorschau läuft lokal. NFC, Android-Sperren und echte Wecker funktionieren nur in der installierten Android-App, nicht im normalen Desktop-Browser.

Nach Änderungen:

```sh
pnpm run build
```

Dieses Kommando baut die Oberfläche und führt `inline.mjs` aus. Das Skript schreibt die komplette Offline-HTML in das benachbarte Android-Projekt. **Danach die APK erneut bauen**, sonst enthält sie noch die vorherige Oberfläche.

## Signierte Release-APK

Zum Weiterführen der bestehenden App-Installation ist der bisherige private Signaturschlüssel notwendig. Er ist absichtlich nicht in diesem Repository enthalten.

Der Android-Build liest ausschließlich folgende Umgebungsvariablen:

| Variable | Bedeutung |
| --- | --- |
| `WACHWERK_KEYSTORE` | Absoluter Pfad zur privaten Keystore-Datei |
| `WACHWERK_STORE_PASSWORD` | Keystore-Passwort |
| `WACHWERK_KEY_ALIAS` | Alias des Signaturschlüssels |
| `WACHWERK_KEY_PASSWORD` | Passwort des Signaturschlüssels |

Diese Werte privat setzen, nicht in Dateien im Repository schreiben. Anschließend im Android-Ordner:

```powershell
.\gradlew.bat assembleRelease
```

Ergebnis: `app/build/outputs/apk/release/app-release.apk`.

Ein eigener neuer Schlüssel ist für ein unabhängiges Build möglich, ersetzt aber keine bereits installierte anders signierte APK. **Für Signaturtests niemals ungeprüft die bestehende App mit ihren Daten deinstallieren.**

## Prüfungen

```powershell
# Im Android-Projekt
.\gradlew.bat testDebugUnitTest lintRelease
```

```sh
# Im Web-Projekt
pnpm exec tsc --noEmit
pnpm test
```

Stand 1.13.0: 25 Android-Unit-Tests, 5 Web-Logiktests, erfolgreiche Kompilierung und keine Lint-Fehler; bestehende Lint-Warnungen bleiben. Die Tests prüfen Logik, nicht reales NFC oder das Verhalten jedes Herstellers. Dafür ist der [Gerätetest](INSTALLATION.md#4-vor-der-ersten-nacht-testen) erforderlich.

## Veröffentlichung

Versionsnummer und Versionscode stehen in `wachwerk-android/app/build.gradle`; die Web-Paketversion in `wachwerk-local-web/package.json`. Für ein Android-Update muss der Versionscode steigen.

APK als Release-Asset veröffentlichen, nicht im Git-Verlauf ablegen. [GitHub- und Release-Anleitung](GITHUB-VEROEFFENTLICHEN.md).

[Zurück zur README](../README.md)
