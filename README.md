# S Pen Presentation Remote

Native Android-App (Kotlin) für das Galaxy Tab S10+. Damit steuerst du Präsentationen mit der S-Pen-Taste, ohne das Display zu berühren.

**Bitte zuerst lesen:** [docs/PHASE1_MACHBARKEIT.md](docs/PHASE1_MACHBARKEIT.md). Samsung schickt Einfach- und Doppelklicks nur an die App im Vordergrund, die sie selbst registriert hat. Ein globales „Einfachklick = Pfeil rechts in jeder App“ ist mit offiziellen Mitteln deshalb nicht möglich. Die App bietet zwei funktionierende Wege:

| Weg | S-Pen-Eingabe | Wo |
|---|---|---|
| **A** Präsentieren in der App | 1× drücken = ▶, 2× drücken = ◀ | Canva-Ansichtslink in der eingebauten Vollbildansicht |
| **B** Systemweit über die Bedienungshilfe | 1× halten = ▶, 2× halten = ◀ | Canva-App, Browser, jede andere App |

## Projektstruktur

| Datei | Zweck |
|---|---|
| `app/src/main/AndroidManifest.xml` | Activities, Air-Actions-`meta-data`, Bedienungshilfe-Dienst |
| `app/src/main/res/xml/remote_action.xml` | Samsung-Air-Actions-Registrierung (Weiter = `DPAD_RIGHT`, Zurück = `DPAD_LEFT`, Rohsignal = `F12`) |
| `app/src/main/res/xml/accessibility_service_config.xml` | Rechte des Bedienungshilfe-Dienstes (nur Gesten und Vordergrund-Paket, keine Bildschirminhalte) |
| `.../MainActivity.kt` | Oberfläche, Empfang der Air-Actions-Tasten, eingebaute Präsentationsansicht (WebView) |
| `.../ClickDetector.kt` | Unterscheidung Einfach-/Doppeldruck mit einstellbarem Zeitfenster |
| `.../PenTriggerActivity.kt` | Unsichtbares Trampolin für „Drücken und halten“ (Weg B) |
| `.../RemoteAccessibilityService.kt` | Sendet Pfeiltaste (`GLOBAL_ACTION_DPAD_*`), Wischen oder Tippen an die App im Vordergrund |
| `.../Prefs.kt`, `.../EventBus.kt` | Einstellungen und Ereignisprotokoll für den Testbereich |
| `.github/workflows/build-apk.yml` | Baut bei jedem Push automatisch die APK |

## APK bekommen

**Variante 1 – GitHub (ohne Android Studio):** Repository › *Actions* › letzter Lauf von „APK bauen“ › unten unter *Artifacts* `PresentationRemote-apk` herunterladen. Das ist eine ZIP-Datei, darin liegt `app-debug.apk`.

**Variante 2 – Android Studio:** Projektordner öffnen, Gradle synchronisieren lassen, dann *Build › Build APK(s)*. Die APK liegt unter `app/build/outputs/apk/debug/app-debug.apk`. Im Terminal geht es mit `./gradlew assembleDebug`.

## Auf dem Tab S10+ installieren
1. APK auf das Tablet kopieren und in *Eigene Dateien* antippen. „Unbekannte Apps installieren“ für Eigene Dateien erlauben.
2. App öffnen. Die Einrichtungsschritte stehen auch in der App.

## Einrichtung

**Weg A (Einmal/Zweimal drücken in der App)**
1. Einstellungen › Erweiterte Funktionen › S Pen › **Air Actions** einschalten.
2. Unter *App-Aktionen* **Presentation Remote** einschalten. Einmal drücken = Nächste Folie, Zweimal drücken = Vorherige Folie.
3. In der App im *Testbereich* die Taste drücken. Das Ergebnis wird angezeigt.
4. In Canva: *Teilen › Ansichtslink* kopieren, in der App einfügen und *Präsentation öffnen* antippen.
5. Optional: „Einmal drücken“ auf **Rohsignal** stellen und „Zweimal drücken“ leer lassen. Dann gilt die einstellbare Doppelklick-Zeit der App.

**Weg B (Canva-App und andere Apps)**
1. Einstellungen › Apps › Presentation Remote › ⋮ › **Eingeschränkte Einstellungen zulassen**.
2. Einstellungen › Eingabehilfe › Installierte Apps › Presentation Remote › **Ein**.
3. Apps › Presentation Remote › Akku › **Nicht eingeschränkt**.
4. Air Actions › **Drücken und halten der S Pen-Taste** › **S-Pen-Halten (Remote)** (zweites App-Symbol, löst immer aus).
5. In Canva präsentieren: Taste ca. 1 s **halten** = weiter, 2× halten (innerhalb des Halte-Fensters) = zurück. Kurzes Drücken kommt in Canva nicht an.
   Beim Halten erscheint kurz „▶ weiter“. Erscheint nichts, steht im Testbereich der App, was angekommen ist.
6. Reagiert Canva nicht auf die Pfeiltaste, in der App *Wischgeste* oder *Tippen* wählen. Mit *Ausgabe testen* lässt sich das prüfen, ohne den S Pen zu benutzen.

## Bekannte offene Punkte (nur am Gerät prüfbar)
Siehe Abschnitt 4 in [docs/PHASE1_MACHBARKEIT.md](docs/PHASE1_MACHBARKEIT.md).
