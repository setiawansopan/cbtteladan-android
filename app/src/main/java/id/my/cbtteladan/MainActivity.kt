package id.my.cbtteladan

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Aplikasi ujian CBT Teladan untuk Android.
 *
 * Alur: pemeriksaan perangkat -> "Mulai Ujian" menyematkan layar (screen
 * pinning) -> halaman ujian dimuat di WebView. Selama ujian layar harus tetap
 * tersemat dan tidak boleh dibagi; bila terlepas, halaman ujian ditutup panel
 * peringatan dan WebView dijeda sehingga halaman ujian mencatat pelanggaran.
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var examContainer: View
    private lateinit var precheck: View
    private lateinit var lockOverlay: View
    private lateinit var lockMessage: TextView
    private lateinit var errorPanel: View
    private lateinit var pinHint: TextView
    private lateinit var checkInternet: TextView
    private lateinit var checkBattery: TextView
    private lateinit var checkWebView: TextView
    private lateinit var clock: TextView
    private lateinit var network: TextView
    private lateinit var battery: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("id", "ID"))

    /** Halaman ujian sudah pernah dimuat. */
    private var examLoaded = false

    /** Menunggu siswa menyetujui permintaan sematkan layar. */
    private var waitingForPinSince = 0L

    /** Panel peringatan sedang menutupi halaman ujian. */
    private var overlayShown = false

    /** URL halaman yang sedang dibuka; dibaca dari thread JavaScript. */
    @Volatile
    private var currentUrl = ""

    private var bridgeScript = ""
    private var documentStartScript = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val monitor = object : Runnable {
        override fun run() {
            checkDeviceState()
            handler.postDelayed(this, MONITOR_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cegah tangkapan layar, rekam layar, dan layar mati selama ujian
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        examContainer = findViewById(R.id.examContainer)
        precheck = findViewById(R.id.precheck)
        lockOverlay = findViewById(R.id.lockOverlay)
        lockMessage = findViewById(R.id.lockMessage)
        errorPanel = findViewById(R.id.errorPanel)
        pinHint = findViewById(R.id.pinHint)
        checkInternet = findViewById(R.id.checkInternet)
        checkBattery = findViewById(R.id.checkBattery)
        checkWebView = findViewById(R.id.checkWebView)
        clock = findViewById(R.id.clock)
        network = findViewById(R.id.network)
        battery = findViewById(R.id.battery)

        findViewById<TextView>(R.id.versionText).text = getString(R.string.version_label, ExamClient.version)
        findViewById<Button>(R.id.startButton).setOnClickListener { requestPin() }
        findViewById<Button>(R.id.precheckExitButton).setOnClickListener { exitApp() }
        findViewById<Button>(R.id.exitButton).setOnClickListener { confirmExit() }
        findViewById<Button>(R.id.overlayExitButton).setOnClickListener { confirmExit() }
        findViewById<Button>(R.id.refreshButton).setOnClickListener { reloadExam() }
        findViewById<Button>(R.id.retryButton).setOnClickListener { reloadExam() }
        findViewById<Button>(R.id.repinButton).setOnClickListener { startPinning() }

        setupWebView()
        runPrecheck()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (!overlayShown) webView.onResume()
        handler.removeCallbacks(monitor)
        handler.post(monitor)
    }

    override fun onPause() {
        // Menjeda WebView membuat halaman "tersembunyi", sehingga halaman
        // ujian mencatat pelanggaran saat siswa meninggalkan aplikasi.
        webView.onPause()
        handler.removeCallbacks(monitor)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Deprecated("Tombol kembali sengaja dinonaktifkan selama ujian")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Tidak melakukan apa-apa: siswa keluar lewat tombol Keluar
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // ------------------------------------------------------------------
    // Sematkan layar dan pemantauan
    // ------------------------------------------------------------------

    private fun isPinned(): Boolean {
        val manager = getSystemService(ActivityManager::class.java) ?: return false
        return manager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun startPinning() {
        try {
            startLockTask()
        } catch (e: RuntimeException) {
            // Beberapa perangkat menolak bila fitur sematkan dimatikan
        }
    }

    private fun requestPin() {
        if (isPinned()) {
            showExam()
            return
        }
        waitingForPinSince = SystemClock.elapsedRealtime()
        pinHint.text = getString(R.string.pin_waiting)
        pinHint.setTextColor(getColor(R.color.text_muted))
        startPinning()
    }

    private fun checkDeviceState() {
        if (waitingForPinSince > 0) {
            if (isPinned()) {
                waitingForPinSince = 0
                showExam()
            } else if (SystemClock.elapsedRealtime() - waitingForPinSince > PIN_TIMEOUT_MS) {
                waitingForPinSince = 0
                pinHint.text = getString(R.string.pin_failed)
                pinHint.setTextColor(getColor(R.color.danger))
            }
        }

        if (examContainer.visibility == View.VISIBLE) {
            val problem = when {
                isInMultiWindowMode -> R.string.multiwindow_message
                !isPinned() -> R.string.unpinned_message
                else -> null
            }
            setOverlay(problem)
            updateStatusBar()
        }
    }

    private fun setOverlay(messageRes: Int?) {
        if (messageRes == null) {
            if (overlayShown) {
                overlayShown = false
                lockOverlay.visibility = View.GONE
                webView.onResume()
            }
            return
        }
        lockMessage.text = getString(messageRes)
        if (!overlayShown) {
            overlayShown = true
            lockOverlay.visibility = View.VISIBLE
            webView.onPause()
        }
    }

    private fun showExam() {
        precheck.visibility = View.GONE
        examContainer.visibility = View.VISIBLE
        updateStatusBar()
        if (!examLoaded) {
            examLoaded = true
            loadExam(ExamClient.examUrl)
        }
    }

    // ------------------------------------------------------------------
    // Keluar
    // ------------------------------------------------------------------

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.exit_confirm) { _, _ -> exitApp() }
            .show()
    }

    private fun exitApp() {
        waitingForPinSince = 0
        if (isPinned()) {
            try {
                stopLockTask()
            } catch (e: RuntimeException) {
                // Tidak sedang tersemat
            }
        }
        finishAndRemoveTask()
    }

    // ------------------------------------------------------------------
    // WebView
    // ------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100
            userAgentString = ExamClient.userAgent(userAgentString)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        // Tanpa menu tekan-lama (salin/tempel/cari di web)
        webView.isLongClickable = false
        webView.isHapticFeedbackEnabled = false
        webView.setOnLongClickListener { true }
        webView.setDownloadListener { _, _, _, _, _ -> }

        webView.addJavascriptInterface(NativeBridge(), "CBTNative")
        bridgeScript = assets.open("cbt-bridge.js").bufferedReader().use { it.readText() }

        documentStartScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (documentStartScript) {
            WebViewCompat.addDocumentStartJavaScript(webView, bridgeScript, setOf(ExamClient.examOrigin))
        }

        webView.webViewClient = ExamWebViewClient()
        webView.webChromeClient = ExamChromeClient()
    }

    private fun loadExam(url: String) {
        errorPanel.visibility = View.GONE
        webView.loadUrl(url, ExamClient.headers())
    }

    private fun reloadExam() {
        val url = webView.url?.takeIf { ExamClient.isExamUrl(Uri.parse(it)) } ?: ExamClient.examUrl
        loadExam(url)
    }

    private fun injectBridgeIfNeeded(view: WebView, url: String) {
        if (!documentStartScript && ExamClient.isExamUrl(Uri.parse(url))) {
            view.evaluateJavascript(bridgeScript, null)
        }
    }

    /** Dipanggil skrip cbt-bridge.js untuk setiap request AJAX/fetch. */
    private inner class NativeBridge {
        @JavascriptInterface
        fun headers(): String {
            if (!ExamClient.isExamUrl(Uri.parse(currentUrl))) return "{}"
            return JSONObject(ExamClient.headers()).toString()
        }
    }

    private inner class ExamWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false

            val uri = request.url
            if (!ExamClient.isExamUrl(uri)) {
                Toast.makeText(this@MainActivity, R.string.link_blocked, Toast.LENGTH_SHORT).show()
                return true
            }

            // Muat sendiri agar header aplikasi + tanda tangan ikut terkirim,
            // termasuk saat server mengalihkan (redirect) halaman.
            view.loadUrl(uri.toString(), ExamClient.headers())
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            currentUrl = url
            injectBridgeIfNeeded(view, url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            currentUrl = url
            // Cadangan untuk WebView lama; skrip tidak dipasang dua kali
            injectBridgeIfNeeded(view, url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            currentUrl = url
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                errorPanel.visibility = View.VISIBLE
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // Mesin WebView berhenti (mis. memori habis): mulai ulang layar
            recreate()
            return true
        }
    }

    private inner class ExamChromeClient : WebChromeClient() {

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback

            val intent = Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*")

            return try {
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQUEST_FILE)
                true
            } catch (e: ActivityNotFoundException) {
                fileCallback = null
                false
            }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            // Kamera/mikrofon tidak dipakai di halaman siswa
            request.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }
    }

    // ------------------------------------------------------------------
    // Pemeriksaan perangkat dan bilah status
    // ------------------------------------------------------------------

    private fun runPrecheck() {
        setCheck(checkInternet, R.string.check_internet, getString(R.string.check_running), R.color.text_muted)

        thread(name = "cbt-precheck") {
            val started = SystemClock.elapsedRealtime()
            val reachable = try {
                val connection = URL(ExamClient.examUrl + "/up").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.instanceFollowRedirects = false
                val code = connection.responseCode
                connection.disconnect()
                code in 200..499
            } catch (e: Exception) {
                false
            }
            val elapsed = SystemClock.elapsedRealtime() - started

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (reachable) {
                    setCheck(checkInternet, R.string.check_internet, getString(R.string.internet_ok, elapsed.toInt()), R.color.ok)
                } else {
                    setCheck(checkInternet, R.string.check_internet, getString(R.string.internet_fail), R.color.danger)
                }
            }
        }

        val (level, charging) = batteryState()
        when {
            level == null -> setCheck(checkBattery, R.string.check_battery, "-", R.color.text_muted)
            level < LOW_BATTERY && !charging ->
                setCheck(checkBattery, R.string.check_battery, getString(R.string.battery_low, level), R.color.warn)
            else -> setCheck(
                checkBattery,
                R.string.check_battery,
                getString(R.string.battery_value, level, if (charging) getString(R.string.battery_charging) else ""),
                R.color.ok,
            )
        }

        val webViewVersion = WebViewCompat.getCurrentWebViewPackage(this)?.versionName
        val major = webViewVersion?.substringBefore('.')?.toIntOrNull()
        when {
            webViewVersion == null -> setCheck(checkWebView, R.string.check_webview, "-", R.color.text_muted)
            major != null && major < MIN_WEBVIEW_MAJOR ->
                setCheck(checkWebView, R.string.check_webview, getString(R.string.webview_old, webViewVersion), R.color.warn)
            else -> setCheck(checkWebView, R.string.check_webview, webViewVersion, R.color.ok)
        }
    }

    private fun setCheck(view: TextView, labelRes: Int, value: String, colorRes: Int) {
        view.text = "${getString(labelRes)}: $value"
        view.setTextColor(getColor(colorRes))
    }

    private fun updateStatusBar() {
        clock.text = timeFormat.format(Date())

        val (level, charging) = batteryState()
        battery.text = if (level == null) "" else "$level%" + if (charging) " +" else ""

        network.text = getString(networkLabel())
    }

    private fun batteryState(): Pair<Int?, Boolean> {
        val manager = getSystemService(BatteryManager::class.java) ?: return null to false
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        return level to manager.isCharging
    }

    private fun networkLabel(): Int {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return R.string.net_offline
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return R.string.net_offline
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> R.string.net_wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> R.string.net_cellular
            else -> R.string.net_other
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
        }
    }

    private companion object {
        const val REQUEST_FILE = 41
        const val MONITOR_INTERVAL_MS = 1000L
        const val PIN_TIMEOUT_MS = 30_000L
        const val LOW_BATTERY = 20
        const val MIN_WEBVIEW_MAJOR = 80
    }
}
