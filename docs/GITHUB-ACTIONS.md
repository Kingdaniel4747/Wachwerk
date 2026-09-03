# APK auf GitHub bauen lassen

Du änderst den Quellcode und lädst deine Änderungen mit GitHub Desktop hoch. GitHub Actions baut daraus die APK. Android Studio ist dafür auf deinem Rechner nicht nötig.

Die App bleibt offline. Nur der **Build** läuft auf GitHub; App-Daten werden dadurch nicht synchronisiert.

## 1. Workflow hochladen und starten

1. Die geänderten Projektdateien in GitHub Desktop als Commit speichern und **Push origin** drücken. Auch `.github/workflows/android-apk.yml` muss mit hochgeladen werden.
2. Dein Repository auf GitHub öffnen und oben **Actions** auswählen.
3. Links **Android APK bauen** öffnen. Wenn GitHub Actions noch deaktiviert ist, zunächst für dieses Repository aktivieren.
4. Nach Code-Änderungen auf `main` oder `master` startet automatisch ein `debug`-Build. Pull Requests werden ebenfalls geprüft. Reine Dokumentationsänderungen lösen keinen Build aus.
5. Alternativ **Run workflow** anklicken, den Branch und zunächst `debug` wählen und starten. Der Workflow muss dafür auf dem Standardbranch vorhanden sein.
6. Den erfolgreichen Lauf öffnen. Unter **Artifacts** das Paket `Wachwerk-debug-…` herunterladen oder den Download-Link in der Zusammenfassung verwenden.
7. ZIP entpacken. Darin liegen `Wachwerk-debug.apk`, `SHA256SUMS.txt` und `BUILD-INFO.txt` mit der zugehörigen Commit-ID.

Die APK-Downloads werden für 30 Tage, Android-Prüfberichte für 14 Tage angefordert; eine strengere Repository-/Organisationsrichtlinie kann das begrenzen. Lade wichtige Ergebnisse rechtzeitig herunter. Der Workflow erstellt **keinen öffentlichen Release** und pusht keine Dateien zurück ins Repository.

Zum Herunterladen von Actions-Artefakten musst du bei GitHub angemeldet sein und Zugriff auf das Repository haben. Für öffentliche APK-Downloads ohne GitHub-Anmeldung kannst du eine Release-APK später manuell als Release-Asset veröffentlichen. [GitHub erklärt den Artefakt-Download hier](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts).

## 2. Test-APK oder Update für dein Handy?

| Variante | Einrichtung | Verwendung |
| --- | --- | --- |
| `debug` | Keine Secrets nötig | Testinstallation mit automatisch erzeugtem Debug-Schlüssel. Nicht als Update der bisher signierten Release-App geeignet. |
| `release` | Vier private GitHub-Secrets | APK mit deinem hinterlegten Schlüssel; mit dem bisherigen Schlüssel für Updates geeignet. |

**Wichtig:** Der temporäre GitHub-Rechner kann bei jedem Lauf einen anderen Debug-Schlüssel erzeugen. Debug-APKs sind deshalb auch untereinander nicht zuverlässig als Update installierbar. Die bestehende Wachwerk-App mit ihren Daten nicht einfach deinstallieren. Für dein regelmäßig benutztes Handy die Release-Variante mit dem bisherigen Schlüssel verwenden; für Debug einen Emulator oder ein separates Testgerät ohne vorhandene Installation nutzen.

## 3. Signierte Release-APK einmalig einrichten

Im Repository **Settings → Secrets and variables → Actions → New repository secret** öffnen. Folgende vier Secrets anlegen:

| Secret | Inhalt |
| --- | --- |
| `WACHWERK_KEYSTORE_BASE64` | Der Inhalt deiner bisherigen Keystore-Datei, als Base64 codiert |
| `WACHWERK_STORE_PASSWORD` | Passwort dieser Keystore-Datei |
| `WACHWERK_KEY_ALIAS` | Alias des bisherigen Schlüssels; beim ursprünglichen Wachwerk-Schlüssel `wachwerk` |
| `WACHWERK_KEY_PASSWORD` | Passwort des Schlüssels |

