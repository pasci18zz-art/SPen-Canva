package com.spenremote.presentation

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Einfacher In-App-Ereigniskanal für Testbereich und Statusanzeige. */
object EventBus {

    data class Entry(val time: String, val title: String, val detail: String)

    private const val MAX = 12
    private val main = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY)
    private val listeners = mutableSetOf<(Entry) -> Unit>()

    val entries = ArrayDeque<Entry>()

    /** Zeitpunkt (uptime) des letzten S-Pen-Signals, 0 = noch keins. */
    @Volatile
    var lastSignalAt = 0L
        private set

    fun markSignal() {
        lastSignalAt = SystemClock.uptimeMillis()
    }

    fun post(title: String, detail: String) {
        val e = Entry(fmt.format(Date()), title, detail)
        main.post {
            entries.addFirst(e)
            while (entries.size > MAX) entries.removeLast()
            listeners.toList().forEach { it(e) }
        }
    }

    fun clear() = main.post { entries.clear() }

    fun addListener(l: (Entry) -> Unit) = listeners.add(l)
    fun removeListener(l: (Entry) -> Unit) = listeners.remove(l)
}
