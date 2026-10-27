package com.mihtam.floatstudy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.view.View
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private var page = 0
    private var editT = "m"
    private var scrollY = 0
    private var scroll: ScrollView? = null

    // Cập nhật
    private var upd: Updater.Info? = null
    private var updMsg = ""
    private var updPct = 0
    private var downloading = false
    private var checking = false
    private var updBar: ProgressLine? = null
    private var updLabel: TextView? = null

    // Tài khoản
    private var serverTxt = ""
    private var emailTxt = ""
    private var codeTxt = ""
    private var codeSent = false
    private var accMsg = ""
    private var busy = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        serverTxt = Cloud.server(this)
        emailTxt = Cloud.email(this) ?: ""
        render()
        checkUpdate(false)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // Xoay ngang / dọc: dựng lại bố cục theo kích thước mới, không để hệ thống tạo lại Activity.
    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        render()
    }

    private fun wide() = resources.configuration.screenWidthDp >= 620

    override fun onPause() {
        super.onPause()
        Cloud.autoSave(this)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (page != 0) {
            page = 0
            scrollY = 0
            render()
        } else super.onBackPressed()
    }

    // ------------------------------------------------------------ Hàm dựng giao diện

    private fun card(look: Look) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val p = Ui.dp(this@MainActivity, 18)
        setPadding(p, p, p, p)
        background = Ui.glass(this@MainActivity, look, 22)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(this@MainActivity, 14) }
    }

    private fun field(look: Look, hint: String, init: String, type: Int, onText: (String) -> Unit): EditText {
        val q = Ui.dp(this, 14)
        return EditText(this).apply {
            this.hint = hint
            setText(init)
            setTextColor(look.text)
            setHintTextColor(look.muted)
            textSize = 15f
            inputType = type
            setSingleLine(true)
            setPadding(q, q, q, q)
            background = Ui.rr(look.tonal, 14)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { onText(s?.toString() ?: "") }
            })
        }
    }

    @Suppress("DEPRECATION")
    private fun bars(look: Look) {
        window.statusBarColor = look.base
        window.navigationBarColor = look.base
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val m = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (look.light) m else 0, m)
        } else {
            var f = window.decorView.systemUiVisibility
            val m = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                (if (android.os.Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
            f = if (look.light) f or m else f and m.inv()
            window.decorView.systemUiVisibility = f
        }
    }

    @Suppress("DEPRECATION")
    private fun render() {
        scroll?.let { scrollY = it.scrollY }
        val look = Look(this, "m")
        bars(look)
        val root = FrameLayout(this)
        root.setBackgroundColor(look.base)
        Ui.bgImage(this, look)?.let { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        val frame = MaxWidthFrame(this, Ui.dp(this, 980))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(this@MainActivity, 18)
            setPadding(p, p, p, p * 2)
        }
        frame.addView(col, FrameLayout.LayoutParams(-1, -2))
        updBar = null
        updLabel = null
        if (page == 0) page0(col, look) else page1(col, look)
        sv.addView(frame, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL))
        // Cách rìa màn hình một khoảng nhỏ nhưng vẫn phủ kín màn: đệm theo vùng an toàn (tai thỏ, thanh hệ thống)
        root.addView(sv, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { v, ins ->
            v.setPadding(ins.systemWindowInsetLeft, 0, ins.systemWindowInsetRight, 0)
            ins
        }
        setContentView(root)
        scroll = sv
        sv.post { sv.scrollTo(0, scrollY) }
    }

    private fun arrange(col: LinearLayout, cards: List<View>) {
        if (!wide()) {
            cards.forEach { col.addView(it) }
            return
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        row.addView(l, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = Ui.dp(this@MainActivity, 7) })
        row.addView(r, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = Ui.dp(this@MainActivity, 7) })
        cards.forEachIndexed { i, v -> (if (i % 2 == 0) l else r).addView(v) }
        col.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun tile(look: Look, ic: Ic, title: String, desc: String): LinearLayout {
        val t = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(this@MainActivity, 12)
            setPadding(p, p, p, p)
            background = Ui.rr(look.tonal, 16)
        }
        t.addView(Ui.icon(this, ic, look.accent, 22))
        t.addView(Ui.gap(this, 8))
        t.addView(Ui.tv(this, look, title, 14f, true))
        t.addView(Ui.tv(this, look, desc, 12f, false, true))
        return t
    }

    // ------------------------------------------------------------ Trang chính

    private fun page0(col: LinearLayout, look: Look) {
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_launcher)
        head.addView(logo, LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)).apply {
            marginEnd = Ui.dp(this@MainActivity, 14)
        })
        val tc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(Ui.tv(this, look, "Học Nổi", 28f, true))
        tc.addView(Ui.tv(this, look, "Đọc tự động và xem YouTube trong khung nổi", 13.5f, false, true))
        head.addView(tc, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(head)
        col.addView(Ui.gap(this, 18))

        val cards = ArrayList<View>()
        updateCard(look)?.let { cards.add(it) }
        cards.add(floatCard(look))
        cards.add(accountCard(look))

        val c2 = card(look)
        c2.addView(Ui.tv(this, look, "Giao diện", 18f, true))
        c2.addView(Ui.gap(this, 2))
        c2.addView(Ui.tv(this, look, "Màu giao diện, ảnh nền, độ mờ, hình nổi, cỡ chữ, tốc độ đọc.", 13f, false, true))
        c2.addView(Ui.gap(this, 12))
        c2.addView(Ui.btn(this, look, "Tùy chỉnh giao diện", true, Ic.SLIDERS) {
            page = 1
            scrollY = 0
            render()
        })
        cards.add(c2)

        val c3 = card(look)
        c3.addView(Ui.tv(this, look, "Đọc được gì?", 18f, true))
        c3.addView(Ui.gap(this, 2))
        c3.addView(Ui.tv(this, look,
            "Văn bản thường, công thức (x^2, m/s², a/b, \\frac{a}{b}, \\sqrt{x}, π, Δt, ≤, ≥, ≠, ∞, √, ∑, ∫…) và đơn vị (m/s, N, J, Hz…).", 13f, false, true))
        cards.add(c3)

        val c4 = card(look)
        c4.addView(Ui.tv(this, look, "Phiên bản ${Updater.currentName(this)}", 15f, true))
        c4.addView(Ui.gap(this, 10))
        c4.addView(Ui.btn(this, look, "Kiểm tra cập nhật", false, Ic.REPEAT) { checkUpdate(true) })
        cards.add(c4)

        arrange(col, cards)
    }

    private fun floatCard(look: Look): View {
        val c1 = card(look)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(Ui.tv(this, look, "Bật app nổi", 20f, true))
        val status = Ui.tv(this, look, "", 13f, false, true)
        tc.addView(status)
        row.addView(tc, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = Ui.dp(this@MainActivity, 12) })

        fun setStatus(on: Boolean) {
            status.text = when {
                on -> "Đang bật. Chạm hình nổi để mở khung."
                !Settings.canDrawOverlays(this) -> "Chưa cấp quyền hiển thị trên ứng dụng khác."
                else -> "Đang tắt."
            }
        }
        var ref: ToggleView? = null
        val sw = Ui.toggle(this, look, FloatingService.running) { on ->
            if (!toggle(on)) ref?.switchTo(false, false)
            setStatus(ref?.state == true)
        }
        ref = sw
        row.addView(sw)
        setStatus(FloatingService.running)
        c1.addView(row)
        c1.addView(Ui.gap(this, 14))
        c1.addView(Ui.row2(this,
            tile(look, Ic.WAVE, "Đọc tự động", "Đọc, lặp lại, đọc được công thức"),
            tile(look, Ic.VIDEO, "YouTube nổi", "Tìm kiếm, xem; thu nhỏ vẫn nghe")))
        if (!Settings.canDrawOverlays(this)) {
            c1.addView(Ui.gap(this, 12))
            c1.addView(Ui.btn(this, look, "Cấp quyền hiển thị trên ứng dụng khác", true, Ic.FLOAT) { openOverlay() })
        }
        return c1
    }

    // ------------------------------------------------------------ Cập nhật

    private fun updateCard(look: Look): View? {
        val info = upd
        if (info == null && updMsg.isEmpty()) return null
        val c = card(look)
        if (info != null) {
            c.addView(Ui.tv(this, look, "Có bản mới ${info.name}", 18f, true))
            if (info.notes.isNotBlank()) c.addView(Ui.tv(this, look, info.notes.trim().take(300), 13f, false, true))
            c.addView(Ui.gap(this, 10))
            if (downloading) {
                val bar = ProgressLine(this, look.withAlpha(look.text, 16), look.accent)
                bar.set(updPct / 100f)
                updBar = bar
                c.addView(bar, LinearLayout.LayoutParams(-1, -2))
                c.addView(Ui.gap(this, 6))
                val l = Ui.tv(this, look, "Đang tải… $updPct%", 13f, false, true)
                updLabel = l
                c.addView(l)
            } else {
                c.addView(Ui.btn(this, look, "Cập nhật ngay", true, Ic.DOWNLOAD) { startUpdate() })
            }
        }
        if (updMsg.isNotEmpty()) {
            if (info != null) c.addView(Ui.gap(this, 8))
            c.addView(Ui.tv(this, look, updMsg, 13f, false, true))
        }
        return c
    }

    private fun checkUpdate(manual: Boolean) {
        if (checking || downloading) return
        checking = true
        if (manual) {
            updMsg = "Đang kiểm tra…"
            render()
        }
        Updater.check(this) { info, err ->
            checking = false
            if (!isDestroyed) {
                upd = info
                updMsg = if (manual) (err ?: if (info == null) "Bạn đang dùng bản mới nhất." else "") else ""
                render()
            }
        }
    }

    private fun startUpdate() {
        val info = upd ?: return
        if (downloading) return
        downloading = true
        updPct = 0
        updMsg = ""
        render()
        Updater.download(this, info, { p ->
            updPct = p
            updBar?.set(p / 100f)
            updLabel?.text = "Đang tải… $p%"
        }) { f, err ->
            downloading = false
            updMsg = if (f == null) (err ?: "Tải thất bại.") else (Updater.install(this, f) ?: "Đang mở trình cài đặt…")
            if (!isDestroyed) render()
        }
    }

    // ------------------------------------------------------------ Tài khoản & Drive

    private fun accountCard(look: Look): View {
        val c = card(look)
        c.addView(Ui.tv(this, look, "Tài khoản & Drive", 18f, true))
        c.addView(Ui.gap(this, 4))
        if (!Cloud.loggedIn(this)) {
            c.addView(Ui.tv(this, look, "Đăng nhập bằng mã gửi qua email để lưu cài đặt, nội dung và ảnh lên Google Drive.", 13f, false, true))
            c.addView(Ui.gap(this, 8))
            c.addView(field(look, "Link máy chủ Apps Script (…/exec)", serverTxt,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) { serverTxt = it })
            c.addView(Ui.gap(this, 8))
            c.addView(field(look, "Email của bạn", emailTxt,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) { emailTxt = it })
            c.addView(Ui.gap(this, 8))
            c.addView(Ui.btn(this, look, if (codeSent) "Gửi lại mã" else "Gửi mã qua email", true, Ic.FORWARD) { sendCode() })
            if (codeSent) {
                c.addView(Ui.gap(this, 8))
                c.addView(field(look, "Mã 6 số", codeTxt, InputType.TYPE_CLASS_NUMBER) { codeTxt = it })
                c.addView(Ui.gap(this, 8))
                c.addView(Ui.btn(this, look, "Xác nhận & đăng nhập", true, Ic.CHECK) { verifyCode() })
            }
        } else {
            c.addView(Ui.tv(this, look, "Đã đăng nhập: ${Cloud.email(this)}", 14f, false, true))
            c.addView(Ui.gap(this, 10))
            c.addView(Ui.row2(this,
                Ui.btn(this, look, "Lưu lên Drive", true, Ic.UPLOAD) { manualUpload() },
                Ui.btn(this, look, "Tải từ Drive", true, Ic.DOWNLOAD) { manualDownload() }
            ))
            c.addView(Ui.gap(this, 8))
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            r.addView(Ui.tv(this, look, "Tự động lưu lên Drive", 14f), LinearLayout.LayoutParams(0, -2, 1f))
            val sw = Ui.toggle(this, look, Prefs.int(this, "autosave", 1) == 1) { on ->
                Prefs.putI(this, "autosave", if (on) 1 else 0)
            }
            r.addView(sw)
            c.addView(r)
            c.addView(Ui.gap(this, 8))
            c.addView(Ui.btn(this, look, "Đăng xuất", false, Ic.BACK) {
                Cloud.logout(this) {
                    accMsg = "Đã đăng xuất."
                    codeSent = false
                    if (!isDestroyed) render()
                }
            })
        }
        if (accMsg.isNotEmpty()) {
            c.addView(Ui.gap(this, 8))
            c.addView(Ui.tv(this, look, accMsg, 13f, false, true))
        }
        return c
    }

    private fun sendCode() {
        if (busy) return
        val e = emailTxt.trim()
        if (!e.contains("@")) {
            accMsg = "Email không hợp lệ."
            render()
            return
        }
        Prefs.putS(this, "server", serverTxt.trim())
        busy = true
        accMsg = "Đang gửi mã…"
        render()
        Cloud.send(this, e) { err ->
            busy = false
            if (err == null) {
                codeSent = true
                accMsg = "Đã gửi mã tới $e. Kiểm tra hộp thư (cả mục Spam), mã có hiệu lực 10 phút."
            } else {
                accMsg = err
            }
            if (!isDestroyed) render()
        }
    }

    private fun verifyCode() {
        if (busy) return
        busy = true
        accMsg = "Đang xác nhận…"
        render()
        Cloud.verify(this, emailTxt.trim(), codeTxt.trim()) { err ->
            if (err != null) {
                busy = false
                accMsg = err
                if (!isDestroyed) render()
            } else {
                accMsg = "Đăng nhập thành công. Đang đồng bộ…"
                if (!isDestroyed) render()
                Cloud.download(this) { e2, found ->
                    if (e2 == null && !found) {
                        Cloud.upload(this, true) { e3 ->
                            busy = false
                            accMsg = e3 ?: "Đã đăng nhập và lưu dữ liệu lên Drive."
                            if (!isDestroyed) render()
                        }
                    } else {
                        busy = false
                        accMsg = e2 ?: "Đã đăng nhập và tải dữ liệu từ Drive."
                        if (!isDestroyed) render()
                    }
                }
            }
        }
    }

    private fun manualUpload() {
        accMsg = "Đang lưu lên Drive…"
        render()
        Cloud.upload(this, true) { e ->
            accMsg = e ?: "Đã lưu lên Drive."
            if (!isDestroyed) render()
        }
    }

    private fun manualDownload() {
        accMsg = "Đang tải từ Drive…"
        render()
        Cloud.download(this) { e, found ->
            accMsg = e ?: if (found) "Đã tải từ Drive." else "Drive chưa có dữ liệu."
            if (!isDestroyed) render()
        }
    }

    // ------------------------------------------------------------ Trang tùy chỉnh

    private fun page1(col: LinearLayout, look: Look) {
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(Ui.iconBtn(this, look, Ic.BACK) {
            page = 0
            scrollY = 0
            render()
        })
        head.addView(Ui.tv(this, look, "Tùy chỉnh giao diện", 22f, true),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = Ui.dp(this@MainActivity, 12) })
        col.addView(head)
        col.addView(Ui.gap(this, 16))
        col.addView(Ui.segment(this, look, listOf(Ic.BOOK to "App gốc", Ic.FLOAT to "App nổi"),
            if (editT == "m") 0 else 1) {
            editT = if (it == 0) "m" else "f"
            scrollY = 0
            render()
        })
        col.addView(Ui.gap(this, 14))
        val c = card(look)
        c.addView(SettingsUi.build(this, look, editT, { key ->
            startActivity(Intent(this, PickActivity::class.java).putExtra("key", key))
        }, {
            if (editT == "m") render()
        }))
        col.addView(c)
    }

    private fun openOverlay() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    /** Trả về true nếu đã bật/tắt được; false nếu thiếu quyền (công tắc sẽ tự trả về tắt). */
    private fun toggle(on: Boolean): Boolean {
        val i = Intent(this, FloatingService::class.java)
        if (on) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Hãy cấp quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show()
                openOverlay()
                return false
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            startForegroundService(i)
        } else {
            stopService(i)
        }
        return true
    }
}
