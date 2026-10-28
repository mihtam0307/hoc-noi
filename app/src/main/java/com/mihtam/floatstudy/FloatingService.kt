package com.mihtam.floatstudy

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
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
        private const val PLAY_JS =
            "(function(){var v=document.querySelector('video');if(v&&v.paused&&!v.ended){var p=v.play();if(p&&p.catch)p.catch(function(){})}})()"
        /** Chặn trang biết mình đang bị ẩn (Page Visibility) để YouTube không tự dừng khi thu nhỏ / phóng to khung. */
        private const val KEEP_JS =
            "(function(){try{if(window.__hnKeep)return;window.__hnKeep=1;" +
            "var f=function(o,k,v){try{Object.defineProperty(o,k,{get:function(){return v},configurable:true})}catch(e){}};" +
            "f(Document.prototype,'hidden',false);f(Document.prototype,'webkitHidden',false);" +
            "f(Document.prototype,'visibilityState','visible');f(Document.prototype,'webkitVisibilityState','visible');" +
            "var s=function(e){e.stopImmediatePropagation()};" +
            "window.addEventListener('visibilitychange',s,true);document.addEventListener('visibilitychange',s,true);" +
            "window.addEventListener('webkitvisibilitychange',s,true);document.addEventListener('webkitvisibilitychange',s,true);" +
            "}catch(e){}})()"
    }

    private class AppEntry(val pkg: String, val label: String, val icon: Drawable)

    private lateinit var wm: WindowManager
    private lateinit var speaker: Speaker
    private val ui = Handler(Looper.getMainLooper())

    // Cửa sổ nổi
    private var bubble: FrameLayout? = null
    private var bubbleImg: ImageView? = null
    private var eq: EqView? = null
    private var menu: LinearLayout? = null
    private var menuClosedAt = 0L
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: FrameLayout? = null
    private var host: FrameLayout? = null          // cửa sổ 1px giữ WebView sống khi khung bị thu nhỏ

    // Thanh vuốt ở rìa màn hình (khi xoay ngang)
    private var handle: LinearLayout? = null
    private var handlePill: View? = null
    private var handleLp: WindowManager.LayoutParams? = null
    private var hPlay: FrameLayout? = null
    private var hStop: FrameLayout? = null
    private var hSig = -1
    private var slideIn = false

    // Thanh ứng dụng cạnh khung + nút phát/dừng trên tiêu đề (khi xoay ngang)
    private var dockBox: LinearLayout? = null
    private var dockPop: FrameLayout? = null
    private var dockPlay: IconBtn? = null
    private var dockStop: IconBtn? = null
    private var pickerOpen = false
    private var cardView: View? = null
    private var hintedFreeform = false
    private var appCache: List<AppEntry>? = null


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
    private var ytWant = false          // đã bấm phát: giữ phát đều khi thu nhỏ / phóng to, chỉ dừng khi người dùng dừng
    private var ytMoveAt = 0L
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
            if (k == "handle_side") {
                applyHandle()
                if (panel != null) buildPanel()
            }
            if (panel == null && k == "content") {
                inputText = Prefs.str(this, "content") ?: ""
                lines = parse(inputText)
            }
            if (panel == null && k == "repeat") repeatIdx = Prefs.int(this, "repeat", 0).coerceIn(0, 5)
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
        speaker.onLine = { i, p -> curLine = i; pass = p; refreshStatus(); highlight(); refreshActivity() }
        speaker.onEnd = { curLine = -1; refreshStatus(); highlight(); refreshActivity() }

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "App nổi", NotificationManager.IMPORTANCE_LOW))
        val n = notif()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        showBubble()
        showHandle()
        refreshLaunchers()
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
            dismissMenu()
            clampBubble()
            applyHandle()
            refreshLaunchers()
            if (panel != null) buildPanel()
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
        menu?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        handle?.let { try { wm.removeView(it) } catch (_: Exception) {} }
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
            .setContentText("Dọc: chạm hình nổi. Ngang: vuốt thanh ở rìa màn hình để mở khung.")
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
        val size = Ui.dp(this, Prefs.int(this, "bubble_size", 64))
        val root = FrameLayout(this)
        root.clipToOutline = true
        root.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setOval(0, 0, v.width, v.height)
            }
        }
        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        root.addView(img, FrameLayout.LayoutParams(-1, -1))
        val eqv = EqView(this)
        eqv.visibility = View.GONE
        root.addView(eqv, FrameLayout.LayoutParams(-1, -1))
        bubbleImg = img
        eq = eqv

        val lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = Ui.dp(this, 8)
        lp.y = Ui.dp(this, 220)
        bubble = root
        bubbleLp = lp

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        root.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startX = lp.x; startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX).toInt()
                    val dy = (ev.rawY - downY).toInt()
                    if (abs(dx) > Ui.dp(this, 6) || abs(dy) > Ui.dp(this, 6)) moved = true
                    if (moved) {
                        dismissMenu()
                        val (sw, sh) = screen()
                        lp.x = (startX + dx).coerceIn(0, maxOf(0, sw - lp.width))
                        lp.y = (startY + dy).coerceIn(0, maxOf(0, sh - lp.height))
                        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleMenu()
                    true
                }
                else -> false
            }
        }
        wm.addView(root, lp)
        applyBubble()
    }

    private fun applyBubble() {
        val root = bubble ?: return
        val img = bubbleImg ?: return
        val lp = bubbleLp ?: return
        val size = Ui.dp(this, Prefs.int(this, "bubble_size", 64))
        lp.width = size
        lp.height = size
        root.alpha = Prefs.int(this, "bubble_alpha", 90) / 100f
        val bmp = Img.load(Prefs.str(this, "bubble_img"), 512)
        if (bmp != null) img.setImageBitmap(bmp) else img.setImageResource(R.drawable.ic_launcher)
        try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
    }

    private fun clampBubble() {
        val iv = bubble ?: return
        val lp = bubbleLp ?: return
        val (sw, sh) = screen()
        lp.x = lp.x.coerceIn(0, maxOf(0, sw - lp.width))
        lp.y = lp.y.coerceIn(0, maxOf(0, sh - lp.height))
        try { wm.updateViewLayout(iv, lp) } catch (_: Exception) {}
    }

    private fun isLand(): Boolean {
        val (w, h) = screen()
        return w > h
    }

    private fun handleSide(): Int = Prefs.int(this, "handle_side", 0).coerceIn(0, 1)

    /** Ẩn / hiện 1 cửa sổ nổi; khi ẩn thì không chặn cảm ứng của app bên dưới. */
    private fun setShown(v: View?, lp: WindowManager.LayoutParams?, show: Boolean) {
        if (v == null || lp == null) return
        v.visibility = if (show) View.VISIBLE else View.GONE
        lp.flags = if (show) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
    }

    /** Dọc: hiện hình nổi. Ngang: hiện thanh vuốt ở rìa. Khi khung đang mở thì ẩn cả hai. */
    private fun refreshLaunchers() {
        val land = isLand()
        val open = panel != null
        setShown(bubble, bubbleLp, !open && !land)
        setShown(handle, handleLp, !open && land)
        if (land || open) dismissMenu()
    }

    private fun handleBtn(ic: Ic, onClick: () -> Unit): FrameLayout {
        val f = FrameLayout(this)
        f.background = Ui.rr(0xE61B1F2E.toInt(), 14, 0x33FFFFFF, 1)
        val iv = IconView(this, ic, Color.WHITE)
        f.addView(iv, FrameLayout.LayoutParams(Ui.dp(this, 14), Ui.dp(this, 14), Gravity.CENTER))
        f.tag = iv
        f.isClickable = true
        f.setOnClickListener { onClick() }
        return f
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showHandle() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val pill = View(this)
        pill.background = Ui.rr(0xE6FFFFFF.toInt(), 2, 0x66000000, 1)
        root.addView(pill, LinearLayout.LayoutParams(Ui.dp(this, 4), Ui.dp(this, 36)).apply {
            topMargin = Ui.dp(this@FloatingService, 12)
            bottomMargin = Ui.dp(this@FloatingService, 12)
        })
        handlePill = pill
        val play = handleBtn(Ic.PLAY) { ytToggle() }
        val stop = handleBtn(Ic.STOP) { speaker.stop() }
        play.visibility = View.GONE
        stop.visibility = View.GONE
        root.addView(play, LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)).apply {
            bottomMargin = Ui.dp(this@FloatingService, 6)
        })
        root.addView(stop, LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)).apply {
            bottomMargin = Ui.dp(this@FloatingService, 6)
        })
        hPlay = play
        hStop = stop

        val wlp = WindowManager.LayoutParams(
            Ui.dp(this, 30), Ui.dp(this, 60), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        wlp.gravity = Gravity.TOP or Gravity.START
        root.visibility = View.GONE
        handle = root
        handleLp = wlp

        var downX = 0f
        var downY = 0f
        var startY = 0
        var mode = 0   // 0 = chưa rõ (chạm), 1 = đang kéo dọc để dời thanh, 2 = đã vuốt vào để mở khung
        root.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    startY = wlp.y
                    mode = 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    val inward = if (handleSide() == 0) dx else -dx
                    val th = Ui.dp(this, 10)
                    if (mode == 0) {
                        if (inward > th && inward > abs(dy)) {
                            mode = 2
                            openFromHandle()
                        } else if (abs(dy) > th && abs(dy) > abs(dx)) {
                            mode = 1
                        }
                    }
                    if (mode == 1) {
                        val (_, sh) = screen()
                        wlp.y = (startY + dy.toInt()).coerceIn(0, maxOf(0, sh - wlp.height))
                        try { wm.updateViewLayout(root, wlp) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (mode == 0) openFromHandle()
                    if (mode == 1) saveHandleY()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (mode == 1) saveHandleY()
                    true
                }
                else -> false
            }
        }
        wm.addView(root, wlp)
        refreshHandle()
    }

    private fun saveHandleY() {
        val wlp = handleLp ?: return
        val (_, sh) = screen()
        val range = maxOf(1, sh - wlp.height)
        Prefs.putI(this, "handle_y", (wlp.y * 100 / range).coerceIn(0, 100))
    }

    private fun applyHandle() {
        val root = handle ?: return
        val wlp = handleLp ?: return
        val side = handleSide()
        val (_, sh) = screen()
        wlp.gravity = Gravity.TOP or (if (side == 0) Gravity.START else Gravity.END)
        wlp.x = 0
        val range = maxOf(0, sh - wlp.height)
        wlp.y = range * Prefs.int(this, "handle_y", 40).coerceIn(0, 100) / 100
        val g = if (side == 0) Gravity.START else Gravity.END
        for (i in 0 until root.childCount) {
            val lp = root.getChildAt(i).layoutParams as LinearLayout.LayoutParams
            lp.gravity = g
            lp.leftMargin = if (side == 0) Ui.dp(this, 2) else 0
            lp.rightMargin = if (side == 0) 0 else Ui.dp(this, 2)
        }
        root.requestLayout()
        try { wm.updateViewLayout(root, wlp) } catch (_: Exception) {}
    }

    /** Thanh vuốt hiện thêm nút phát/dừng video và dừng đọc khi đang có video / đang đọc. */
    private fun refreshHandle() {
        val play = hPlay ?: return
        val stop = hStop ?: return
        val wlp = handleLp ?: return
        val showPlay = ytHas
        val showStop = speaker.speaking
        val sig = (if (showPlay) 1 else 0) or (if (showStop) 2 else 0) or (if (ytPlaying) 4 else 0)
        if (sig == hSig) return
        hSig = sig
        play.visibility = if (showPlay) View.VISIBLE else View.GONE
        (play.tag as? IconView)?.set(if (ytPlaying) Ic.PAUSE else Ic.PLAY)
        stop.visibility = if (showStop) View.VISIBLE else View.GONE
        val n = (if (showPlay) 1 else 0) + (if (showStop) 1 else 0)
        wlp.height = Ui.dp(this, 60 + 34 * n)
        applyHandle()
    }

    private fun openFromHandle() {
        if (panel != null) return
        slideIn = true
        settingsOpen = false
        pickerOpen = false
        showPanel()
    }

    // ---------------------------------------------------------------- Hiệu ứng thanh nhạc + menu nhỏ

    private fun readStatusText(): String {
        val tot = if (speaker.total == -1) "∞" else speaker.total.toString()
        return "Đang đọc dòng ${curLine + 1}/${lines.size} · lần $pass/$tot"
    }

    /** Hình nổi hiện các thanh nhạc chuyển động khi đang phát video hoặc đang đọc. */
    private fun refreshActivity() {
        val active = panel == null && ((ytHas && ytPlaying) || speaker.speaking)
        eq?.setActive(active)
        syncMedia()
        refreshHandle()
    }

    /** Cập nhật nút phát/dừng trên tiêu đề khung (màn ngang). */
    private fun syncMedia() {
        dockPlay?.let {
            it.visibility = if (ytHas) View.VISIBLE else View.GONE
            it.iv.set(if (ytPlaying) Ic.PAUSE else Ic.PLAY)
        }
        dockStop?.visibility = if (speaker.speaking) View.VISIBLE else View.GONE
    }

    private fun addMediaBtns(head: LinearLayout, look: Look) {
        val play = Ui.iconBtn(this, look, if (ytPlaying) Ic.PAUSE else Ic.PLAY, 40, true) { ytToggle() }
        val stop = Ui.iconBtn(this, look, Ic.STOP, 40) { speaker.stop() }
        (play.layoutParams as LinearLayout.LayoutParams).marginStart = Ui.dp(this, 8)
        (stop.layoutParams as LinearLayout.LayoutParams).marginStart = Ui.dp(this, 8)
        dockPlay = play
        dockStop = stop
        head.addView(play)
        head.addView(stop)
        syncMedia()
    }

    private class MenuItem(val ic: Ic, val label: String, val danger: Boolean, val act: () -> Unit)

    private fun dismissMenu() {
        menu?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        if (menu != null) menuClosedAt = SystemClock.uptimeMillis()
        menu = null
    }

    private fun toggleMenu() {
        if (menu != null) {
            dismissMenu()
        } else if (SystemClock.uptimeMillis() - menuClosedAt > 350) {
            showMenu()
        }
    }

    private fun openPanel(t: Int, settings: Boolean) {
        tab = t
        Prefs.putI(this, "last_tab", t)
        settingsOpen = settings
        settingsY = 0
        showPanel()
    }

    private fun showMenu() {
        val b = bubbleLp ?: return
        dismissMenu()
        val look = Look(this, "f")
        val items = ArrayList<MenuItem>()
        if (ytHas) {
            items.add(MenuItem(if (ytPlaying) Ic.PAUSE else Ic.PLAY,
                if (ytPlaying) "Tạm dừng video" else "Phát video", false) { ytToggle() })
        }
        if (speaker.speaking) items.add(MenuItem(Ic.STOP, "Dừng đọc", false) { speaker.stop() })
        items.add(MenuItem(Ic.WAVE, "Đọc tự động", false) { openPanel(0, false) })
        items.add(MenuItem(Ic.VIDEO, "YouTube", false) { openPanel(1, false) })
        items.add(MenuItem(Ic.SLIDERS, "Cài đặt", false) { openPanel(tab, true) })
        items.add(MenuItem(Ic.CLOSE, "Tắt app nổi", true) { stopSelf() })

        val pad = Ui.dp(this, 6)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = Ui.rr(look.glassAt(maxOf(look.glass, 94)), 20, look.hair)
            elevation = Ui.dp(this@FloatingService, 8).toFloat()
        }
        val red = 0xFFD93025.toInt()
        items.forEach { it0 ->
            val col = if (it0.danger) red else look.text
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(this@FloatingService, 12), Ui.dp(this@FloatingService, 13),
                    Ui.dp(this@FloatingService, 14), Ui.dp(this@FloatingService, 13))
                background = RippleDrawable(
                    ColorStateList.valueOf(look.withAlpha(look.text, 14)), null, Ui.rr(Color.WHITE, 14)
                )
                isClickable = true
                setOnClickListener {
                    dismissMenu()
                    it0.act()
                }
            }
            val ic = Ui.icon(this, it0.ic, if (it0.danger) red else look.accent, 20)
            (ic.layoutParams as LinearLayout.LayoutParams).marginEnd = Ui.dp(this, 14)
            row.addView(ic)
            val t = Ui.tv(this, look, it0.label, 15f, true)
            t.setTextColor(col)
            row.addView(t)
            root.addView(row, lp(-1, -2))
        }

        val w = Ui.dp(this, 232)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val h = root.measuredHeight
        val (sw, sh) = screen()
        val gap = Ui.dp(this, 8)
        val right = b.x + b.width + gap + w <= sw - gap
        var x = if (right) b.x + b.width + gap else b.x - w - gap
        x = x.coerceIn(gap, maxOf(gap, sw - w - gap))
        val y = minOf(b.y, sh - h - gap).coerceAtLeast(gap)

        val lpm = WindowManager.LayoutParams(
            w, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or hardware(),
            PixelFormat.TRANSLUCENT
        )
        lpm.gravity = Gravity.TOP or Gravity.START
        lpm.x = x
        lpm.y = y
        root.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_OUTSIDE) {
                dismissMenu()
                true
            } else false
        }
        root.alpha = 0f
        root.scaleX = 0.92f
        root.scaleY = 0.92f
        root.pivotX = if (right) 0f else w.toFloat()
        root.pivotY = 0f
        wm.addView(root, lpm)
        menu = root
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start()
    }

    // ---------------------------------------------------------------- Khung chính

    private fun showPanel() {
        if (panel != null) return
        dismissMenu()
        eq?.setActive(false)
        buildPanel()
        refreshLaunchers()
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
        dockBox = null
        dockPop = null
        dockPlay = null
        dockStop = null
        pickerOpen = false
        refreshLaunchers()
        refreshActivity()
        Cloud.autoSave(this)
    }

    private fun onBack() {
        when {
            dockPop != null -> dismissDockPop()
            customView != null -> hideCustom()
            pickerOpen -> {
                pickerOpen = false
                buildPanel()
            }
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
        dockBox = null
        dockPop = null
        dockPlay = null
        dockStop = null

        val look = Look(this, "f")
        curLook = look
        val land = isLand()
        if (!land) pickerOpen = false
        val textSp = Prefs.int(this, "text_size", 18).toFloat()
        val margin = Ui.dp(this, 10)       // khoảng cách nhỏ từ khung tới rìa màn hình
        val pad = Ui.dp(this, 14)
        val side = handleSide()

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
        cardView = card

        if (land) {
            // Ngang: ngăn kéo ra từ rìa = thanh dọc (tab + ứng dụng) bên trái + hình chữ nhật gần nửa màn hình
            root.setBackgroundColor(0x33000000)
            root.setOnClickListener { hidePanel() }      // chạm ra ngoài thì thu lại
            val (sw, _) = screen()
            val gap = Ui.dp(this, 8)
            val stripW = Ui.dp(this, 66)
            val total = minOf((sw * 0.52f).toInt() + gap + stripW, sw - margin * 2)
            val drawer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isClickable = true                       // chạm trong ngăn kéo không bị tính là chạm ra ngoài
            }
            drawer.addView(buildDock(look), lp(stripW, -1))
            drawer.addView(View(this), lp(gap, 1))
            drawer.addView(card, lp(0, -1, 1f))
            root.addView(drawer, FrameLayout.LayoutParams(total, -1, if (side == 0) Gravity.START else Gravity.END))
            if (slideIn) {
                slideIn = false
                drawer.alpha = 0f
                drawer.translationX = (if (side == 0) -1 else 1) * Ui.dp(this, 140).toFloat()
                drawer.post { drawer.animate().alpha(1f).translationX(0f).setDuration(190).start() }
            }
        } else {
            root.addView(card, FrameLayout.LayoutParams(-1, -1))
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        card.addView(col, FrameLayout.LayoutParams(-1, -1))

        if (land && pickerOpen) {
            hostYt()
            buildPicker(col, look)
        } else {
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
            } else if (land) {
                head.addView(Ui.tv(this, look, if (tab == 0) "Đọc tự động" else "YouTube", 19f, true),
                    lp(0, -2, 1f).apply { marginStart = Ui.dp(this@FloatingService, 4) })
            } else {
                head.addView(Ui.segment(this, look, listOf(Ic.WAVE to "Đọc", Ic.VIDEO to "YouTube"), tab) {
                    tab = it
                    Prefs.putI(this, "last_tab", it)
                    buildPanel()
                }, lp(0, -2, 1f))
            }
            if (land) addMediaBtns(head, look)
            if (!settingsOpen) {
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

    // ---------------------------------------------------------------- Thanh dọc (màn ngang): tab + ứng dụng

    private fun dockApps(): List<String> {
        val raw = Prefs.str(this, "dock_apps")
        if (raw == null) {
            // Lần đầu: sẵn Google và TikTok (máy nào có cài thì hiện)
            return listOf(
                "com.google.android.googlequicksearchbox",
                "com.ss.android.ugc.trill",
                "com.zhiliaoapp.musically"
            )
        }
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveDock(l: List<String>) {
        Prefs.putS(this, "dock_apps", JSONArray(l).toString())
    }

    private fun dockTab(look: Look, ic: Ic, label: String, on: Boolean, onClick: () -> Unit): LinearLayout {
        val col = if (on) look.onAccent else look.muted
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@FloatingService, 6), 0, Ui.dp(this@FloatingService, 5))
            background = if (on) Ui.rr(look.accent, 14) else RippleDrawable(
                ColorStateList.valueOf(look.withAlpha(look.text, 14)), null, Ui.rr(Color.WHITE, 14)
            )
            isClickable = true
            setOnClickListener { onClick() }
        }
        v.addView(Ui.icon(this, ic, col, 20))
        val t = Ui.tv(this, look, label, 9.5f, true)
        t.setTextColor(col)
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        v.addView(t, lp(-2, -2).apply { topMargin = Ui.dp(this@FloatingService, 2) })
        return v
    }

    private fun buildDock(look: Look): LinearLayout {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(this@FloatingService, 6), Ui.dp(this@FloatingService, 10),
                Ui.dp(this@FloatingService, 6), Ui.dp(this@FloatingService, 10))
            background = Ui.glass(this@FloatingService, look, 28)
        }
        val idle = !settingsOpen && !pickerOpen
        dock.addView(dockTab(look, Ic.WAVE, "Đọc", idle && tab == 0) {
            tab = 0
            Prefs.putI(this, "last_tab", 0)
            settingsOpen = false
            pickerOpen = false
            buildPanel()
        }, lp(-1, -2).apply { bottomMargin = Ui.dp(this@FloatingService, 4) })
        dock.addView(dockTab(look, Ic.VIDEO, "YouTube", idle && tab == 1) {
            tab = 1
            Prefs.putI(this, "last_tab", 1)
            settingsOpen = false
            pickerOpen = false
            buildPanel()
        }, lp(-1, -2))

        val div = View(this)
        div.setBackgroundColor(look.hair)
        dock.addView(div, lp(-1, Ui.dp(this, 1)).apply {
            topMargin = Ui.dp(this@FloatingService, 8)
            bottomMargin = Ui.dp(this@FloatingService, 8)
            leftMargin = Ui.dp(this@FloatingService, 6)
            rightMargin = Ui.dp(this@FloatingService, 6)
        })

        val plus = Ui.iconBtn(this, look, Ic.PLUS, 42, true) {
            appCache = null
            pickerOpen = true
            settingsOpen = false
            buildPanel()
        }
        (plus.layoutParams as LinearLayout.LayoutParams).bottomMargin = Ui.dp(this, 8)
        dock.addView(plus)

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        sv.addView(box, FrameLayout.LayoutParams(-1, -2))
        dock.addView(sv, lp(-1, 0, 1f))
        dockBox = box
        fillDock(look)
        return dock
    }

    private fun fillDock(look: Look) {
        val box = dockBox ?: return
        box.removeAllViews()
        val pm = packageManager
        for (pkg in dockApps()) {
            val icon = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { continue }
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
            val cell = FrameLayout(this)
            cell.background = RippleDrawable(
                ColorStateList.valueOf(look.withAlpha(look.text, 18)), null, Ui.rr(Color.WHITE, 14)
            )
            val iv = ImageView(this)
            iv.setImageDrawable(icon)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.clipToOutline = true
            iv.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, Ui.dp(this@FloatingService, 10).toFloat())
                }
            }
            cell.addView(iv, FrameLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40), Gravity.CENTER))
            cell.isClickable = true
            cell.setOnClickListener { launchApp(pkg) }
            cell.setOnLongClickListener {
                showDockPop(cell, pkg, label)
                true
            }
            box.addView(cell, lp(Ui.dp(this, 50), Ui.dp(this, 50)).apply {
                bottomMargin = Ui.dp(this@FloatingService, 6)
            })
        }
    }

    private fun freeformOn(): Boolean {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) return true
        return try {
            Settings.Global.getInt(contentResolver, "enable_freeform_support", 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    /** Tùy chọn mở ứng dụng dạng cửa sổ nhỏ (freeform) đúng vùng [r]. */
    private fun miniOptions(r: Rect): Bundle? = try {
        val o = ActivityOptions.makeBasic()
        o.setLaunchBounds(r)
        try {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(o, 5)
        } catch (_: Throwable) {
        }
        o.toBundle()
    } catch (_: Throwable) {
        null
    }

    /**
     * Mở ứng dụng: ở màn ngang thì cố mở dạng cửa sổ mini ngay vị trí hình chữ nhật (cần máy bật cửa sổ tự do),
     * không được thì mở toàn màn hình như bình thường. Khung tự thu lại về thanh vuốt.
     */
    private fun launchApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(this, "Không mở được ứng dụng này", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        var rect: Rect? = null
        val c = cardView
        if (isLand() && c != null && c.width > 0 && c.height > 0) {
            val a = IntArray(2)
            c.getLocationOnScreen(a)
            rect = Rect(a[0], a[1], a[0] + c.width, a[1] + c.height)
        }
        val free = freeformOn()
        val opts = if (rect != null && free) miniOptions(rect) else null
        hidePanel()
        try {
            if (opts != null) startActivity(intent, opts) else startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Không mở được ứng dụng này", Toast.LENGTH_SHORT).show()
            }
        }
        if (rect != null && !free && !hintedFreeform) {
            hintedFreeform = true
            Toast.makeText(
                this,
                "Máy chưa bật cửa sổ tự do nên ứng dụng mở toàn màn hình. Bật trong Tùy chọn nhà phát triển: " +
                    "“Bật cửa sổ dạng tự do” và “Buộc các hoạt động có thể thay đổi kích thước”.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun dismissDockPop() {
        dockPop?.let { detach(it) }
        dockPop = null
    }

    /** Nhấn giữ 1 ứng dụng trên thanh: hiện nút gỡ khỏi thanh. */
    private fun showDockPop(anchor: View, pkg: String, label: String) {
        val pn = panel ?: return
        val look = curLook ?: return
        dismissDockPop()
        val catcher = FrameLayout(this)
        catcher.isClickable = true
        catcher.setOnClickListener { dismissDockPop() }
        val pop = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@FloatingService, 12), Ui.dp(this@FloatingService, 10),
                Ui.dp(this@FloatingService, 12), Ui.dp(this@FloatingService, 10))
            background = Ui.rr(look.glassAt(98), 16, look.hair)
            elevation = Ui.dp(this@FloatingService, 8).toFloat()
            isClickable = true
        }
        val t = Ui.tv(this, look, label, 14f, true)
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        pop.addView(t)
        pop.addView(Ui.gap(this, 8))
        pop.addView(Ui.btn(this, look, "Gỡ khỏi thanh", false, Ic.TRASH, true) {
            saveDock(dockApps().filter { it != pkg })
            dismissDockPop()
            fillDock(look)
        })
        val w = Ui.dp(this, 190)
        val a = IntArray(2)
        anchor.getLocationOnScreen(a)
        val r = IntArray(2)
        pn.getLocationOnScreen(r)
        val ax = a[0] - r[0] - pn.paddingLeft
        val ay = a[1] - r[1] - pn.paddingTop
        val x = ax + anchor.width + Ui.dp(this, 8)
        val maxY = maxOf(0, pn.height - pn.paddingTop - pn.paddingBottom - Ui.dp(this, 110))
        catcher.addView(pop, FrameLayout.LayoutParams(w, -2).apply {
            leftMargin = x.coerceAtLeast(0)
            topMargin = ay.coerceIn(0, maxY)
        })
        pn.addView(catcher, FrameLayout.LayoutParams(-1, -1))
        dockPop = catcher
    }

    private fun loadApps(done: (List<AppEntry>) -> Unit) {
        appCache?.let {
            done(it)
            return
        }
        Thread {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val out = ArrayList<AppEntry>()
            val seen = HashSet<String>()
            try {
                for (ri in pm.queryIntentActivities(q, 0)) {
                    val pkg = ri.activityInfo?.packageName ?: continue
                    if (pkg == packageName || !seen.add(pkg)) continue
                    val label = try { ri.loadLabel(pm).toString() } catch (_: Exception) { pkg }
                    val icon = try { ri.loadIcon(pm) } catch (_: Exception) { continue }
                    out.add(AppEntry(pkg, label, icon))
                }
            } catch (_: Exception) {
            }
            out.sortBy { it.label.lowercase() }
            ui.post {
                appCache = out
                done(out)
            }
        }.start()
    }

    /** Màn chọn ứng dụng để thêm vào thanh dọc (hiện trong hình chữ nhật). */
    private fun buildPicker(col: LinearLayout, look: Look) {
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(Ui.iconBtn(this, look, Ic.BACK) {
            pickerOpen = false
            buildPanel()
        })
        head.addView(Ui.tv(this, look, "Thêm ứng dụng", 19f, true),
            lp(0, -2, 1f).apply { marginStart = Ui.dp(this@FloatingService, 12) })
        head.addView(Ui.iconBtn(this, look, Ic.DOWN) { hidePanel() },
            lp(-2, -2).apply { marginStart = Ui.dp(this@FloatingService, 8) })
        col.addView(head)
        col.addView(Ui.gap(this, 10))

        val field = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@FloatingService, 14), 0, Ui.dp(this@FloatingService, 10), 0)
            background = Ui.rr(look.tonal, 21)
        }
        field.addView(Ui.icon(this, Ic.SEARCH, look.muted, 18))
        val et = EditText(this).apply {
            hint = "Tìm ứng dụng"
            setTextColor(look.text)
            setHintTextColor(look.muted)
            textSize = 15f
            setSingleLine(true)
            background = null
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setPadding(Ui.dp(this@FloatingService, 10), 0, 0, 0)
        }
        field.addView(et, lp(0, Ui.dp(this, 42), 1f))
        col.addView(field, lp(-1, Ui.dp(this, 42)))
        col.addView(Ui.gap(this, 8))

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(box, FrameLayout.LayoutParams(-1, -2))
        col.addView(sv, lp(-1, 0, 1f))
        box.addView(Ui.tv(this, look, "Đang tải danh sách ứng dụng…", 14f, false, true))

        var all: List<AppEntry> = emptyList()
        var loaded = false
        fun fill() {
            box.removeAllViews()
            val key = et.text.toString().trim().lowercase()
            val added = dockApps().toSet()
            val shown = all.filter { key.isEmpty() || it.label.lowercase().contains(key) }
            if (shown.isEmpty()) {
                box.addView(Ui.tv(this, look, "Không có ứng dụng nào.", 14f, false, true))
                return
            }
            for (e in shown) {
                val on = e.pkg in added
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(Ui.dp(this@FloatingService, 6), Ui.dp(this@FloatingService, 8),
                        Ui.dp(this@FloatingService, 10), Ui.dp(this@FloatingService, 8))
                    background = RippleDrawable(
                        ColorStateList.valueOf(look.withAlpha(look.text, 14)), null, Ui.rr(Color.WHITE, 14)
                    )
                    isClickable = true
                }
                val iv = ImageView(this)
                iv.setImageDrawable(e.icon)
                row.addView(iv, lp(Ui.dp(this, 40), Ui.dp(this, 40)))
                val t = Ui.tv(this, look, e.label, 15f)
                t.maxLines = 1
                t.ellipsize = TextUtils.TruncateAt.END
                row.addView(t, lp(0, -2, 1f).apply {
                    marginStart = Ui.dp(this@FloatingService, 14)
                    marginEnd = Ui.dp(this@FloatingService, 8)
                })
                val mark = Ui.icon(this, if (on) Ic.CHECK else Ic.PLUS, if (on) look.accent else look.muted, 20)
                row.addView(mark)
                row.setOnClickListener {
                    val cur = dockApps().toMutableList()
                    if (cur.contains(e.pkg)) {
                        cur.remove(e.pkg)
                        mark.set(Ic.PLUS, look.muted)
                    } else {
                        cur.add(e.pkg)
                        mark.set(Ic.CHECK, look.accent)
                    }
                    saveDock(cur)
                    fillDock(look)
                }
                box.addView(row, lp(-1, -2))
            }
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (loaded) fill()
            }
        })
        loadApps { list ->
            if (!pickerOpen) return@loadApps
            all = list
            loaded = true
            fill()
        }
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
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
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
            Ui.btn(this, look, "Đưa lên", true, Ic.UPLOAD, land) {
                hideKeyboard(et)
                lines = parse(inputText)
                curLine = -1
                buildPanel()
            },
            Ui.btn(this, look, "Xóa", false, Ic.TRASH, land) {
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
            Ui.btn(this, look, "Đọc", true, Ic.PLAY, land) {
                hideKeyboard(et)
                val fresh = parse(inputText)
                if (fresh.isNotEmpty() && fresh != lines) {
                    lines = fresh
                    buildPanel()
                }
                startRead(0)
            },
            Ui.btn(this, look, "Dừng", false, Ic.STOP, land) { speaker.stop() }))
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

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                view?.evaluateJavascript(KEEP_JS, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(KEEP_JS, null)
                ytBack?.alpha = if (view?.canGoBack() == true) 1f else 0.35f
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, p: Int) {
                if (p in 20..80) view?.evaluateJavascript(KEEP_JS, null)
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
        onYtMoved()
    }

    /** WebView vừa đổi cửa sổ: nếu đang phát thì ép phát tiếp (trang có thể bị tạm dừng khi gỡ/gắn lại). */
    private fun onYtMoved() {
        val w = yt ?: return
        if (ytHas && ytPlaying) ytWant = true
        ytMoveAt = SystemClock.uptimeMillis()
        w.evaluateJavascript(KEEP_JS, null)
        if (!ytWant) return
        for (d in longArrayOf(120, 450, 1000, 2000)) {
            ui.postDelayed({
                if (ytWant) yt?.evaluateJavascript(PLAY_JS, null)
            }, d)
        }
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
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
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
        onYtMoved()
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
        ytWant = ytPlaying
        refreshActivity()
    }

    /** Đọc trạng thái video mỗi giây: tên, thời gian, đang phát hay tạm dừng. */
    private fun pollYt() {
        val w = yt ?: return
        val url = w.url ?: ""
        if (!(url.contains("/watch") || url.contains("/shorts"))) {
            if (ytHas) {
                ytHas = false
                refreshActivity()
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
                    if (o.optBoolean("p")) {
                        ytPlaying = true
                        ytWant = true
                    } else if (ytWant && SystemClock.uptimeMillis() - ytMoveAt < 3000) {
                        // vừa đổi cửa sổ nên bị tạm dừng: không tính là người dùng dừng, phát lại ngay
                        ytPlaying = true
                        yt?.evaluateJavascript(PLAY_JS, null)
                    } else {
                        ytPlaying = false
                        ytWant = false
                    }
                    ytTime = o.optDouble("t", 0.0).let { if (it.isNaN() || it.isInfinite()) 0.0 else it }
                    ytDur = o.optDouble("d", 0.0).let { if (it.isNaN() || it.isInfinite()) 0.0 else it }
                    ytTitle = o.optString("n")
                } catch (_: Exception) {
                    ytHas = false
                }
            }
            refreshActivity()
        }
    }
}