Den **bisherigen** privaten Signaturschlüssel benutzen, nicht einen neuen erzeugen. Er ist absichtlich nicht Teil des Repositorys. Lade weder die Schlüsseldatei noch ihr Backup in Git, Issues oder Releases hoch.

So kannst du die Base64-Fassung lokal mit PowerShell direkt in die Zwischenablage kopieren, ohne sie in einer Repository-Datei zu speichern. Den Beispielpfad durch den echten Pfad zu deiner Keystore-Datei ersetzen:

```powershell
$keystorePath = 'C:\Privat\wachwerk-release.jks'
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath)) | Set-Clipboard
```

Den Zwischenablage-Inhalt ausschließlich als Wert von `WACHWERK_KEYSTORE_BASE64` in GitHub einfügen. Danach die Zwischenablage und gegebenenfalls deren Verlauf leeren. Base64 ist **keine Verschlüsselung**: Behandle den Inhalt wie die private Schlüsseldatei. Speichere ihn nicht bei den normalen „Variables“, sondern unter **Secrets**. GitHub beschreibt die Verwaltung verschlüsselter [Actions-Secrets hier](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).

Anschließend:

1. **Actions → Android APK bauen → Run workflow**.
2. Den Standardbranch, normalerweise `main`, und `release` auswählen.
3. Nach erfolgreichem Lauf das Paket `Wachwerk-release-…` herunterladen und entpacken.
4. `Wachwerk-release.apk` auf dem Handy als Update installieren oder als Asset eines GitHub-Releases anhängen.

Wenn ein Secret fehlt oder die Signierung scheitert, schlägt der Lauf fehl. Es wird **nicht** stillschweigend eine unpassende Test- oder unsignierte APK als Release ausgegeben. Release-Builds sind nur manuell vom Standardbranch erlaubt. Verwende dafür ausschließlich geprüften Code; wer diesen Code oder den Workflow ändern kann, könnte sonst beim Build auf die freigegebenen Secrets zugreifen. Branch-Schutz und sorgfältige Prüfung von Änderungen sind deshalb sinnvoll.

Für neue Versionen `versionCode` und `versionName` in `wachwerk-android/app/build.gradle` anpassen. Der Versionscode muss für eine neue Update-Version steigen. Die sichtbare Version zusätzlich in `wachwerk-local-web/package.json` und den Release-Hinweisen nachziehen.

## Was der Workflow macht

1. Node.js 24, die in `package.json` festgelegte pnpm-Version, Java 17 und Android SDK 35 einrichten.
2. Web-Abhängigkeiten exakt aus dem Lockfile installieren, TypeScript prüfen und Web-Tests ausführen.
3. Die React-Oberfläche frisch bauen und über `inline.mjs` in die Android-Assets übernehmen. Änderungen an der Oberfläche landen dadurch wirklich in der neuen APK.
4. Den Gradle-Wrapper prüfen, Android-Unit-Tests und Release-Lint ausführen.
5. Die gewünschte APK bauen, ihre Signatur mit `apksigner` prüfen und die SHA-256-Prüfsumme erzeugen.
6. APK und Android-Prüfberichte als getrennte Downloads bereitstellen.

Der Release-Schlüssel wird nur für den Signierschritt in einer temporären Datei angelegt und danach entfernt. Er ist nicht Bestandteil der hochgeladenen Artefakte oder des Gradle-Caches. Externe Actions sind auf feste Commit-IDs festgelegt. Es gibt keine automatischen Release-Veröffentlichungen, Build-Scans oder Repository-Schreibrechte.

Der erste Lauf braucht wegen der Downloads gewöhnlich länger. Bei einem roten Lauf zuerst den fehlgeschlagenen Schritt und dessen Fehlermeldung öffnen. Ein erfolgreicher Build ersetzt keinen echten Handytest von NFC, Alarmton, Display-Sperre und Energiesparen.

[Zurück zur README](../README.md)
