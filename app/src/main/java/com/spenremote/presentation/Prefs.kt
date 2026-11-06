package com.spenremote.presentation

import android.content.Context
import android.os.Build

/** Alle Einstellungen der App (SharedPreferences). */
class Prefs(context: Context) {

    enum class Output { KEY, SWIPE, TAP }

    private val sp = context.applicationContext.getSharedPreferences("remote", Context.MODE_PRIVATE)

    /** Doppelklick-Fenster für das Rohsignal (App-eigene Erkennung im Vordergrund). */
    var doubleClickMs: Int
        get() = sp.getInt("double_click_ms", 350)
        set(v) = sp.edit().putInt("double_click_ms", v).apply()

    /** Zeitfenster für „zweimal Drücken und halten“. */
    var holdWindowMs: Int
        get() = sp.getInt("hold_window_ms", 2000)
        set(v) = sp.edit().putInt("hold_window_ms", v).apply()

    /** true: erstes Halten schaltet sofort weiter; false: Fenster abwarten, dann entscheiden. */
    var instant: Boolean
        get() = sp.getBoolean("instant", true)
        set(v) = sp.edit().putBoolean("instant", v).apply()

    var output: Output
        get() = runCatching { Output.valueOf(sp.getString("output", null) ?: "") }
            .getOrDefault(if (Build.VERSION.SDK_INT >= 33) Output.KEY else Output.SWIPE)
        set(v) = sp.edit().putString("output", v.name).apply()

    /** Wartezeit nach dem unsichtbaren Trampolin-Start, bevor die Aktion gesendet wird. */
    var delayMs: Int
        get() = sp.getInt("delay_ms", 400)
        set(v) = sp.edit().putInt("delay_ms", v).apply()

    /** Kurze Bestätigung (Toast) beim Auslösen über „Drücken und halten“. */
    var confirmToast: Boolean
        get() = sp.getBoolean("confirm_toast", true)
        set(v) = sp.edit().putBoolean("confirm_toast", v).apply()

    var lastUrl: String
        get() = sp.getString("last_url", "") ?: ""
        set(v) = sp.edit().putString("last_url", v).apply()
}
