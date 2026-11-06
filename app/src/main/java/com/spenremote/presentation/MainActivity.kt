package com.spenremote.presentation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Hauptseite + eingebaute Präsentationsansicht.
 *
 * Diese Activity ist bei Samsung Air Actions registriert (res/xml/remote_action.xml).
 * Solange sie im Vordergrund ist, schickt Samsungs S-Pen-Framework die Tastendrücke als
 * KeyEvents hierher:
 *   Einmal drücken  -> KEYCODE_DPAD_RIGHT  (Samsung erkennt Einfach/Doppel)
 *   Zweimal drücken -> KEYCODE_DPAD_LEFT
 *   „Rohsignal“     -> KEYCODE_F12         (App erkennt Einfach/Doppel selbst)
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var rawDetector: ClickDetector

    private lateinit var statusAirActions: TextView
    private lateinit var statusPen: TextView
    private lateinit var statusService: TextView
    private lateinit var lastInputBig: TextView
    private lateinit var lastInputDetail: TextView
    private lateinit var eventLog: TextView
    private lateinit var urlField: EditText
    private lateinit var presenter: FrameLayout
    private lateinit var webView: WebView
    private lateinit var presenterToast: TextView

    private val busListener: (EventBus.Entry) -> Unit = { renderLog() }

    private val ticker = object : Runnable {
        override fun run() {
            renderPenStatus()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        statusAirActions = findViewById(R.id.statusAirActions)
        statusPen = findViewById(R.id.statusPen)
        statusService = findViewById(R.id.statusService)
        lastInputBig = findViewById(R.id.lastInputBig)
        lastInputDetail = findViewById(R.id.lastInputDetail)
        eventLog = findViewById(R.id.eventLog)
        urlField = findViewById(R.id.urlField)
        presenter = findViewById(R.id.presenter)
        webView = findViewById(R.id.webView)
        presenterToast = findViewById(R.id.presenterToast)

        rawDetector = ClickDetector(
            handler,
            windowMs = { prefs.doubleClickMs.toLong() },
            instant = { false }, // im Vordergrund immer eindeutig entscheiden
        ) { isDouble, actions ->
            onSlide(actions.last(), if (isDouble) "Doppelklick" else "Einfachklick", "App-Erkennung (${prefs.doubleClickMs} ms)")
        }

        setupSettings()
        setupButtons()
        setupWebView()
        findViewById<TextView>(R.id.setupText).text = SETUP_TEXT
        applyContentWidth()
    }

    override fun onResume() {
        super.onResume()
        EventBus.addListener(busListener)
        renderStatus()
        renderLog()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        EventBus.removeListener(busListener)
        handler.removeCallbacks(ticker)
        rawDetector.cancel()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyContentWidth()
    }

    // ---------------------------------------------------------------- S-Pen-Eingaben

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val relevant = code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_LEFT ||
            code == KeyEvent.KEYCODE_F12
        // Beim Tippen in das URL-Feld Pfeiltasten einer echten Tastatur normal lassen.
        if (!relevant || (presenter.visibility != View.VISIBLE && urlField.hasFocus())) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            EventBus.markSignal()
            val source = "${KeyEvent.keyCodeToString(code)} · Gerät ${event.deviceId}"
            when (code) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> onSlide(SlideAction.NEXT, "Einfachklick", "Samsung-Erkennung · $source")
                KeyEvent.KEYCODE_DPAD_LEFT -> onSlide(SlideAction.PREVIOUS, "Doppelklick", "Samsung-Erkennung · $source")
                KeyEvent.KEYCODE_F12 -> {
                    EventBus.post("Rohsignal", source)
                    rawDetector.press()
                }
            }
        }
        return true // Down und Up verbrauchen
    }

    private fun onSlide(action: SlideAction, gesture: String, detail: String) {
        val arrow = if (action == SlideAction.NEXT) "▶  Pfeil rechts" else "◀  Pfeil links"
        lastInputBig.text = "$gesture → $arrow"
        lastInputDetail.text = detail
        EventBus.post("$gesture → $arrow", detail)

        if (presenter.visibility == View.VISIBLE) {
            sendArrowToWebView(action)
            showPresenterToast(if (action == SlideAction.NEXT) "▶ Weiter" else "◀ Zurück")
        }
    }

    /** Innerhalb der eigenen App dürfen wir KeyEvents frei an eigene Views senden. */
    private fun sendArrowToWebView(action: SlideAction) {
        val code = if (action == SlideAction.NEXT) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        if (!webView.hasFocus()) webView.requestFocus()
        val t = SystemClock.uptimeMillis()
        webView.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        webView.dispatchKeyEvent(KeyEvent(t, t + 30, KeyEvent.ACTION_UP, code, 0))
    }

    // ---------------------------------------------------------------- Präsentationsansicht

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        urlField.setText(prefs.lastUrl)
    }

    private fun openPresenter() {
        var url = urlField.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Bitte einen Link einfügen", Toast.LENGTH_SHORT).show()
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        prefs.lastUrl = url
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(urlField.windowToken, 0)
        urlField.clearFocus()

        presenter.visibility = View.VISIBLE
        if (webView.url != url) webView.loadUrl(url)
        webView.requestFocus()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setFullscreen(true)
        showPresenterToast("S Pen: 1× weiter · 2× zurück · Zurück-Taste beendet")
    }

    private fun closePresenter() {
        presenter.visibility = View.GONE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setFullscreen(false)
    }

    private fun setFullscreen(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val c = window.insetsController ?: return
            if (on) {
                c.hide(WindowInsets.Type.systemBars())
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                c.show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (on) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else 0
        }
    }

    private fun showPresenterToast(text: String) {
        presenterToast.text = text
        presenterToast.visibility = View.VISIBLE
        handler.removeCallbacks(hidePresenterToast)
        handler.postDelayed(hidePresenterToast, 1500)
    }

    private val hidePresenterToast = Runnable { presenterToast.visibility = View.GONE }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (presenter.visibility == View.VISIBLE) closePresenter() else super.onBackPressed()
    }

    // ---------------------------------------------------------------- Einstellungen

    private fun setupSettings() {
        bindSeek(R.id.seekDoubleClick, R.id.labelDoubleClick, prefs.doubleClickMs, 10,
            { "Doppelklick-Erkennungszeit: $it ms" }) { prefs.doubleClickMs = it }
        bindSeek(R.id.seekHoldWindow, R.id.labelHoldWindow, prefs.holdWindowMs, 100,
            { "Halte-Fenster: ${"%.1f".format(it / 1000f)} s" }) { prefs.holdWindowMs = it }
        bindSeek(R.id.seekDelay, R.id.labelDelay, prefs.delayMs, 10,
            { "Verzögerung vor dem Senden: $it ms" }) { prefs.delayMs = it }

        findViewById<Switch>(R.id.switchInstant).apply {
            isChecked = prefs.instant
            setOnCheckedChangeListener { _, checked -> prefs.instant = checked }
        }

        val radio = findViewById<RadioGroup>(R.id.radioOutput)
        radio.check(
            when (prefs.output) {
                Prefs.Output.KEY -> R.id.outKey
                Prefs.Output.SWIPE -> R.id.outSwipe
                Prefs.Output.TAP -> R.id.outTap
            }
        )
        if (Build.VERSION.SDK_INT < 33) findViewById<View>(R.id.outKey).isEnabled = false
        radio.setOnCheckedChangeListener { _, id ->
            prefs.output = when (id) {
                R.id.outSwipe -> Prefs.Output.SWIPE
                R.id.outTap -> Prefs.Output.TAP
                else -> Prefs.Output.KEY
            }
        }
    }

    private fun bindSeek(seekId: Int, labelId: Int, value: Int, step: Int, text: (Int) -> String, save: (Int) -> Unit) {
        val seek = findViewById<SeekBar>(seekId)
        val label = findViewById<TextView>(labelId)
        seek.progress = value
        label.text = text(value)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val v = (progress / step) * step
                label.text = text(v)
                if (fromUser) save(v)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnAppInfo).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            EventBus.clear()
            handler.post { renderLog() }
        }
        findViewById<Button>(R.id.btnPresent).setOnClickListener { openPresenter() }
        findViewById<Button>(R.id.btnTestOutput).setOnClickListener {
            val svc = RemoteAccessibilityService.instance
            if (svc == null) {
                Toast.makeText(this, "Zuerst den Bedienungshilfe-Dienst einschalten", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Jetzt zur Präsentation wechseln – „weiter“ kommt in 5 s", Toast.LENGTH_LONG).show()
                svc.perform(listOf(SlideAction.NEXT), "Ausgabetest", 5000)
            }
        }
    }

    // ---------------------------------------------------------------- Status

    private fun renderStatus() {
        val airCommand = try {
            packageManager.getApplicationInfo(PenTriggerActivity.AIR_COMMAND_PACKAGE, 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        statusAirActions.text = when (airCommand) {
            true -> "● Samsung-Air-Command-Dienst vorhanden. Ob Air Actions eingeschaltet sind, gibt Samsung nicht öffentlich preis – siehe Einrichtung."
            false -> "● Samsung-Air-Command-Dienst ist deaktiviert."
            null -> "● Samsung-Air-Command-Dienst nicht gefunden – Air Actions auf diesem Gerät nicht verfügbar."
        }
        statusAirActions.setTextColor(getColor(if (airCommand == true) R.color.ok else R.color.bad))

        val enabled = RemoteAccessibilityService.isEnabledInSettings(this)
        val connected = RemoteAccessibilityService.instance != null
        statusService.text = when {
            enabled && connected -> "● Aktiv – Folien können an andere Apps gesendet werden"
            enabled -> "● Eingeschaltet, aber nicht verbunden – einmal aus- und wieder einschalten"
            else -> "● Aus – nur Steuerung innerhalb dieser App möglich"
        }
        statusService.setTextColor(getColor(if (enabled && connected) R.color.ok else R.color.warn))
        renderPenStatus()
    }

    private fun renderPenStatus() {
        val last = EventBus.lastSignalAt
        val ago = if (last == 0L) null else (SystemClock.uptimeMillis() - last) / 1000
        statusPen.text = buildString {
            append(
                when {
                    ago == null -> "Noch kein Signal empfangen"
                    ago < 2 -> "● Signal gerade empfangen"
                    ago < 60 -> "Letztes Signal vor $ago s"
                    else -> "Letztes Signal vor ${ago / 60} min"
                }
            )
            append("\nDen Bluetooth-Verbindungsstatus des S Pen stellt Samsung Apps nicht öffentlich zur Verfügung.")
        }
        statusPen.setTextColor(getColor(if (ago != null && ago < 60) R.color.ok else R.color.text))
    }

    private fun renderLog() {
        eventLog.text = if (EventBus.entries.isEmpty()) "Noch keine Ereignisse."
        else EventBus.entries.joinToString("\n") { "${it.time}  ${it.title}\n              ${it.detail}" }
    }

    private fun applyContentWidth() {
        val content = findViewById<LinearLayout>(R.id.content)
        val d = resources.displayMetrics.density
        val widthPx = resources.displayMetrics.widthPixels
        val maxContent = (760 * d).toInt()
        val side = maxOf((24 * d).toInt(), (widthPx - maxContent) / 2)
        content.setPadding(side, content.paddingTop, side, content.paddingBottom)
    }

    companion object {
        private val SETUP_TEXT = """
            A) In dieser App (Einmal/Zweimal drücken)
            1. Einstellungen › Erweiterte Funktionen › S Pen › Air Actions einschalten.
            2. Dort unter „App-Aktionen“ Presentation Remote einschalten.
               Einmal drücken = Nächste Folie, Zweimal drücken = Vorherige Folie.
            3. Testbereich oben: Taste drücken, ohne das Display zu berühren.
            4. Optional: „Einmal drücken“ auf „Rohsignal“ stellen und „Zweimal drücken“ leeren – dann gilt die einstellbare Doppelklick-Zeit dieser App.

            B) In Canva & anderen Apps (Drücken und halten)
            Samsung schickt Einfach-/Doppelklicks nur an die App im Vordergrund, die sie selbst registriert hat (z. B. PowerPoint). Canva tut das nicht. Die einzige S-Pen-Aktion, die eine fremde App starten darf, ist „Drücken und halten“.
            1. Einstellungen › Apps › Presentation Remote › ⋮ › „Eingeschränkte Einstellungen zulassen“ (nötig für per APK installierte Apps ab Android 13).
            2. Einstellungen › Eingabehilfe › Installierte Apps › Presentation Remote › Ein.
            3. Einstellungen › Apps › Presentation Remote › Akku › Nicht eingeschränkt.
            4. Air Actions › „Drücken und halten der S Pen-Taste“ › Presentation Remote.
            5. In Canva präsentieren: 1× halten = weiter, 2× halten = zurück.
            6. Klappt die Pfeiltaste nicht, oben „Wischgeste“ oder „Tippen“ wählen und mit „Ausgabe testen“ prüfen.
        """.trimIndent()
    }
}
