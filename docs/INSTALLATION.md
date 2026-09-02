# Installation & erster Test

## 1. APK herunterladen

Öffne auf GitHub **Releases**, wähle Version **1.13.0** und lade unter **Assets** die Datei `Wachwerk-v1.13.0-Offline-signiert.apk` herunter. Die automatisch angebotenen „Source code“-ZIPs enthalten Quellcode, keine direkt installierbare App.

Du brauchst Android 8.0 oder neuer. NFC ist optional; QR, Schütteln und andere Aufgaben stehen als Alternativen zur Verfügung.

## 2. Installieren oder aktualisieren

Öffne die APK auf dem Handy. Erlaube Android bei Bedarf die Installation aus der verwendeten Quelle, zum Beispiel dem Browser oder Dateimanager. Diese Erlaubnis kann danach wieder ausgeschaltet werden.

**Vorhandene App nicht deinstallieren.** Die Release-Version 1.13.0 verwendet denselben Schlüssel wie die bisherigen Wachwerk-Releases. Bei einer Meldung über eine inkompatible Signatur nicht einfach deinstallieren: Vermutlich ist eine anders signierte Version installiert.

## 3. Android-Zugriffe prüfen

Wachwerk zeigt die notwendigen Zugriffe in der App an. Nicht jede Funktion braucht alle Berechtigungen:

| Zugriff | Wofür? |
| --- | --- |
| Benachrichtigungen | Alarmhinweise, Erinnerungen und Fokus-Status |
| Alarme & Erinnerungen / genaue Alarme | Möglichst genaue Auslösung geplanter Wecker |
| Vollbild-Benachrichtigungen | Wecker-Ansicht bei gesperrtem Bildschirm |
| Kamera | Nur für QR-Aufwachaufgaben und QR-Freigaben |
| NFC | Einen eigenen Tag erkennen; NFC muss am Handy eingeschaltet sein |
| Bedienungshilfe | Ausgewählte Apps erkennen und die Sperransicht anzeigen |
| Nutzungsdatenzugriff | Heutige App-Nutzung für Tageslimits auswerten |
| Nicht-stören-Zugriff | Optional Benachrichtigungen während einer Fokusphase unterdrücken |

Bei seitlich installierten Apps kann Android besondere Bestätigungen verlangen. Prüfe solche Warnungen bewusst und erlaube Zugriffe nur, wenn du der installierten APK vertraust. Bezeichnungen unterscheiden sich je nach Hersteller.

## 4. Vor der ersten Nacht testen

1. Einen Wecker wenige Minuten in die Zukunft stellen, Display sperren und warten.
2. Beim Klingeln Home öffnen. Der Ton soll weiterlaufen; die Alarm-Benachrichtigung führt zurück zur Aufgabe.
3. Die Aufgabe abschließen. Bei NFC den zuvor angelernten Tag benutzen. Reagiert NFC am gesperrten Gerät nicht, „Handy für NFC entsperren“ verwenden und anschließend erneut scannen.
4. Unter **Blocker → Morgen** eine App auswählen, eine Minute einstellen und aktivieren. Nach dem nächsten erfolgreich beendeten Wecker muss diese App vorübergehend gesperrt sein. Snooze darf die Morgensperre noch nicht starten.
5. Ein Tageslimit mit kurzer Nutzungszeit prüfen und die Anzeige am nächsten Tag kontrollieren.

Hersteller-Energiesparmodi können Hintergrundfunktionen beeinflussen. Ein komplett ausgeschaltetes Handy, Androids „Stopp erzwingen“ oder entzogene Berechtigungen kann Wachwerk nicht umgehen. Bis der Gerätetest erfolgreich war, für wichtige Termine einen zweiten Wecker verwenden.

## Hinweise zur Genauigkeit

Die Nutzungszeit wird aus Android-Nutzungsereignissen seit lokaler Mitternacht rekonstruiert. Es wird nicht die sichtbare Samsung-Bildschirmzeit-Anzeige ausgelesen. Rundung, Aktualisierung und Mehrfensterbetrieb können zu Abweichungen führen.

Schlafvorschläge und Coach-Auswertungen sind Orientierung aus eigenen Angaben, keine Messung von Schlafphasen.

[Zurück zur README](../README.md)
