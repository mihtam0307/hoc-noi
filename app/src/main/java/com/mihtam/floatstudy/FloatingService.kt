package com.mihtam.floatstudy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingService : Service() {
    companion object {
        @Volatile var running = false
        @Volatile var instance: FloatingService? = null
        const val A_STOP = "stop"
        const val A_STOP_READ = "stopread"
        const val CH = "float_study"
    }

    private lateinit var wm: WindowManager
    private lateinit var speaker: Speaker
    private val ui = Handler(Looper.getMainLooper())

    private var bubble: ImageView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: View? = null

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
    private val repeats = intArrayOf(1, 2, 5, 10, 20, -1)

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val k = key ?: return@OnSharedPreferenceChangeListener
        ui.post {
            if (k.startsWith("bubble_")) applyBubble()
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
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        speaker = Speaker(this)
        speaker.onLine = { i, p -> curLine = i; pass = p; refreshStatus(); highlight() }
        speaker.onEnd = { curLine = -1; refreshStatus(); highlight() }

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

    override fun onDestroy() {
        running = false
        instance = null
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        speaker.shutdown()
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
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
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Học Nổi đang bật")
            .setContentText("Chạm hình nổi để mở khung đọc. Kéo để di chuyển.")
            .setContentIntent(open)
            .addAction(0, "Dừng đọc", pi(A_STOP_READ, 1))
            .addAction(0, "Tắt app nổi", pi(A_STOP, 2))
            .setOngoing(true)
            .build()
    }

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
                        lp.x = startX + dx
                        lp.y = startY + dy
                        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
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
    }

    // ---------------------------------------------------------------- Khung đọc

    private fun showPanel() {
        if (panel != null) return
        bubble?.visibility = View.GONE
        buildPanel()
    }

    private fun hidePanel() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        statusTv = null
        listScroll = null
        settingsSv = null
        lineViews.clear()
        bubble?.visibility = View.VISIBLE
        Cloud.autoSave(this)
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

    private fun lpW() = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = Ui.dp(this@FloatingService, 6) }

    private fun buildPanel() {
        settingsSv?.let { settingsY = it.scrollY }
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        lineViews.clear()
        settingsSv = null

        val look = Look(this, "f")
        curLook = look
        val textSp = Prefs.int(this, "text_size", 18).toFloat()
        val dm = resources.displayMetrics
        val pad = Ui.dp(this, 14)

        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(e: KeyEvent): Boolean {
                if (e.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (e.action == KeyEvent.ACTION_UP) hidePanel()
                    return true
                }
                return super.dispatchKeyEvent(e)
            }
        }
        root.background = Ui.glass(this, look, 26)
        root.clipToOutline = true
        root.outlineProvider = ViewOutlineProvider.BACKGROUND
        Ui.bgImage(this, look)?.let { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(col, FrameLayout.LayoutParams(-1, -1))

        // Thanh tiêu đề
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            Ui.tv(this, look, if (settingsOpen) "Cài đặt" else "Đọc tự động", 20f, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val hp = { LinearLayout.LayoutParams(-2, -2).apply { marginStart = Ui.dp(this@FloatingService, 8) } }
        head.addView(Ui.btn(this, look, if (settingsOpen) "← Quay lại" else "⚙") {
            settingsOpen = !settingsOpen
            settingsY = 0
            buildPanel()
        }, hp())
        head.addView(Ui.btn(this, look, "⌄ Thu nhỏ") { hidePanel() }, hp())
        col.addView(head)
        col.addView(Ui.gap(this, 10))

        if (settingsOpen) {
            val sv = ScrollView(this)
            sv.addView(SettingsUi.build(this, look, "f", { key -> pick(key) }, { }))
            col.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
            settingsSv = sv
            sv.post { sv.scrollTo(0, settingsY) }
        } else {
            val q = Ui.dp(this, 12)
            val et = EditText(this).apply {
                hint = "Nhập hoặc dán nội dung (mỗi dòng là một câu)…"
                setText(inputText)
                setTextColor(look.text)
                setHintTextColor(look.withAlpha(look.text, 50))
                textSize = textSp
                minLines = 3
                maxLines = 5
                gravity = Gravity.TOP
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setPadding(q, q, q, q)
                background = GradientDrawable().apply {
                    setColor(look.withAlpha(look.text, 12))
                    cornerRadius = Ui.dp(this@FloatingService, 14).toFloat()
                }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        inputText = s?.toString() ?: ""
                        Prefs.putS(this@FloatingService, "content", inputText)
                    }
                })
            }
            col.addView(et, LinearLayout.LayoutParams(-1, -2))
            col.addView(Ui.gap(this, 8))

            val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r1.addView(Ui.btn(this, look, "⬆ Đưa lên", true) {
                hideKeyboard(et)
                lines = parse(inputText)
                curLine = -1
                buildPanel()
            }, lpW())
            r1.addView(Ui.btn(this, look, "🗑 Xóa") {
                speaker.stop()
                inputText = ""
                Prefs.putS(this, "content", "")
                lines = emptyList()
                curLine = -1
                buildPanel()
            }, lpW())
            col.addView(r1)
            col.addView(Ui.gap(this, 12))

            col.addView(Ui.tv(this, look, "Đọc tự động · lặp lại", 13f, true))
            col.addView(Ui.gap(this, 4))
            col.addView(Ui.chips(this, look, listOf("1 lần", "2 lần", "5 lần", "10 lần", "20 lần", "∞ Vô hạn"), repeatIdx) {
                repeatIdx = it
                Prefs.putI(this, "repeat", it)
            })
            col.addView(Ui.gap(this, 8))

            val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r2.addView(Ui.btn(this, look, "▶ Đọc", true) {
                hideKeyboard(et)
                val fresh = parse(inputText)
                if (fresh.isNotEmpty() && fresh != lines) {
                    lines = fresh
                    buildPanel()
                }
                startRead(0)
            }, lpW())
            r2.addView(Ui.btn(this, look, "■ Dừng", true) { speaker.stop() }, lpW())
            col.addView(r2)
            col.addView(Ui.gap(this, 8))

            val st = Ui.tv(this, look, "", 14f, true)
            statusTv = st
            col.addView(st)
            col.addView(Ui.gap(this, 6))

            val sv = ScrollView(this)
            listScroll = sv
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            if (lines.isEmpty()) {
                box.addView(Ui.tv(this, look, "Chưa có nội dung. Nhập chữ rồi bấm “Đưa lên”.", 14f))
            }
            lines.forEachIndexed { i, s ->
                val t = Ui.tv(this, look, s, textSp)
                t.setPadding(q, q / 2, q, q / 2)
                t.setOnClickListener {
                    hideKeyboard(et)
                    startRead(i)
                }
                lineViews.add(t)
                box.addView(t, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(this@FloatingService, 4) })
            }
            sv.addView(box)
            col.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
            refreshStatus()
            highlight()
        }

        val lp = WindowManager.LayoutParams(
            (dm.widthPixels * 0.96f).toInt(), (dm.heightPixels * 0.90f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.CENTER
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        if (Build.VERSION.SDK_INT >= 31) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = Ui.dp(this, 20)
        }
        wm.addView(root, lp)
        panel = root
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
            speaker.speaking && curLine >= 0 -> {
                val tot = if (speaker.total == -1) "∞" else speaker.total.toString()
                "Đang đọc dòng ${curLine + 1}/${lines.size} · lần $pass/$tot"
            }
            lines.isNotEmpty() -> "Sẵn sàng · ${lines.size} dòng (chạm vào 1 dòng để đọc từ đó)"
            else -> ""
        }
    }

    private fun highlight() {
        val look = curLook ?: return
        lineViews.forEachIndexed { i, t ->
            val on = i == curLine && speaker.speaking
            t.background = if (on) GradientDrawable().apply {
                setColor(look.withAlpha(look.text, 30))
                cornerRadius = Ui.dp(this@FloatingService, 10).toFloat()
                setStroke(Ui.dp(this@FloatingService, 1), look.withAlpha(look.text, 70))
            } else null
            t.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
        }
        if (speaker.speaking && curLine in lineViews.indices) {
            val v = lineViews[curLine]
            listScroll?.post {
                listScroll?.smoothScrollTo(0, (v.top - Ui.dp(this, 40)).coerceAtLeast(0))
            }
        }
    }
}
