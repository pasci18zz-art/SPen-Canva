package com.spenremote.presentation

import android.os.Handler
import android.os.SystemClock

enum class SlideAction { NEXT, PREVIOUS }

/**
 * Unterscheidet Einfach- und Doppeldruck anhand eines Zeitfensters.
 *
 * - instant = false (klassisch): Nach dem ersten Druck wird das Fenster abgewartet.
 *   Kommt kein zweiter Druck -> NEXT. Kommt einer -> PREVIOUS. Eindeutig, aber mit Verzögerung.
 * - instant = true: Der erste Druck löst sofort NEXT aus. Ein zweiter Druck im Fenster
 *   löst zweimal PREVIOUS aus (macht das NEXT rückgängig und geht eine Folie zurück).
 */
class ClickDetector(
    private val handler: Handler,
    private val windowMs: () -> Long,
    private val instant: () -> Boolean,
    private val onResult: (isDouble: Boolean, actions: List<SlideAction>) -> Unit,
) {
    private var firstPressAt = 0L
    private var pending: Runnable? = null

    fun press() {
        val now = SystemClock.uptimeMillis()
        val inWindow = firstPressAt != 0L && now - firstPressAt <= windowMs()

        if (inWindow) {
            // Zweiter Druck innerhalb des Fensters -> Doppeldruck
            val wasInstant = pending == null
            pending?.let { handler.removeCallbacks(it) }
            pending = null
            firstPressAt = 0L
            onResult(true, if (wasInstant) listOf(SlideAction.PREVIOUS, SlideAction.PREVIOUS) else listOf(SlideAction.PREVIOUS))
            return
        }

        firstPressAt = now
        if (instant()) {
            pending = null
            onResult(false, listOf(SlideAction.NEXT))
        } else {
            val r = Runnable {
                pending = null
                firstPressAt = 0L
                onResult(false, listOf(SlideAction.NEXT))
            }
            pending = r
            handler.postDelayed(r, windowMs())
        }
    }

    fun cancel() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        firstPressAt = 0L
    }
}
