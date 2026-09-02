# Drittanbieter-Hinweise

Die folgenden direkt genutzten Komponenten sind nicht Eigentum des Wachwerk-Projekts. Die vorhandenen Urheber- und Lizenzhinweise bleiben erhalten.

| Komponente | Verwendung | Lizenztext |
| --- | --- | --- |
| React 19.2.6 | Lokale Oberfläche | [MIT](docs/licenses/React-MIT.txt) |
| React DOM 19.2.6 | Darstellung der Oberfläche | [MIT](docs/licenses/React-DOM-MIT.txt) |
| ZXing Core 3.4.1 | QR-Code-Erzeugung und -Erkennung | [Upstream-Lizenztext](docs/licenses/ZXing-LICENSE.txt) |
| Gradle Wrapper 8.13 | Build-Startskripte und Wrapper | Apache 2.0; Lizenzhinweise stehen auch in den Wrapper-Skripten. Der Apache-2.0-Text ist im [beigefügten Lizenztext](docs/licenses/ZXing-LICENSE.txt) enthalten. |

ZXing Core verwendet Apache 2.0. Die vollständige Upstream-Lizenzdatei enthält zusätzliche Hinweise anderer Komponenten des ZXing-Gesamtprojekts; das bedeutet nicht, dass diese Komponenten alle in Wachwerk verwendet werden.

Build-/Test-Abhängigkeiten, etwa Vite, TypeScript, esbuild, JUnit und Mockito, werden beim Build über die jeweiligen Paketquellen bezogen. Ihre eigenen Paketlizenzen sind dort enthalten. Versionen stehen in den Build-Dateien und im pnpm-Lockfile. Dies ist keine vollständige transitive Software-Stückliste.

Bei Weitergabe der APK die beigefügten Drittanbieter-Lizenztexte mitliefern. Im vorbereiteten Release-Paket gibt es dafür `Wachwerk-Lizenzen-v1.13.0.zip`.

Quellen: [React](https://github.com/facebook/react), [ZXing 3.4.1](https://github.com/zxing/zxing/tree/zxing-3.4.1), [Gradle](https://github.com/gradle/gradle).
