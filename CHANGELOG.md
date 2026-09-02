# Änderungsverlauf

## 1.13.0 · 2. September 2026

### Neu

- **Blocker → Morgen:** ausgewählte Apps nach einer erfolgreich abgeschlossenen Aufwachaufgabe für eine konfigurierbare Dauer sperren.
- Dauerhaft gespeicherte Warteschlange für ausgelöste Alarmereignisse.
- Hinweis in der App, über den die laufende Aufwachaufgabe wieder geöffnet werden kann.

### Korrigiert

- Tagesnutzung aus Android-Ereignissen seit Mitternacht statt übergroßer aggregierter Tagesfenster.
- Keine pauschale Übernahme des höheren Werts aus System- und lokalem Zähler.
- Alarmton in eigenem Vordergrunddienst; Lebenszyklus der Ansicht beendet den Alarm nicht mehr.
- NFC-Leser beim Zurückkehren neu aktivieren, aktuelle Aufwachaufgabe bei neuen Intents laden.
- Gerätesperre, fehlendes NFC und ausgeschaltetes NFC sichtbar behandeln.
- Getrennte Behandlung der Morgensperre und der bisherigen Sperrbereiche.
- Android-8.0-kompatible Aufteilung der Navigationsleisten-Theme-Einstellung.

### Prüfstand

25 Android-Unit-Tests und 5 Web-Logiktests erfolgreich; keine Lint-Fehler. Geräteprüfung für NFC, gesperrten Bildschirm und Samsung-Energiesparverhalten steht aus.

## 1.12.0

- Drei wählbare Farbpaletten: Original, Sonnenwärme und Abendruhe.
- Kompaktere Zahlenfelder mit Drehrad.
- Vergrößerte Schließgesten-Zone für die unteren Dialoge.
- Habit-Markierungen in einer Zeile.
- Getrennte Schlüssel und Aktivierungszustände für Direkt, Limits und Uhrzeiten.
- Überlappende Sperren separat freigeben.
