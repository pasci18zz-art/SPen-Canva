# Phase 1 – Technische Machbarkeit

Stand: November 2026 · Zielgerät: Galaxy Tab S10+ (One UI, Android 14 oder neuer)

## Kurzfassung

| Frage | Antwort |
|---|---|
| Kann die App in den Air-Actions-Einstellungen erscheinen? | **Ja.** Über die offizielle `REMOTE_ACTION`-Registrierung erscheint sie unter „App-Aktionen“ und bekommt eigene Aktionen für „Einmal drücken“ / „Zweimal drücken“. |
| Empfängt sie Klicks, während eine **andere** App im Vordergrund ist? | **Nein, nicht Einfach-/Doppelklicks.** Samsung schickt sie nur an die App, die oben im Vordergrund ist **und** selbst Aktionen registriert hat. **Ausnahme:** „Drücken und halten“ kann eine beliebige App starten, auch über Canva. |
| Kann sie Klicks in globale Tastatureingaben für andere Apps umwandeln? | **Nicht mit normalen App-Rechten.** `INJECT_EVENTS` ist nur für System-Apps erlaubt. **Möglich ist es über einen Bedienungshilfe-Dienst:** `performGlobalAction(GLOBAL_ACTION_DPAD_RIGHT/LEFT)` (ab Android 13) oder Wisch- und Tippgesten. |
| Gewünschte Architektur (Einfach = rechts, Doppel = links, in jeder App) | **So nicht umsetzbar.** Machbar sind zwei Wege, siehe unten. |

## 1. Offizielle Samsung-Schnittstellen

### 1a. Air Actions „App-Aktionen“ (`REMOTE_ACTION`)
- Die App fügt einer Activity im Manifest einen Intent-Filter und ein `meta-data`-Element `com.samsung.android.support.REMOTE_ACTION` hinzu. Dieses verweist auf eine XML-Datei (`<remote-actions>`).
- Jede `<action>` hat `id`, `label`, `priority` und `trigger_key`. `trigger_key` ist der KeyEvent-Name ohne `KEYCODE_`, z. B. `DPAD_RIGHT`. Kombinationen schreibt man mit `+`.
- Mit `<preference name="gesture" value="click|double_click|…"/>` legt man die Standardbelegung fest.
- Samsungs S-Pen-Framework wandelt das Bluetooth-Signal in genau dieses KeyEvent um. Es schickt das KeyEvent **an die App im Vordergrund**, und nur, wenn diese App das KeyEvent registriert hat. So funktioniert es bei PowerPoint.
- Nur **eine** Activity pro App darf RemoteActions haben.
- Neu installierte Apps sind in Air Actions standardmäßig **aus**. Man muss sie von Hand einschalten. Eine automatische Aktivierung (`enable_key`) gibt es nur mit einer Sondergenehmigung von Samsung.
- **Samsung erkennt Einfach- und Doppelklick selbst.** Die App bekommt dann schon das passende KeyEvent.

### 1b. S Pen Remote SDK (`SpenRemote`, `SpenUnitManager`, `SpenEventListener`)
- Liefert rohe Tasten- und Bewegungsdaten.
- Laut Samsung-FAQ funktioniert es **nur in der Activity im Vordergrund**. Listener müssen in `onPause` abgemeldet werden.
- Das SDK ist eine AAR-Datei, die man nach einem Login bei developer.samsung.com herunterlädt. Es steht in keinem Maven-Repository.
- Ein Erfahrungsbericht (Projekt *pen-scroll*, Galaxy S24 Ultra) meldet, dass der SDK-Bindungsdienst auf neueren Geräten fehlt.
- **Bringt für fremde Apps also nichts.** Deshalb wird es nicht verwendet.

### 1c. Was im Hintergrund wirklich ankommt
Auf einem S24 Ultra wurde das per Logcat untersucht (Projekt *pen-scroll*, siehe Quellen):
- Der Air-Command-Dienst (`com.samsung.android.service.aircommand`) bekommt Einfach- und Doppelklicks über BLE. Hat die App im Vordergrund keine eigene Aktion, zeigt er nur den Hinweis „keine Aktion“. **An andere Apps wird nichts weitergegeben.**
- Der Dienst prüft `isTopActivityHasAppDefinedRemoteAction`. Eine unsichtbare Activity, die man über Canva legt, wurde getestet. Sie stört die Wiedergabe und kann die App einfrieren (ANR).
- **„Drücken und halten“** kann in den Air-Actions-Einstellungen eine **beliebige App starten**, auch wenn eine andere App im Vordergrund ist. Das ist der einzige S-Pen-Weg aus fremden Apps heraus.

## 2. Tastendrücke an andere Apps senden

