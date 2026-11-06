package com.spenremote.presentation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Unsichtbares "Trampolin".
 *
 * In den Air-Actions-Einstellungen kann man unter „Drücken und halten“ eine beliebige App
 * auswählen. Samsungs Air-Command-Dienst startet dann diese Activity – auch wenn gerade Canva
 * o. Ä. im Vordergrund ist. Sie zeigt nichts an, meldet das Signal an den
 * Bedienungshilfe-Dienst und beendet sich sofort, sodass die Präsentation wieder vorne ist.
 *
 * Haupt-Symbol „Presentation Remote“: Start durch Air Command -> Folienaktion,
 * sonst Hauptseite öffnen (Erkennung über den Referrer).
 * Zweites Symbol „S-Pen-Halten (Remote)“ ([PenHoldActivity]): löst IMMER eine Folienaktion aus.
 * Das ist die robuste Wahl für „Drücken und halten“, falls One UI einen anderen Referrer meldet.
 */
open class PenTriggerActivity : Activity() {

    protected open val alwaysTrigger = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val caller = referrer?.host ?: "unbekannt"
        val fromPen = alwaysTrigger || caller == AIR_COMMAND_PACKAGE ||
            caller.contains("aircommand") || caller.contains("spen")

        EventBus.post(
            "Start von „${if (alwaysTrigger) "S-Pen-Halten" else "Presentation Remote"}“",
            "Aufrufer: $caller · ${if (fromPen) "→ Folienaktion" else "→ Hauptseite"}"
        )

        if (fromPen) {
            EventBus.markSignal()
            val service = RemoteAccessibilityService.instance
            if (service == null) {
                EventBus.post("S Pen: Halten erkannt", "Bedienungshilfe-Dienst ist AUS – keine Weitergabe")
                Toast.makeText(this, "Presentation Remote: Bedienungshilfe-Dienst einschalten", Toast.LENGTH_LONG).show()
            } else {
                service.onPenHold()
            }
        } else {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val AIR_COMMAND_PACKAGE = "com.samsung.android.service.aircommand"
    }
}

/** Zweites Launcher-Symbol: für „Drücken und halten“ auswählen – löst immer weiter/zurück aus. */
class PenHoldActivity : PenTriggerActivity() {
    override val alwaysTrigger = true
}
