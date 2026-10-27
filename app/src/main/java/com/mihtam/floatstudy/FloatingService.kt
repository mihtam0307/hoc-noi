package com.mihtam.floatstudy

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

class FloatingService : Service() {
    companion object {
        @Volatile var running = false
        @Volatile var instance: FloatingService? = null
        const val A_STOP = "stop"
        const val A_STOP_READ = "stopread"
        const val CH = "float_study"
        private const val HOME = "https://m.youtube.com"
        private const val POLL_JS =
            "(function(){var v=document.querySelector('video');if(!v||!v.duration)return '';" +
            "var t=document.title.replace(/ - YouTube\$/,'');" +
            "return JSON.stringify({p:!v.paused&&!v.ended,t:v.currentTime,d:v.duration,n:t});})()"
        private const val TOGGLE_JS =
            "(function(){var v=document.querySelector('video');if(v){if(v.paused){v.play()}else{v.pause()}}})()"
    }

    private lateinit var wm: WindowManager
    private lateinit var speaker: Speaker
    private val ui = Handler(Looper.getMainLooper())

    // Cửa sổ nổi
    private var bubble: ImageView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: FrameLayout? = null
    private var host: FrameLayout? = null          // cửa sổ 1px giữ WebView sống khi khung bị thu nhỏ

    // Thanh mini (hiện khi thu nhỏ mà vẫn đang đọc / đang phát video)
    private var mini: LinearLayout? = null
    private var miniLp: WindowManager.LayoutParams? = null
    private var miniYtRow: LinearLayout? = null
    private var miniRdRow: LinearLayout? = null
    private var miniPlay: IconBtn? = null
    private var miniTitle: TextView? = null
    private var miniTime: TextView? = null
    private var miniRd: TextView? = null
    private var miniBar: ProgressLine? = null
    private var miniShown = false

    // Trạng thái khung đọc (giữ lại khi dựng lại giao diện)
    private var inputText = ""
    private var lines: List<String> = emptyList()
    private var repeatIdx = 0
    private var settingsOpen = false
    private var settingsY = 0
    private var settingsSv: ScrollView? = null
    private var curLine = -1
    private var pass = 1
    private var curLook: Look? = null
    private val lineViews = ArrayList<TextView>()
    private var statusTv: TextView? = null
    private var listScroll: ScrollView? = null
    private var readEt: EditText? = null
    private val repeats = intArrayOf(1, 2, 5, 10, 20, -1)

    // Tab: 0 = đọc tự động, 1 = YouTube
    private var tab = 0

    // YouTube
    private var yt: WebView? = null
    private var ytHolder: FrameLayout? = null
    private var ytEt: EditText? = null
    private var ytBack: IconBtn? = null
    private var ytProgress: ProgressLine? = null
    private var sugBox: LinearLayout? = null
    private var sugRun: Runnable? = null
    private var sugToken = 0
    private var ytQuery = ""
    private var ytHas = false
    private var ytPlaying = false
    private var ytTitle = ""
    private var ytTime = 0.0
    private var ytDur = 0.0
    private var lastW = 0
    private var lastH = 0
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    private val pollRun = object : Runnable {
        override fun run() {
            pollYt()
            ui.postDelayed(this, 1000)
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val k = key ?: return@OnSharedPreferenceChangeListener
        ui.post {
            if (k.startsWith("bubble_")) applyBubble()
            if (panel == null && k == "content") {
                inputText = Prefs.str(this, "content") ?: ""
                lines = parse(inputText)
            }
            if (panel == null && k == "repeat") repeatIdx = Prefs.int(this, "repeat", 0).coerceIn(0, 5)
            if (k.startsWith("f_")) dropMini()
            if (panel != null && (k.startsWith("f_") || k == "text_size" || k == "speed")) buildPanel()
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        instance = this
        inputText = Prefs.str(this, "content") ?: ""
        lines = parse(inputText)
        repeatIdx = Prefs.int(this, "repeat", 0).coerceIn(0, 5)
        tab = Prefs.int(this, "last_tab", 0).coerceIn(0, 1)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        speaker = Speaker(this)
        speaker.onLine = { i, p -> curLine = i; pass = p; refreshStatus(); highlight(); refreshMini() }
        speaker.onEnd = { curLine = -1; refreshStatus(); highlight(); refreshMini() }

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "App nổi", NotificationManager.IMPORTANCE_LOW))
        val n = notif()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        showBubble()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        when (i?.action) {
            A_STOP -> stopSelf()
            A_STOP_READ -> speaker.stop()
        }
        return START_NOT_STICKY
    }

