package com.spenremote.presentation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Bedienungshilfe-Dienst – die einzige offizielle Android-Schnittstelle, über die eine normale
 * (nicht-System-)App Eingaben an eine ANDERE App im Vordergrund senden kann:
 *
 * - performGlobalAction(GLOBAL_ACTION_DPAD_RIGHT / _LEFT)  (ab Android 13 / API 33)
 *   -> das System erzeugt einen Pfeiltasten-Druck im fokussierten Fenster.
 * - dispatchGesture(...)  (ab Android 7)
 *   -> Wisch- oder Tippgeste auf dem Bildschirm.
 *
 * Ein freies Einspeisen beliebiger KeyEvents (INJECT_EVENTS) ist für normale Apps gesperrt.
 */
class RemoteAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var detector: ClickDetector

    /** Zuletzt im Vordergrund gesehene App (nur Paketname, keine Inhalte). */
    @Volatile
    var foregroundPackage: String? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        detector = ClickDetector(
            handler,
            windowMs = { prefs.holdWindowMs.toLong() },
            instant = { prefs.instant },
        ) { isDouble, actions ->
            val label = if (isDouble) "Zweimal gehalten" else "Einmal gehalten"
            perform(actions, label, prefs.delayMs.toLong())
        }
        instance = this
        EventBus.post("Bedienungshilfe-Dienst", "verbunden")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in IGNORED_PACKAGES) return
        foregroundPackage = pkg
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        if (::detector.isInitialized) detector.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** Aufgerufen von [PenTriggerActivity], wenn Air Actions „Drücken und halten“ ausgelöst hat. */
    fun onPenHold() {
        detector.press()
    }

    /** Sendet die Folienaktionen nach [delayMs] an die App im Vordergrund. */
    fun perform(actions: List<SlideAction>, label: String, delayMs: Long) {
        actions.forEachIndexed { i, action ->
            handler.postDelayed({
                val ok = performOne(action)
                if (i == actions.lastIndex) {
                    val target = foregroundPackage ?: "unbekannt"
                    val arrow = if (actions.last() == SlideAction.NEXT) "▶ Pfeil rechts" else "◀ Pfeil links"
                    val count = if (actions.size > 1) " (${actions.size}×)" else ""
                    EventBus.post(
                        "$label → $arrow$count",
                        "an $target · ${prefs.output.label()} · ${if (ok) "gesendet" else "vom System abgelehnt"}"
                    )
                }
            }, delayMs + i * STEP_GAP_MS)
        }
    }

    private fun performOne(action: SlideAction): Boolean = when (prefs.output) {
        Prefs.Output.KEY ->
            if (Build.VERSION.SDK_INT >= 33) {
                performGlobalAction(
                    if (action == SlideAction.NEXT) GLOBAL_ACTION_DPAD_RIGHT else GLOBAL_ACTION_DPAD_LEFT
                )
            } else {
                swipe(action) // Fallback für Android < 13
            }
        Prefs.Output.SWIPE -> swipe(action)
        Prefs.Output.TAP -> tap(action)
    }

    private fun screenSize(): Pair<Float, Float> {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            b.width().toFloat() to b.height().toFloat()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels.toFloat() to dm.heightPixels.toFloat()
        }
    }

    /** Weiter = von rechts nach links wischen (wie beim Blättern). */
    private fun swipe(action: SlideAction): Boolean {
        val (w, h) = screenSize()
        val y = h * 0.5f
        val (fromX, toX) = if (action == SlideAction.NEXT) w * 0.8f to w * 0.2f else w * 0.2f to w * 0.8f
        val path = Path().apply { moveTo(fromX, y); lineTo(toX, y) }
        return dispatch(path, 180)
    }

    /** Tippen am rechten (weiter) bzw. linken (zurück) Rand. */
    private fun tap(action: SlideAction): Boolean {
        val (w, h) = screenSize()
        val x = if (action == SlideAction.NEXT) w * 0.92f else w * 0.08f
        val path = Path().apply { moveTo(x, h * 0.5f) }
        return dispatch(path, 40)
    }

    private fun dispatch(path: Path, durationMs: Long): Boolean {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        var instance: RemoteAccessibilityService? = null
            private set

        private const val STEP_GAP_MS = 220L

        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            PenTriggerActivity.AIR_COMMAND_PACKAGE,
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin",
        )

        /** Ist der Dienst in den Android-Einstellungen eingeschaltet? */
        fun isEnabledInSettings(context: Context): Boolean {
            val me = ComponentName(context, RemoteAccessibilityService::class.java)
            val list = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}

fun Prefs.Output.label(): String = when (this) {
    Prefs.Output.KEY -> "Pfeiltaste"
    Prefs.Output.SWIPE -> "Wischgeste"
    Prefs.Output.TAP -> "Tippen"
}
