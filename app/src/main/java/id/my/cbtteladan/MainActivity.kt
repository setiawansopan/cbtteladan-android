package id.my.cbtteladan

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
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
import android.widget.ImageView
import android.widget.LinearLayout
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
    private lateinit var listDevice: LinearLayout
    private lateinit var listNetwork: LinearLayout
    private lateinit var summaryText: TextView
    private lateinit var barClock: TextView
    private lateinit var barDate: TextView
    private lateinit var barNetworkIcon: ImageView
    private lateinit var barNetworkLabel: TextView
    private lateinit var barBatteryIcon: ImageView
    private lateinit var barBatteryText: TextView

    /** Semua baris di halaman pengecekan, untuk menyusun ringkasan hasil. */
    private val checkRows = mutableListOf<CheckRow>()

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("id", "ID"))
    private val dateFormat = SimpleDateFormat("d MMM", Locale("id", "ID"))

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
        listDevice = findViewById(R.id.listDevice)
        listNetwork = findViewById(R.id.listNetwork)
        summaryText = findViewById(R.id.summaryText)
        barClock = findViewById(R.id.barClock)
        barDate = findViewById(R.id.barDate)
        barNetworkIcon = findViewById(R.id.barNetworkIcon)
        barNetworkLabel = findViewById(R.id.barNetworkLabel)
        barBatteryIcon = findViewById(R.id.barBatteryIcon)
        barBatteryText = findViewById(R.id.barBatteryText)

        findViewById<TextView>(R.id.versionText).text = getString(R.string.version_label, ExamClient.version)
        findViewById<TextView>(R.id.barVersion).text =
            getString(R.string.bar_version, ExamClient.version, getString(R.string.school_name))
        findViewById<Button>(R.id.startButton).setOnClickListener { requestPin() }
        findViewById<Button>(R.id.precheckExitButton).setOnClickListener { exitApp() }
        findViewById<View>(R.id.exitButton).setOnClickListener { confirmExit() }
        findViewById<Button>(R.id.overlayExitButton).setOnClickListener { confirmExit() }
        findViewById<View>(R.id.refreshButton).setOnClickListener { reloadExam() }
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

    /** Satu baris di halaman pengecekan: spinner saat memeriksa, lalu ikon hasil. */
    private class CheckRow(
        private val progress: View,
        private val statusBg: View,
        private val statusIcon: ImageView,
        private val value: TextView,
    ) {
        /** Null selama masih memeriksa. */
        var status: CheckStatus? = null
            private set

        fun show(text: String, newStatus: CheckStatus) {
            val context = value.context
            status = newStatus
            value.text = text
            value.setTextColor(context.getColor(R.color.text))
            progress.visibility = View.GONE
            statusBg.visibility = View.VISIBLE

            val (icon, tint, background) = when (newStatus) {
                CheckStatus.PASS -> Triple(R.drawable.ic_check, R.color.ok, R.color.ok_bg)
                CheckStatus.WARN -> Triple(R.drawable.ic_warning, R.color.warn, R.color.warn_bg)
                CheckStatus.FAIL -> Triple(R.drawable.ic_close, R.color.danger, R.color.danger_bg)
            }
            statusIcon.setImageResource(icon)
            statusIcon.imageTintList = ColorStateList.valueOf(context.getColor(tint))
            statusBg.backgroundTintList = ColorStateList.valueOf(context.getColor(background))
        }
    }

    private fun addRow(
        list: LinearLayout,
        icon: Int,
        tint: Int,
        background: Int,
        label: Int,
        requirement: Int? = null,
    ): CheckRow {
        val view = layoutInflater.inflate(R.layout.item_check, list, false)

        view.findViewById<ImageView>(R.id.rowIcon).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(getColor(tint))
        }
        view.findViewById<View>(R.id.rowIconBg).backgroundTintList = ColorStateList.valueOf(getColor(background))
        view.findViewById<TextView>(R.id.rowLabel).setText(label)
        view.findViewById<TextView>(R.id.rowReq).apply {
            if (requirement != null) setText(requirement) else visibility = View.GONE
        }
        val value = view.findViewById<TextView>(R.id.rowValue).apply {
            setText(R.string.checking)
            setTextColor(getColor(R.color.faint))
        }

        list.addView(view)

        return CheckRow(
            view.findViewById(R.id.rowProgress),
            view.findViewById(R.id.rowStatusBg),
            view.findViewById(R.id.rowStatus),
            value,
        ).also { checkRows.add(it) }
    }

    private fun runPrecheck() {
        listDevice.removeAllViews()
        listNetwork.removeAllViews()
        checkRows.clear()
        summaryText.setText(R.string.summary_checking)
        summaryText.setTextColor(getColor(R.color.text_muted))

        val device = addRow(listDevice, R.drawable.ic_phone, R.color.ic_blue, R.color.ic_blue_bg, R.string.row_device)
        val androidRow = addRow(listDevice, R.drawable.ic_info, R.color.ic_indigo, R.color.ic_indigo_bg, R.string.row_android, R.string.req_android)
        val cpu = addRow(listDevice, R.drawable.ic_board, R.color.ic_orange, R.color.ic_orange_bg, R.string.row_cpu, R.string.req_cpu)
        val ram = addRow(listDevice, R.drawable.ic_memory, R.color.ic_purple, R.color.ic_purple_bg, R.string.row_ram, R.string.req_ram)
        val storage = addRow(listDevice, R.drawable.ic_storage, R.color.ic_amber, R.color.ic_amber_bg, R.string.row_storage, R.string.req_storage)
        val screen = addRow(listDevice, R.drawable.ic_screen, R.color.ic_teal, R.color.ic_teal_bg, R.string.row_screen)
        val battery = addRow(listDevice, R.drawable.ic_battery, R.color.ic_green, R.color.ic_green_bg, R.string.row_battery, R.string.req_battery)

        val network = addRow(listNetwork, R.drawable.ic_wifi, R.color.ic_blue, R.color.ic_blue_bg, R.string.row_network)
        val server = addRow(listNetwork, R.drawable.ic_public, R.color.ic_green, R.color.ic_green_bg, R.string.row_server, R.string.req_server)
        val webViewRow = addRow(listNetwork, R.drawable.ic_extension, R.color.ic_pink, R.color.ic_pink_bg, R.string.row_webview, R.string.req_webview)

        // Perangkat keras dan sistem
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        device.show(
            if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}",
            CheckStatus.PASS,
        )
        androidRow.show(getString(R.string.val_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT), CheckStatus.PASS)

        val cores = Runtime.getRuntime().availableProcessors()
        cpu.show(getString(R.string.val_cores, cores), SystemCheck.cpu(cores))

        val totalRam = totalMemoryBytes()
        ram.show(SystemCheck.formatBytes(totalRam), SystemCheck.ram(totalRam))

        val stat = StatFs(Environment.getDataDirectory().path)
        storage.show(
            getString(R.string.val_storage, SystemCheck.formatBytes(stat.availableBytes), SystemCheck.formatBytes(stat.totalBytes)),
            SystemCheck.storage(stat.availableBytes),
        )

        val (width, height) = screenSize()
        screen.show(
            getString(R.string.val_screen, minOf(width, height), maxOf(width, height), resources.displayMetrics.densityDpi),
            CheckStatus.PASS,
        )

        val (level, charging) = batteryState()
        battery.show(
            when {
                level == null -> getString(R.string.val_unknown)
                charging -> getString(R.string.val_battery_charging, level)
                else -> getString(R.string.val_battery, level)
            },
            SystemCheck.battery(level, charging),
        )

        // Koneksi
        when (networkKind()) {
            NetworkKind.WIFI -> network.show(getString(R.string.val_net_wifi), CheckStatus.PASS)
            NetworkKind.CELLULAR -> network.show(getString(R.string.val_net_cell), CheckStatus.PASS)
            NetworkKind.OTHER -> network.show(getString(R.string.val_net_other), CheckStatus.PASS)
            NetworkKind.NONE -> network.show(getString(R.string.val_net_none), CheckStatus.FAIL)
        }

        val webViewVersion = WebViewCompat.getCurrentWebViewPackage(this)?.versionName
        val major = webViewVersion?.substringBefore('.')?.toIntOrNull()
        val webViewStatus = SystemCheck.webView(major)
        webViewRow.show(
            when {
                webViewVersion == null -> getString(R.string.val_unknown)
                webViewStatus != CheckStatus.PASS -> getString(R.string.val_webview_old, webViewVersion)
                else -> webViewVersion
            },
            webViewStatus,
        )

        // Server ujian: satu-satunya pemeriksaan yang butuh waktu, jadi jalan di thread lain
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
            val elapsed = (SystemClock.elapsedRealtime() - started).toInt()

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                val status = SystemCheck.server(reachable, elapsed)
                server.show(
                    when (status) {
                        CheckStatus.FAIL -> getString(R.string.val_server_fail)
                        CheckStatus.WARN -> getString(R.string.val_server_slow, elapsed)
                        CheckStatus.PASS -> getString(R.string.val_server_ok, elapsed)
                    },
                    status,
                )
                updateSummary()
            }
        }

        updateSummary()
    }

    /** Ringkasan di bawah daftar; baru tampil setelah semua pemeriksaan selesai. */
    private fun updateSummary() {
        if (checkRows.any { it.status == null }) return

        val results = checkRows.mapNotNull { it.status }
        val attention = results.count { it != CheckStatus.PASS }

        if (attention == 0) {
            summaryText.setText(R.string.summary_ok)
        } else {
            summaryText.text = getString(R.string.summary_warn, attention)
        }
        summaryText.setTextColor(
            getColor(
                when (SystemCheck.worst(results)) {
                    CheckStatus.PASS -> R.color.ok
                    CheckStatus.WARN -> R.color.warn
                    CheckStatus.FAIL -> R.color.danger
                },
            ),
        )
    }

    private fun totalMemoryBytes(): Long {
        val info = ActivityManager.MemoryInfo()
        getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        return info.totalMem
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = android.util.DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    /** Bilah bawah selama ujian: jam, jaringan, dan baterai, seperti taskbar di aplikasi Windows. */
    private fun updateStatusBar() {
        val now = Date()
        barClock.text = timeFormat.format(now)
        barDate.text = dateFormat.format(now)

        val (level, charging) = batteryState()
        val low = SystemCheck.battery(level, charging) != CheckStatus.PASS
        val batteryColor = ColorStateList.valueOf(getColor(if (low) R.color.accent_red else R.color.on_bar))
        barBatteryText.text = level?.let { "$it%" } ?: ""
        barBatteryText.setTextColor(batteryColor)
        barBatteryIcon.setImageResource(if (charging) R.drawable.ic_battery_charging else R.drawable.ic_battery)
        barBatteryIcon.imageTintList = batteryColor

        val kind = networkKind()
        barNetworkIcon.setImageResource(if (kind == NetworkKind.CELLULAR) R.drawable.ic_cell else R.drawable.ic_wifi)
        barNetworkIcon.alpha = if (kind == NetworkKind.NONE) 0.4f else 1f
        barNetworkLabel.setText(
            when (kind) {
                NetworkKind.WIFI -> R.string.bar_wifi
                NetworkKind.CELLULAR -> R.string.bar_cell
                NetworkKind.OTHER -> R.string.bar_online
                NetworkKind.NONE -> R.string.bar_offline
            },
        )
        // Layar sempit: cukup ikon agar bilah tidak penuh
        barNetworkLabel.visibility = if (resources.configuration.screenWidthDp >= 400) View.VISIBLE else View.GONE
    }

    private fun batteryState(): Pair<Int?, Boolean> {
        val manager = getSystemService(BatteryManager::class.java) ?: return null to false
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        return level to manager.isCharging
    }

    private enum class NetworkKind { WIFI, CELLULAR, OTHER, NONE }

    private fun networkKind(): NetworkKind {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return NetworkKind.NONE
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return NetworkKind.NONE
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
            else -> NetworkKind.OTHER
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
    }
}