    /** Xoay màn hình: dựng lại bố cục theo hướng mới, WebView giữ nguyên nên video không bị gián đoạn. */
    override fun onConfigurationChanged(cfg: Configuration) {
        super.onConfigurationChanged(cfg)
        ui.postDelayed({
            clampBubble()
            if (panel != null) buildPanel() else positionMini()
        }, 150)
    }

    override fun onDestroy() {
        running = false
        instance = null
        ui.removeCallbacks(pollRun)
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        speaker.shutdown()
        customCb?.onCustomViewHidden()
        yt?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            try { it.destroy() } catch (_: Exception) {}
        }
        yt = null
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        host?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        mini?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }

    private fun notif(): Notification {
        fun pi(a: String, rc: Int) = PendingIntent.getService(
            this, rc, Intent(this, FloatingService::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Học Nổi đang bật")
            .setContentText("Chạm hình nổi để mở khung. Kéo để di chuyển.")
            .setContentIntent(open)
            .addAction(0, "Dừng đọc", pi(A_STOP_READ, 1))
            .addAction(0, "Tắt app nổi", pi(A_STOP, 2))
            .setOngoing(true)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun screen(): Pair<Int, Int> {
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        return dm.widthPixels to dm.heightPixels
    }

    private fun hardware() = WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    // ---------------------------------------------------------------- Hình nổi

    private fun showBubble() {
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.clipToOutline = true
        iv.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setOval(0, 0, v.width, v.height)
            }
        }
        val size = Ui.dp(this, Prefs.int(this, "bubble_size", 64))
        val lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = Ui.dp(this, 8)
        lp.y = Ui.dp(this, 220)
        bubble = iv
        bubbleLp = lp

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        iv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > Ui.dp(this, 6) || abs(dy) > Ui.dp(this, 6)) moved = true
                    if (moved) {
                        val (sw, sh) = screen()
                        lp.x = (startX + dx).coerceIn(0, maxOf(0, sw - lp.width))
                        lp.y = (startY + dy).coerceIn(0, maxOf(0, sh - lp.height))
                        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                        positionMini()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) showPanel()
                    true
                }
                else -> false
            }
        }
        wm.addView(iv, lp)
        applyBubble()
    }

    private fun applyBubble() {
        val iv = bubble ?: return
        val lp = bubbleLp ?: return
        val size = Ui.dp(this, Prefs.int(this, "bubble_size", 64))
        lp.width = size
        lp.height = size
        iv.alpha = Prefs.int(this, "bubble_alpha", 90) / 100f
        val bmp = Img.load(Prefs.str(this, "bubble_img"), 512)
        if (bmp != null) iv.setImageBitmap(bmp) else iv.setImageResource(R.drawable.ic_launcher)
        try { wm.updateViewLayout(iv, lp) } catch (_: Exception) {}
        positionMini()
    }

    private fun clampBubble() {
        val iv = bubble ?: return
        val lp = bubbleLp ?: return
        val (sw, sh) = screen()
        lp.x = lp.x.coerceIn(0, maxOf(0, sw - lp.width))
        lp.y = lp.y.coerceIn(0, maxOf(0, sh - lp.height))
        try { wm.updateViewLayout(iv, lp) } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------- Thanh mini

    private fun dropMini() {
        mini?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        mini = null
        miniLp = null
        miniShown = false
        refreshMini()
    }

    private fun ensureMini(): LinearLayout {
        mini?.let { return it }
        val look = Look(this, "f")
        val p = Ui.dp(this, 10)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p, p, p)
            background = Ui.rr(look.glassAt(maxOf(look.glass, 92)), 20, look.hair)
            elevation = Ui.dp(this@FloatingService, 6).toFloat()
            setOnClickListener { openFromMini() }
        }

        val ytRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val play = Ui.iconBtn(this, look, Ic.PAUSE, 40, true) { ytToggle() }
        ytRow.addView(play)
        val tc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = Ui.tv(this, look, "YouTube", 13.5f, true)
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        val time = Ui.tv(this, look, "", 12f, false, true)
        tc.addView(title)
        tc.addView(time)
        ytRow.addView(tc, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = Ui.dp(this@FloatingService, 10)
            marginEnd = Ui.dp(this@FloatingService, 8)
        })
        ytRow.addView(Ui.iconBtn(this, look, Ic.EXPAND, 34) { openFromMini() })
        val bar = ProgressLine(this, look.withAlpha(look.text, 16), look.accent)

        val rdRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rdRow.addView(Ui.iconBtn(this, look, Ic.STOP, 40, true) { speaker.stop() })
        val rd = Ui.tv(this, look, "", 13f, true)
        rd.maxLines = 2
        rd.ellipsize = TextUtils.TruncateAt.END
        rdRow.addView(rd, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = Ui.dp(this@FloatingService, 10)
        })

        root.addView(ytRow, LinearLayout.LayoutParams(-1, -2))
        root.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(this@FloatingService, 8) })
        root.addView(rdRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(this@FloatingService, 8) })

        val (sw, _) = screen()
        val w = minOf(Ui.dp(this, 290), (sw * 0.8f).toInt())
        val lp = WindowManager.LayoutParams(
            w, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or hardware(), PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        miniYtRow = ytRow
        miniRdRow = rdRow
        miniPlay = play
        miniTitle = title
        miniTime = time
        miniRd = rd
        miniBar = bar
        mini = root
        miniLp = lp
        wm.addView(root, lp)
        positionMini()
        return root
    }

    private fun positionMini() {
        val m = mini ?: return
        val lp = miniLp ?: return
        val b = bubbleLp ?: return
        val (sw, sh) = screen()
        val gap = Ui.dp(this, 8)
        var x = b.x + b.width + gap
        if (x + lp.width > sw - gap) x = b.x - lp.width - gap
        lp.x = x.coerceIn(gap, maxOf(gap, sw - lp.width - gap))
        lp.y = b.y.coerceIn(gap, maxOf(gap, sh - Ui.dp(this, 140)))
        try { wm.updateViewLayout(m, lp) } catch (_: Exception) {}
    }

    private fun openFromMini() {
        if (ytHas && !(speaker.speaking && !ytPlaying)) tab = 1 else tab = 0
        Prefs.putI(this, "last_tab", tab)
        showPanel()
    }

    private fun readStatusText(): String {
        val tot = if (speaker.total == -1) "∞" else speaker.total.toString()
        return "Đang đọc dòng ${curLine + 1}/${lines.size} · lần $pass/$tot"
    }

    private fun fmtTime(s: Double): String {
        val t = s.toInt().coerceAtLeast(0)
        val h = t / 3600
        val m = (t % 3600) / 60
        val x = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
    }

    private fun refreshMini() {
        val reading = speaker.speaking && curLine >= 0
        val show = panel == null && (ytHas || reading)
        if (!show) {
            mini?.visibility = View.GONE
            miniShown = false
            return
        }
        val m = ensureMini()
        miniYtRow?.visibility = if (ytHas) View.VISIBLE else View.GONE
        miniBar?.visibility = if (ytHas) View.VISIBLE else View.GONE
        miniRdRow?.visibility = if (reading) View.VISIBLE else View.GONE
        if (ytHas) {
            miniTitle?.text = ytTitle.ifBlank { "YouTube" }
            miniTime?.text = fmtTime(ytTime) + " / " + fmtTime(ytDur)
            miniBar?.set(if (ytDur > 0) (ytTime / ytDur).toFloat() else 0f)
            miniPlay?.iv?.set(if (ytPlaying) Ic.PAUSE else Ic.PLAY)
        }
        if (reading) miniRd?.text = readStatusText()
        if (!miniShown) {
            m.visibility = View.VISIBLE
            miniShown = true
            positionMini()
        }
    }

    // ---------------------------------------------------------------- Khung chính

    private fun showPanel() {
        if (panel != null) return
        bubble?.visibility = View.GONE
        mini?.visibility = View.GONE
        miniShown = false
        buildPanel()
    }

    private fun hidePanel() {
        if (customView != null) hideCustom()
        hostYt()   // chuyển WebView sang cửa sổ 1px TRƯỚC khi gỡ khung, để video không bị ngắt
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        statusTv = null
        listScroll = null
        settingsSv = null
        readEt = null
        ytHolder = null
        ytEt = null
        sugBox = null
        lineViews.clear()
        bubble?.visibility = View.VISIBLE
        refreshMini()
        Cloud.autoSave(this)
    }

    private fun onBack() {
        when {
            customView != null -> hideCustom()
            settingsOpen -> {
                settingsOpen = false
                buildPanel()
            }
            tab == 1 && yt?.canGoBack() == true -> yt?.goBack()
            else -> hidePanel()
        }
    }

    fun onPickDone() {
        ui.post { panel?.visibility = View.VISIBLE }
    }

    private fun pick(key: String) {
        panel?.visibility = View.GONE
        startActivity(
            Intent(this, PickActivity::class.java).putExtra("key", key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun parse(s: String): List<String> =
        s.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    private fun hideKeyboard(v: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    private fun detach(v: View) {
        (v.parent as? ViewGroup)?.removeView(v)
    }

    @Suppress("DEPRECATION")
    private fun insetsOf(ins: WindowInsets): IntArray =
        if (Build.VERSION.SDK_INT >= 30) {
            val i = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            intArrayOf(i.left, i.top, i.right, i.bottom)
        } else {
            intArrayOf(ins.stableInsetLeft, ins.stableInsetTop, ins.stableInsetRight, ins.stableInsetBottom)
        }

    private fun buildPanel() {
        settingsSv?.let { settingsY = it.scrollY }
        customView?.let { detach(it) }
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        lineViews.clear()
        settingsSv = null
        ytHolder = null
        sugBox = null

        val look = Look(this, "f")
        curLook = look
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val textSp = Prefs.int(this, "text_size", 18).toFloat()
        val margin = Ui.dp(this, 10)       // khoảng cách nhỏ từ khung tới rìa màn hình
        val pad = Ui.dp(this, 14)

        // Cửa sổ phủ kín màn hình, tự theo hướng xoay; khung kính nằm bên trong, cách rìa một đoạn nhỏ.
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(e: KeyEvent): Boolean {
                if (e.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (e.action == KeyEvent.ACTION_UP) onBack()
                    return true
                }
                return super.dispatchKeyEvent(e)
            }
        }
        root.setPadding(margin, margin, margin, margin)
        root.setOnApplyWindowInsetsListener { v, ins ->
            val i = insetsOf(ins)
            v.setPadding(margin + i[0], margin + i[1], margin + i[2], margin + i[3])
            ins
        }

        val card = FrameLayout(this)
        card.background = Ui.glass(this, look, 26)
        card.clipToOutline = true
        card.outlineProvider = ViewOutlineProvider.BACKGROUND
        Ui.bgImage(this, look)?.let { card.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        root.addView(card, FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        card.addView(col, FrameLayout.LayoutParams(-1, -1))

        // Thanh tiêu đề
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (settingsOpen) {
            head.addView(Ui.iconBtn(this, look, Ic.BACK) {
                settingsOpen = false
                buildPanel()
            })
            head.addView(Ui.tv(this, look, "Cài đặt", 19f, true),
                lp(0, -2, 1f).apply { marginStart = Ui.dp(this@FloatingService, 12) })
        } else {
            head.addView(Ui.segment(this, look, listOf(Ic.WAVE to "Đọc", Ic.VIDEO to "YouTube"), tab) {
                tab = it
                Prefs.putI(this, "last_tab", it)
                buildPanel()
            }, lp(0, -2, 1f))
            head.addView(Ui.iconBtn(this, look, Ic.SLIDERS) {
                settingsOpen = true
                settingsY = 0
                buildPanel()
            }, lp(-2, -2).apply { marginStart = Ui.dp(this@FloatingService, 8) })
        }
        head.addView(Ui.iconBtn(this, look, Ic.DOWN) { hidePanel() },
            lp(-2, -2).apply { marginStart = Ui.dp(this@FloatingService, 8) })
        col.addView(head)
        col.addView(Ui.gap(this, 12))

        ytHolder = null
        if (settingsOpen) {
            hostYt()
            val sv = ScrollView(this)
            sv.isVerticalScrollBarEnabled = false
            val mw = MaxWidthFrame(this, Ui.dp(this, 560))
            mw.addView(SettingsUi.build(this, look, "f", { key -> pick(key) }, { }), FrameLayout.LayoutParams(-1, -2))
            sv.addView(mw, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL))
            col.addView(sv, lp(-1, 0, 1f))
            settingsSv = sv
            sv.post { sv.scrollTo(0, settingsY) }
        } else if (tab == 0) {
            hostYt()
            buildRead(col, look, textSp, land)
        } else {
            buildYoutube(col, look)
        }

        customView?.let { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }

        val wl = WindowManager.LayoutParams(
            -1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            hardware(), PixelFormat.TRANSLUCENT
        )
        wl.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        if (Build.VERSION.SDK_INT >= 31) {
            wl.flags = wl.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            wl.blurBehindRadius = Ui.dp(this, 20)
        }
        wm.addView(root, wl)
        panel = root
    }

    // ---------------------------------------------------------------- Tab đọc tự động

    private fun buildRead(col: LinearLayout, look: Look, textSp: Float, land: Boolean) {
        val q = Ui.dp(this, 12)
        val et = EditText(this).apply {
            hint = "Nhập hoặc dán nội dung (mỗi dòng là một câu)…"
            setText(inputText)
            setTextColor(look.text)
            setHintTextColor(look.muted)
            textSize = textSp
            minLines = if (land) 2 else 3
            maxLines = if (land) 4 else 5
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(q, q, q, q)
            background = Ui.rr(look.tonal, 16)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    inputText = s?.toString() ?: ""
                    Prefs.putS(this@FloatingService, "content", inputText)
                }
            })
        }
        readEt = et

        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        controls.addView(et, lp(-1, -2))
        controls.addView(Ui.gap(this, 10))
        controls.addView(Ui.row2(this,
            Ui.btn(this, look, "Đưa lên", true, Ic.UPLOAD) {
                hideKeyboard(et)
                lines = parse(inputText)
                curLine = -1
                buildPanel()
            },
            Ui.btn(this, look, "Xóa", false, Ic.TRASH) {
                speaker.stop()
                inputText = ""
                Prefs.putS(this, "content", "")
                lines = emptyList()
                curLine = -1
                buildPanel()
            }))
        controls.addView(Ui.gap(this, 14))
        controls.addView(Ui.tv(this, look, "LẶP LẠI", 12f, true, true))
        controls.addView(Ui.gap(this, 6))
        controls.addView(Ui.chips(this, look, listOf("1 lần", "2 lần", "5 lần", "10 lần", "20 lần", "Vô hạn"), repeatIdx) {
            repeatIdx = it
            Prefs.putI(this, "repeat", it)
        })
        controls.addView(Ui.gap(this, 12))
        controls.addView(Ui.row2(this,
            Ui.btn(this, look, "Đọc", true, Ic.PLAY) {
                hideKeyboard(et)
                val fresh = parse(inputText)
                if (fresh.isNotEmpty() && fresh != lines) {
                    lines = fresh
                    buildPanel()
                }
                startRead(0)
            },
            Ui.btn(this, look, "Dừng", false, Ic.STOP) { speaker.stop() }))
        controls.addView(Ui.gap(this, 10))
        val st = Ui.tv(this, look, "", 13.5f, true)
        statusTv = st
        controls.addView(st)
        controls.addView(Ui.gap(this, 6))

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        listScroll = sv
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (lines.isEmpty()) {
            box.addView(Ui.tv(this, look, "Chưa có nội dung. Nhập chữ rồi bấm “Đưa lên”.", 14f, false, true))
        }
        lines.forEachIndexed { i, s ->
            val t = Ui.tv(this, look, s, textSp)
            t.setPadding(q, q / 2 + 2, q, q / 2 + 2)
            t.setOnClickListener {
                hideKeyboard(et)
                startRead(i)
            }
            lineViews.add(t)
            box.addView(t, lp(-1, -2).apply { bottomMargin = Ui.dp(this@FloatingService, 4) })
        }
        sv.addView(box)

        if (land) {
            // Ngang: điều khiển bên trái, danh sách dòng bên phải
            val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val left = ScrollView(this)
            left.isVerticalScrollBarEnabled = false
            left.addView(controls)
            body.addView(left, lp(0, -1, 1f).apply { marginEnd = Ui.dp(this@FloatingService, 12) })
            body.addView(sv, lp(0, -1, 1f))
            col.addView(body, lp(-1, 0, 1f))
        } else {
            col.addView(controls, lp(-1, -2))
            col.addView(sv, lp(-1, 0, 1f))
        }
        refreshStatus()
        highlight()
    }

    private fun startRead(from: Int) {
        if (lines.isEmpty()) {
            Toast.makeText(this, "Chưa có nội dung để đọc", Toast.LENGTH_SHORT).show()
            return
        }
        if (!speaker.langOk) {
            Toast.makeText(
                this, "Máy chưa có giọng tiếng Việt. Hãy cài trong Cài đặt > Chuyển văn bản thành giọng nói.",
                Toast.LENGTH_LONG
            ).show()
        }
        speaker.start(lines, from, repeats[repeatIdx], Prefs.int(this, "speed", 100) / 100f)
    }

    private fun refreshStatus() {
        val tv = statusTv ?: return
        tv.text = when {
            speaker.speaking && curLine >= 0 -> readStatusText()
            lines.isNotEmpty() -> "Sẵn sàng · ${lines.size} dòng (chạm vào 1 dòng để đọc từ đó)"
            else -> ""
        }
    }

    private fun highlight() {
        val look = curLook ?: return
        lineViews.forEachIndexed { i, t ->
            val on = i == curLine && speaker.speaking
            t.background = if (on) Ui.rr(look.withAlpha(look.accent, 16), 12, look.withAlpha(look.accent, 60)) else null
            t.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
        }
        if (speaker.speaking && curLine in lineViews.indices) {
            val v = lineViews[curLine]
            listScroll?.post {
                listScroll?.smoothScrollTo(0, (v.top - Ui.dp(this, 40)).coerceAtLeast(0))
            }
        }
    }

    // ---------------------------------------------------------------- Tab YouTube

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun ensureYt(look: Look): WebView {
        yt?.let { return it }
        val w = WebView(this)
        w.setBackgroundColor(look.base)
        val s = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.userAgentString = s.userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(w, true)
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url?.toString() ?: return true
                return !(u.startsWith("http://") || u.startsWith("https://"))
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                ytBack?.alpha = if (view?.canGoBack() == true) 1f else 0.35f
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, p: Int) {
                ytProgress?.let {
                    it.set(p / 100f)
                    it.visibility = if (p >= 100) View.GONE else View.VISIBLE
                }
            }

            override fun onShowCustomView(view: View?, cb: WebChromeClient.CustomViewCallback?) {
                val pn = panel
                if (view == null || pn == null) {
                    cb?.onCustomViewHidden()
                    return
                }
                customView?.let { detach(it) }
                customCb = cb
                customView = view
                view.setBackgroundColor(Color.BLACK)
                pn.addView(view, FrameLayout.LayoutParams(-1, -1))
            }

            override fun onHideCustomView() {
                hideCustom()
            }
        }
        w.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                sugBox?.visibility = View.GONE
                ytEt?.let { hideKeyboard(it) }
            }
            false
        }
        w.loadUrl(HOME)
        yt = w
        ui.removeCallbacks(pollRun)
        ui.postDelayed(pollRun, 1000)
        return w
    }

    private fun hideCustom() {
        customView?.let { detach(it) }
        customView = null
        customCb?.onCustomViewHidden()
        customCb = null
    }

    /** Đưa WebView sang cửa sổ 1px (vẫn hiển thị, vẫn phát tiếp) khi khung bị thu nhỏ hoặc đang ở tab khác. */
    private fun hostYt() {
        val w = yt ?: return
        if (host != null && w.parent === host) return
        if (w.width > 0 && w.height > 0) {
            lastW = w.width
            lastH = w.height
        }
        detach(w)
        val h = host ?: FrameLayout(this).also {
            it.alpha = 0.01f
            val wl = WindowManager.LayoutParams(
                1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or hardware(),
                PixelFormat.TRANSLUCENT
            )
            wl.gravity = Gravity.TOP or Gravity.START
            wm.addView(it, wl)
            host = it
        }
        h.removeAllViews()
        val (sw, sh) = screen()
        h.addView(w, FrameLayout.LayoutParams(if (lastW > 0) lastW else sw, if (lastH > 0) lastH else sh / 2))
        w.onResume()
        w.resumeTimers()
    }

    private fun releaseHost() {
        host?.let {
            it.removeAllViews()
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        host = null
    }

    private fun buildYoutube(col: LinearLayout, look: Look) {
        val w = ensureYt(look)
        detach(w)
        releaseHost()

        // Hàng tìm kiếm
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = Ui.iconBtn(this, look, Ic.BACK, 42) { if (yt?.canGoBack() == true) yt?.goBack() }
        back.alpha = if (w.canGoBack()) 1f else 0.35f
        ytBack = back
        row.addView(back)

        val field = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@FloatingService, 14), 0, Ui.dp(this@FloatingService, 6), 0)
            background = Ui.rr(look.tonal, 21)
        }
        field.addView(Ui.icon(this, Ic.SEARCH, look.muted, 18))
        val et = EditText(this).apply {
            hint = "Tìm kiếm trên YouTube"
            setText(ytQuery)
            setTextColor(look.text)
            setHintTextColor(look.muted)
            textSize = 15f
            setSingleLine(true)
            background = null
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(Ui.dp(this@FloatingService, 10), 0, Ui.dp(this@FloatingService, 6), 0)
        }
        ytEt = et
        field.addView(et, lp(0, Ui.dp(this, 42), 1f))
        val clear = Ui.icon(this, Ic.CLOSE, look.muted, 18)
        clear.setPadding(Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3))
        clear.visibility = if (ytQuery.isEmpty()) View.GONE else View.VISIBLE
        clear.setOnClickListener {
            et.setText("")
            et.requestFocus()
        }
        field.addView(clear)
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                ytQuery = s?.toString() ?: ""
                clear.visibility = if (ytQuery.isEmpty()) View.GONE else View.VISIBLE
                if (et.hasFocus()) scheduleSuggest(ytQuery)
            }
        })
        et.setOnEditorActionListener { v, id, ev ->
            if (id == EditorInfo.IME_ACTION_SEARCH ||
                (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)
            ) {
                hideKeyboard(v)
                ytSearch(v.text.toString())
                true
            } else false
        }
        row.addView(field, lp(0, Ui.dp(this, 42), 1f).apply {
            marginStart = Ui.dp(this@FloatingService, 8)
            marginEnd = Ui.dp(this@FloatingService, 8)
        })
        row.addView(Ui.iconBtn(this, look, Ic.HOME, 42) {
            ytQuery = ""
            et.setText("")
            hideKeyboard(et)
            sugBox?.visibility = View.GONE
            yt?.loadUrl(HOME)
        })
        col.addView(row)
        col.addView(Ui.gap(this, 10))

        // Vùng hiển thị YouTube: WebView + thanh tiến độ + danh sách gợi ý (nổi phía trên)
        val holder = FrameLayout(this)
        holder.clipToOutline = true
        holder.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, Ui.dp(this@FloatingService, 18).toFloat())
            }
        }
        holder.addView(w, FrameLayout.LayoutParams(-1, -1))
        val pg = ProgressLine(this, 0, look.accent)
        pg.visibility = View.GONE
        ytProgress = pg
        holder.addView(pg, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        val sb = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rr(look.glassAt(98), 16, look.hair)
            visibility = View.GONE
            elevation = Ui.dp(this@FloatingService, 4).toFloat()
        }
        sugBox = sb
        holder.addView(sb, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            topMargin = Ui.dp(this@FloatingService, 2)
        })
        ytHolder = holder
        col.addView(holder, lp(-1, 0, 1f))
        w.onResume()
    }

    private fun ytSearch(q: String) {
        val s = q.trim()
        if (s.isEmpty()) return
        val w = yt ?: return
        val url = when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            s.contains("youtu.be/") || s.contains("youtube.com/") -> "https://$s"
            else -> "$HOME/results?search_query=" + URLEncoder.encode(s, "UTF-8")
        }
        ytQuery = s
        sugBox?.visibility = View.GONE
        sugToken++
        w.loadUrl(url)
    }

    /** Gợi ý tìm kiếm theo thời gian thực khi đang gõ. */
    private fun scheduleSuggest(q: String) {
        sugRun?.let { ui.removeCallbacks(it) }
        if (q.isBlank()) {
            sugToken++
            sugBox?.visibility = View.GONE
            return
        }
        val r = Runnable { fetchSuggest(q.trim()) }
        sugRun = r
        ui.postDelayed(r, 180)
    }

    private fun fetchSuggest(q: String) {
        val tk = ++sugToken
        Thread {
            val out = ArrayList<String>()
            try {
                val u = URL("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=vi&q=" +
                    URLEncoder.encode(q, "UTF-8"))
                val cn = u.openConnection() as HttpURLConnection
                cn.connectTimeout = 4000
                cn.readTimeout = 4000
                val txt = cn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(txt).getJSONArray(1)
                for (i in 0 until minOf(arr.length(), 7)) out.add(arr.getString(i))
            } catch (_: Exception) {
            }
            ui.post { if (tk == sugToken) showSuggest(out) }
        }.start()
    }

    private fun showSuggest(list: List<String>) {
        val box = sugBox ?: return
        val look = curLook ?: return
        box.removeAllViews()
        if (list.isEmpty() || ytEt?.hasFocus() != true) {
            box.visibility = View.GONE
            return
        }
        list.forEach { s ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(this@FloatingService, 14), Ui.dp(this@FloatingService, 12),
                    Ui.dp(this@FloatingService, 14), Ui.dp(this@FloatingService, 12))
            }
            r.addView(Ui.icon(this, Ic.SEARCH, look.muted, 16))
            val t = Ui.tv(this, look, s, 15f)
            t.maxLines = 1
            t.ellipsize = TextUtils.TruncateAt.END
            r.addView(t, lp(0, -2, 1f).apply { marginStart = Ui.dp(this@FloatingService, 12) })
            r.setOnClickListener {
                ytEt?.let { e ->
                    hideKeyboard(e)
                    e.setText(s)
                    e.clearFocus()
                }
                ytSearch(s)
            }
            box.addView(r)
        }
        box.visibility = View.VISIBLE
    }

    private fun ytToggle() {
        yt?.evaluateJavascript(TOGGLE_JS, null)
        ytPlaying = !ytPlaying
        refreshMini()
    }

    /** Đọc trạng thái video mỗi giây: tên, thời gian, đang phát hay tạm dừng. */
    private fun pollYt() {
        val w = yt ?: return
        val url = w.url ?: ""
        if (!(url.contains("/watch") || url.contains("/shorts"))) {
            if (ytHas) {
                ytHas = false
                refreshMini()
            }
            return
        }
        w.evaluateJavascript(POLL_JS) { raw ->
            val s = try { JSONTokener(raw ?: "").nextValue() as? String } catch (_: Exception) { null } ?: ""
            if (s.isEmpty()) {
                ytHas = false
            } else {
                try {
                    val o = JSONObject(s)
                    ytHas = true
                    ytPlaying = o.optBoolean("p")
                    ytTime = o.optDouble("t", 0.0).let { if (it.isNaN() || it.isInfinite()) 0.0 else it }
                    ytDur = o.optDouble("d", 0.0).let { if (it.isNaN() || it.isInfinite()) 0.0 else it }
                    ytTitle = o.optString("n")
                } catch (_: Exception) {
                    ytHas = false
                }
            }
            refreshMini()
        }
    }
}