| Weg | Status |
|---|---|
| `Instrumentation.sendKeyDownUpSync`, `InputManager.injectInputEvent` | Braucht `INJECT_EVENTS`, eine Signatur-/System-Berechtigung. **Für normale Apps nicht möglich.** |
| `adb shell input keyevent 22` | Funktioniert nur mit ADB-/Shell-Rechten. Für den Alltag ungeeignet. |
| **AccessibilityService** `performGlobalAction(GLOBAL_ACTION_DPAD_RIGHT = 19 / _LEFT = 18)` | Offiziell ab **Android 13 (API 33)**. Das System erzeugt einen Pfeiltasten-Druck im fokussierten Fenster. **Wird verwendet.** |
| **AccessibilityService** `dispatchGesture` (Wischen/Tippen) | Offiziell ab Android 7. **Wird als Alternative verwendet.** |
| In der **eigenen** App `view.dispatchKeyEvent(...)` | Uneingeschränkt erlaubt. **Wird für die eingebaute Präsentationsansicht verwendet.** |

Einschränkungen beim Bedienungshilfe-Dienst:
- Seit Android 13 sind Bedienungshilfen für Apps aus einer APK-Datei zunächst gesperrt. Man muss sie freischalten: App-Info › ⋮ › „Eingeschränkte Einstellungen zulassen“.
- Für Google Play wären eine Begründung und eine Prüfung nötig. Für eine selbst installierte APK gilt das nicht.
- Der Dienst liest hier **keine** Bildschirminhalte (`canRetrieveWindowContent=false`). Er merkt sich nur, welches Paket im Vordergrund ist.
- One UI kann Dienste im Hintergrund beenden. Deshalb sollte man den Akku auf „Nicht eingeschränkt“ stellen.

## 3. Daraus folgende Lösungen (umgesetzt)

### Weg A – Präsentieren in der App (voll offiziell, Einfach-/Doppelklick wie gewünscht)
S Pen → Air Actions → `MainActivity`, die im Vordergrund ist → einfacher oder doppelter Klick → `KEYCODE_DPAD_RIGHT/LEFT` → eingebettete WebView mit dem Canva-Ansichtslink → Canva reagiert auf ArrowRight/ArrowLeft.
Eine Bedienungshilfe ist dafür nicht nötig. Die Doppelklick-Zeit kommt von Samsung. Mit dem „Rohsignal“ kann man stattdessen eine eigene Zeit in der App einstellen.

### Weg B – In der Canva-App oder jeder anderen App („Drücken und halten“)
1× halten → Air Actions startet das unsichtbare `PenTriggerActivity` → der Bedienungshilfe-Dienst zählt die Halte-Vorgänge im Zeitfenster → Pfeiltaste, Wischgeste oder Tippen an die App im Vordergrund.
1× halten = weiter, 2× halten = zurück. **Abweichung vom Wunsch:** Halten statt kurz Drücken. Ein Halten dauert bei Samsung etwa 0,8 s.

### Weg C – ohne eigene App
Canva-Präsentation als PPTX exportieren und in PowerPoint zeigen. Dort funktionieren Air Actions bereits.

### Was eine echte Lösung „Einfachklick in jeder App“ bräuchte
- Samsung müsste Air Actions für Drittanbieter global freigeben. Ein solcher Modus ist nicht dokumentiert.
- **Oder** Canva müsste selbst `REMOTE_ACTION`-Aktionen registrieren, so wie PowerPoint.
- **Oder** Systemrechte (`INJECT_EVENTS`) und der Zugriff auf die Ereignisse des Air-Command-Dienstes. Das geht nur mit einer platform-signierten System-App oder Root und kommt deshalb nicht in Frage.

## 4. Unsicherheiten, die nur am Gerät geklärt werden können
1. Ob die Canva-Android-App im Präsentationsmodus auf `DPAD_RIGHT/LEFT` reagiert. Sonst „Wischgeste“ oder „Tippen“ wählen. Die App hat dafür „Ausgabe testen“.
2. Ob Canva den Präsentationsmodus behält, wenn die unsichtbare Trampolin-Activity kurz startet.
3. Ob die genaue Syntax der Air-Actions-XML (Version `1.2`, `button_only`) von One UI auf dem Tab S10+ angenommen wird. Das prüft man so: Erscheint die App unter Air Actions › App-Aktionen?
4. Das Tastenkürzel `F12` für das Rohsignal ist frei gewählt. Wird es abgelehnt, auf eine andere Taste ausweichen.

## Quellen
- Samsung: [Air Actions (S Pen Remote)](https://developer.samsung.com/galaxy-spen-remote/air-actions.html), [Code Lab Air Actions](https://developer.samsung.com/codelab/s-pen-remote/air-actions/development.html), [FAQ](https://developer.samsung.com/galaxy-spen-remote/faq.html), [Common Mistakes](https://developer.samsung.com/sdp/blog/en/2020/06/11/avoid-common-mistakes-when-using-the-s-pen-remote-sdk), [Note9-Blog](https://developer.samsung.com/sdp/blog/en-us/2018/10/04/new-developer-features-for-the-galaxy-note9-s-pen)
- Samsung Support: [Air actions auf dem Tablet](https://www.samsung.com/us/support/answer/ANS00087382/)
- Android: [API-33-Änderungen AccessibilityService (GLOBAL_ACTION_DPAD_*)](https://developer.android.com/sdk/api_diff/33/changes/android.accessibilityservice.AccessibilityService)
- Praxisbericht: [pen-scroll](https://github.com/thisisgaganbirru/pen-scroll) (Logcat-Analyse des Air-Command-Dienstes, Trampolin über „Drücken und halten“)
