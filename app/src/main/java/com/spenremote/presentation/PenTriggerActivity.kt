package com.spenremote.presentation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Unsichtbares "Trampolin".
 *
 * In den Air-Actions-Einstellungen kann man unter „Drücken und halten“ eine beliebige App
 * auswählen. Wählt man diese App, startet Samsungs Air-Command-Dienst diese Activity – auch
 * wenn gerade Canva o. Ä. im Vordergrund ist. Sie zeigt nichts an, meldet das Signal an den
 * Bedienungshilfe-Dienst und beendet sich sofort, sodass die Präsentation wieder vorne ist.
 *
 * Wird sie normal über das App-Symbol gestartet, öffnet sie die Hauptseite.
 */
class PenTriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val caller = referrer?.host
        if (caller == AIR_COMMAND_PACKAGE) {
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
