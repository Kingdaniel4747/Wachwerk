# Daten & Berechtigungen

Wachwerk ist ohne Benutzerkonto und ohne eigenen Cloud-Dienst konzipiert. In der APK ist keine `INTERNET`-Berechtigung deklariert. Die lokale Oberfläche enthält keine Werbe- oder Analyse-SDKs.

## Was wird gespeichert?

Einstellungen, Wecker, Aufgaben, Habits, Morgenchecks, importierte Alarmtöne, ausgewählte App-Pakete, NFC-/QR-Schlüssel und Sperrregeln bleiben in den lokalen App-Daten. Die Oberfläche nutzt lokalen WebView-Speicher; native Funktionen nutzen app-eigene Dateien und Android SharedPreferences.

Die App ist kein verschlüsselter Datentresor. Ein Gerätezugriff, Root-Zugriff oder Zugriff auf Backups kann Daten offenlegen.

## Was sieht der App-Blocker?

Die Bedienungshilfe verarbeitet Fenster-/App-Ereignisse, um die aktive App zu erkennen und ausgewählte Apps zu sperren. Sie ist nicht zum Auslesen von Bildschirmtext eingerichtet (`canRetrieveWindowContent=false`).

Der Nutzungsdatenzugriff liefert Android-Ereignisse zur App-Nutzung. Wachwerk berechnet daraus die heutige Vordergrundzeit. Es greift nicht auf private Samsung-Datenbanken zu.

## NFC, QR und Passwort

NFC dient zum Wiedererkennen des angelernten Tags. Der QR-Scanner prüft den erwarteten lokalen Code. Blocker-Passwörter werden als Prüfsumme gespeichert. Diese Mechanismen dienen der Selbstorganisation, nicht als manipulationssichere Gerätesperre oder Schutz vor einem Angreifer mit Gerätezugriff.

Keine NFC-Kennungen, QR-Inhalte, Passwörter oder personenbezogenen Logs in öffentlichen Issues teilen.

## Android-Backup

Im Manifest ist `allowBackup=true` gesetzt. Android beziehungsweise der Gerätehersteller kann App-Daten deshalb je nach Systemeinstellungen sichern oder auf ein anderes Gerät übertragen. „Offline“ bedeutet hier: Die App selbst betreibt keine Datenübertragung zu einem eigenen Dienst; es ist keine Garantie gegen systemseitige Sicherungen. Wer diese nicht möchte, muss die Backup-Einstellungen seines Geräts prüfen.

## Löschen

Deinstallation oder „App-Daten löschen“ entfernt lokale App-Daten. Vor einem normalen Update nicht deinstallieren. Vorhandene systemseitige Backups werden dadurch nicht zwingend ebenfalls gelöscht.

[Zurück zur README](../README.md)
