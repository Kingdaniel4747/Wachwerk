# Repository & APK auf GitHub veröffentlichen

Diese Anleitung beschreibt den manuellen Upload. Das vorbereitete Paket nimmt keine Veröffentlichung und keine Kontoänderung vor.

## Repository anlegen

1. Auf GitHub **New repository** wählen.
2. Einen Namen, etwa `wachwerk`, und die gewünschte Sichtbarkeit festlegen.
3. Nicht automatisch eine README, Gitignore oder Lizenz hinzufügen lassen, wenn du dieses vorbereitete Projekt importierst.
4. Die Projektdateien hochladen, sodass `README.md` sowie die beiden Projektordner direkt im Repository liegen. Die Ordnerstruktur beibehalten, nicht nur ein ZIP in den Code-Bereich hochladen.
5. Auch `.gitignore` und `.gitattributes` mitnehmen. Bei einem Upload-Limit die Dateien in mehreren Schritten hinzufügen oder GitHub Desktop verwenden.

Quelle: [GitHub – Repository erstellen](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository).

## Release 1.13.0 anlegen

Öffne auf der Repository-Seite **Releases**, erstelle einen neuen Release-Entwurf und lege den Tag `v1.13.0` auf dem hochgeladenen Quellcode-Stand an.

Titel: **Wachwerk 1.13.0**

Im mitgelieferten Paket liegt neben dem Repository ein Ordner `Release-Dateien/v1.13.0`. Er enthält APK, Quellcode-ZIP, Drittanbieter-Lizenzen, Prüfsummen und einen fertigen `RELEASE-TEXT.md`.

- Den Release-Text in das Beschreibungsfeld kopieren.
- Die APK, beide ZIP-Dateien und `SHA256SUMS.txt` als Assets hinzufügen.
- `RELEASE-TEXT.md` und diese Anleitung müssen nicht selbst als Download angehängt werden.
- Bis zur Prüfung auf dem echten Handy als **Pre-release** kennzeichnen.
- Erst nach Kontrolle veröffentlichen; alternativ als Entwurf speichern.

Quelle: [GitHub – Releases erstellen und Dateien anhängen](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).

## Wichtig: Code ist nicht Release

Die APK gehört zu den Release-Downloads, nicht dauerhaft in den Git-Verlauf. `.gitignore` schützt beim Arbeiten mit Git vor typischen Build-Dateien und privaten Schlüsseln. Beim manuellen Browser-Upload musst du die Dateiauswahl trotzdem selbst kontrollieren.

Die bereits gebündelte Offline-Oberfläche und das Gradle-Wrapper-JAR gehören dagegen absichtlich zum Quellcodepaket. So lässt sich die App ohne vorherigen Web-Build öffnen und bauen.

## Vor „Public“ prüfen

- Keine Schlüsseldatei, kein Signatur-Backup und kein Passwort hochgeladen.
- Keine persönlichen QR-/NFC-Kennungen oder Nutzer-Backups enthalten.
- README-Bilder werden angezeigt.
- License-Entscheidung bewusst getroffen; siehe [Lizenzstatus](../LICENSE-STATUS.md).
- APK und Tag stammen von derselben Version.
- Einschränkungen und Geräte-Teststand bleiben im Release-Text erhalten.

[Zurück zur README](../README.md)
