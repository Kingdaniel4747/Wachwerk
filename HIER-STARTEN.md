# Dein Wachwerk-Paket für GitHub

Alles ist vorbereitet. Es wurde noch nichts auf GitHub veröffentlicht.

## Was kommt wohin?

- **Repository/** → diesen Ordnerinhalt als GitHub-Repository hochladen.
- **Release-Dateien/v1.13.0/** → die APK und Downloads später bei einem GitHub-Release anhängen.
- Diese Datei ist deine persönliche Startanleitung und muss nicht ins Repository.

**Nicht den gesamten Wachwerk-GitHub-Ordner als Repository hochladen.** Öffne zuerst `Repository`. Dort sollen `README.md`, `wachwerk-android` und `wachwerk-local-web` direkt auf der obersten Ebene liegen.

## So geht es ohne Terminal

1. Auf GitHub ein neues Repository erstellen, zum Beispiel `wachwerk`. Entscheide selbst zwischen öffentlich und privat. Keine zusätzliche README, Gitignore oder Lizenz generieren lassen – die Projektdateien sind bereits vorbereitet.
2. Auf der leeren Repository-Seite die vorhandenen Dateien hochladen. Alternativ später **Add file → Upload files**.
3. Den **Inhalt** des Ordners `Repository` in das Upload-Feld ziehen und den Upload mit „Commit changes“ abschließen. Unterordner beibehalten.
4. Kontrollieren, dass `.gitignore` und `.gitattributes` mit hochgeladen wurden. Falls GitHub ein Dateilimit meldet, die Ordner in mehreren Uploads hinzufügen.
5. Danach rechts **Releases → Create a new release / Draft a new release** öffnen.
6. Als Tag `v1.13.0`, als Ziel den hochgeladenen Stand und als Titel `Wachwerk 1.13.0` verwenden.
7. Den Text aus `Release-Dateien/v1.13.0/RELEASE-TEXT.md` in das Beschreibungsfeld kopieren.
8. Diese Dateien als Release-Assets anhängen:
   - `Wachwerk-v1.13.0-Offline-signiert.apk`
   - `Wachwerk-Quellcode-v1.13.0.zip`
   - `Wachwerk-Lizenzen-v1.13.0.zip`
   - `SHA256SUMS.txt`
9. Solange der reale Handytest noch fehlt, **This is a pre-release** markieren. Zunächst als Entwurf speichern oder nach deiner Prüfung veröffentlichen.

GitHub beschreibt [neue Repositorys](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository) und [Releases mit Dateianhängen](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository) auch direkt in seiner Hilfe.

## Noch zwei wichtige Punkte

**Lizenz:** Ich habe keine MIT- oder andere Open-Source-Lizenz für deinen eigenen Code festgelegt. Wenn andere ihn ausdrücklich weiterverwenden dürfen sollen, wähle vor der Veröffentlichung eine passende Lizenz und ergänze sie als `LICENSE`. In README und `LICENSE-STATUS.md` ist der aktuelle Stand klar benannt. Bestehende Drittanbieter-Lizenzen sind enthalten.

**Geheimnisse:** Die privaten Wachwerk-Signaturschlüssel, deren Backup, Passwörter, lokale Rechnerpfade und Abhängigkeitsordner sind nicht im Repository-Paket. Deinen bisherigen Signaturschlüssel privat behalten – er wird für künftige kompatible App-Updates gebraucht. Die vorhandene APK ist unverändert und bereits signiert.

## Wenn du lieber GitHub Desktop nutzt

Erstelle dort ein neues lokales Repository an einem freien Speicherort. Kopiere den Inhalt von `Repository` hinein, kontrolliere die Dateiliste, erstelle den ersten Commit und veröffentliche es anschließend über GitHub Desktop. Die APK weiterhin nur als Release-Asset hochladen.

Mehr Details findest du in [der vollständigen Veröffentlichungshilfe](Repository/docs/GITHUB-VEROEFFENTLICHEN.md).
