package com.mihtam.floatstudy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
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
    private var updBar: ProgressBar? = null
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
        val p = Ui.dp(this@MainActivity, 16)
        setPadding(p, p, p, p)
        background = Ui.glass(this@MainActivity, look, 20)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(this@MainActivity, 14) }
    }

    private fun field(look: Look, hint: String, init: String, type: Int, onText: (String) -> Unit): EditText {
        val q = Ui.dp(this, 12)
        return EditText(this).apply {
            this.hint = hint
            setText(init)
            setTextColor(look.text)
            setHintTextColor(look.withAlpha(look.text, 50))
            textSize = 15f
            inputType = type
            setSingleLine(true)
            setPadding(q, q, q, q)
            background = GradientDrawable().apply {
                setColor(look.withAlpha(look.text, 12))
                cornerRadius = Ui.dp(this@MainActivity, 12).toFloat()
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { onText(s?.toString() ?: "") }
            })
        }
    }

    private fun row2(a: android.view.View, b: android.view.View): LinearLayout {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(a, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = Ui.dp(this@MainActivity, 6) })
        r.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        return r
    }

    private fun render() {
        scroll?.let { scrollY = it.scrollY }
        val look = Look(this, "m")
        val root = FrameLayout(this)
        root.setBackgroundColor(look.base)
        Ui.bgImage(this, look)?.let { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(this@MainActivity, 18)
            setPadding(p, p * 2, p, p * 2)
        }
        updBar = null
        updLabel = null
        if (page == 0) page0(col, look) else page1(col, look)
        sv.addView(col)
        root.addView(sv, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        scroll = sv
        sv.post { sv.scrollTo(0, scrollY) }
    }

    // ------------------------------------------------------------ Trang chính

    private fun page0(col: LinearLayout, look: Look) {
        col.addView(Ui.tv(this, look, "Học Nổi", 32f, true))
        col.addView(Ui.tv(this, look, "Đọc nội dung tự động trên mọi ứng dụng", 14f))
        col.addView(Ui.gap(this, 20))

        updateCard(col, look)

        val c1 = card(look)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(Ui.tv(this, look, "Bật app nổi", 20f, true), LinearLayout.LayoutParams(0, -2, 1f))
        val sw = Switch(this)
        row.addView(sw)
        c1.addView(row)
        c1.addView(Ui.gap(this, 6))
        val status = Ui.tv(this, look, "", 13f)
        c1.addView(status)

        fun setStatus(on: Boolean) {
            status.text = when {
                on -> "Đang bật. Chạm hình nổi trên màn hình để mở khung đọc."
                !Settings.canDrawOverlays(this) -> "Chưa cấp quyền hiển thị trên ứng dụng khác."
                else -> "Đang tắt."
            }
        }
        sw.isChecked = FloatingService.running
        setStatus(sw.isChecked)
        sw.setOnCheckedChangeListener { _, on ->
            toggle(on, sw)
            setStatus(sw.isChecked)
        }
        if (!Settings.canDrawOverlays(this)) {
            c1.addView(Ui.gap(this, 10))
            c1.addView(Ui.btn(this, look, "Cấp quyền hiển thị trên ứng dụng khác", true) { openOverlay() })
        }
        col.addView(c1)

        accountCard(col, look)

        val c2 = card(look)
        c2.addView(Ui.tv(this, look, "Giao diện", 18f, true))
        c2.addView(Ui.tv(this, look, "Màu kính, ảnh nền, độ mờ, hình nổi, cỡ chữ, tốc độ đọc.", 13f))
        c2.addView(Ui.gap(this, 10))
        c2.addView(Ui.btn(this, look, "🎨 Tùy chỉnh giao diện", true) {
            page = 1
            scrollY = 0
            render()
        })
        col.addView(c2)

        val c3 = card(look)
        c3.addView(Ui.tv(this, look, "Đọc được gì?", 18f, true))
        c3.addView(Ui.tv(this, look,
            "Văn bản thường, công thức (x^2, m/s², a/b, \\frac{a}{b}, \\sqrt{x}, π, Δt, ≤, ≥, ≠, ∞, √, ∑, ∫…) và đơn vị (m/s, N, J, Hz…).", 13f))
        col.addView(c3)

        val c4 = card(look)
        c4.addView(Ui.tv(this, look, "Phiên bản ${Updater.currentName(this)}", 14f, true))
        c4.addView(Ui.gap(this, 8))
        c4.addView(Ui.btn(this, look, "Kiểm tra cập nhật") { checkUpdate(true) })
        col.addView(c4)
    }

    // ------------------------------------------------------------ Cập nhật

    private fun updateCard(col: LinearLayout, look: Look) {
        val info = upd
        if (info == null && updMsg.isEmpty()) return
        val c = card(look)
        if (info != null) {
            c.addView(Ui.tv(this, look, "Có bản mới ${info.name}", 18f, true))
            if (info.notes.isNotBlank()) c.addView(Ui.tv(this, look, info.notes.trim().take(300), 13f))
            c.addView(Ui.gap(this, 8))
            if (downloading) {
                val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
                bar.max = 100
                bar.progress = updPct
                bar.progressTintList = ColorStateList.valueOf(look.text)
                updBar = bar
                c.addView(bar)
                val l = Ui.tv(this, look, "Đang tải… $updPct%", 13f)
                updLabel = l
                c.addView(l)
            } else {
                c.addView(Ui.btn(this, look, "⬇ Cập nhật ngay", true) { startUpdate() })
            }
        }
        if (updMsg.isNotEmpty()) {
            if (info != null) c.addView(Ui.gap(this, 8))
            c.addView(Ui.tv(this, look, updMsg, 13f))
        }
        col.addView(c)
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
            updBar?.progress = p
            updLabel?.text = "Đang tải… $p%"
        }) { f, err ->
            downloading = false
            updMsg = if (f == null) (err ?: "Tải thất bại.") else (Updater.install(this, f) ?: "Đang mở trình cài đặt…")
            if (!isDestroyed) render()
        }
    }

    // ------------------------------------------------------------ Tài khoản & Drive

    private fun accountCard(col: LinearLayout, look: Look) {
        val c = card(look)
        c.addView(Ui.tv(this, look, "Tài khoản & Drive", 18f, true))
        c.addView(Ui.gap(this, 4))
        if (!Cloud.loggedIn(this)) {
            c.addView(Ui.tv(this, look, "Đăng nhập bằng mã gửi qua email để lưu cài đặt, nội dung và ảnh lên Google Drive.", 13f))
            c.addView(Ui.gap(this, 8))
            c.addView(field(look, "Link máy chủ Apps Script (…/exec)", serverTxt,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) { serverTxt = it })
            c.addView(Ui.gap(this, 8))
            c.addView(field(look, "Email của bạn", emailTxt,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) { emailTxt = it })
            c.addView(Ui.gap(this, 8))
            c.addView(Ui.btn(this, look, if (codeSent) "Gửi lại mã" else "Gửi mã qua email", true) { sendCode() })
            if (codeSent) {
                c.addView(Ui.gap(this, 8))
                c.addView(field(look, "Mã 6 số", codeTxt, InputType.TYPE_CLASS_NUMBER) { codeTxt = it })
                c.addView(Ui.gap(this, 8))
                c.addView(Ui.btn(this, look, "Xác nhận & đăng nhập", true) { verifyCode() })
            }
        } else {
            c.addView(Ui.tv(this, look, "Đã đăng nhập: ${Cloud.email(this)}", 14f))
            c.addView(Ui.gap(this, 8))
            c.addView(row2(
                Ui.btn(this, look, "⬆ Lưu lên Drive", true) { manualUpload() },
                Ui.btn(this, look, "⬇ Tải từ Drive", true) { manualDownload() }
            ))
            c.addView(Ui.gap(this, 8))
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            r.addView(Ui.tv(this, look, "Tự động lưu lên Drive", 14f), LinearLayout.LayoutParams(0, -2, 1f))
            val sw = Switch(this)
            sw.isChecked = Prefs.int(this, "autosave", 1) == 1
            sw.setOnCheckedChangeListener { _, on -> Prefs.putI(this, "autosave", if (on) 1 else 0) }
            r.addView(sw)
            c.addView(r)
            c.addView(Ui.gap(this, 8))
            c.addView(Ui.btn(this, look, "Đăng xuất") {
                Cloud.logout(this) {
                    accMsg = "Đã đăng xuất."
                    codeSent = false
                    if (!isDestroyed) render()
                }
            })
        }
        if (accMsg.isNotEmpty()) {
            c.addView(Ui.gap(this, 8))
            c.addView(Ui.tv(this, look, accMsg, 13f))
        }
        col.addView(c)
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
        col.addView(Ui.btn(this, look, "← Quay lại") {
            page = 0
            scrollY = 0
            render()
        }, LinearLayout.LayoutParams(-2, -2))
        col.addView(Ui.gap(this, 12))
        col.addView(Ui.tv(this, look, "Tùy chỉnh giao diện", 26f, true))
        col.addView(Ui.gap(this, 12))
        col.addView(Ui.chips(this, look, listOf("App gốc", "App nổi"), if (editT == "m") 0 else 1) {
            editT = if (it == 0) "m" else "f"
            scrollY = 0
            render()
        })
        col.addView(Ui.gap(this, 12))
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

    private fun toggle(on: Boolean, sw: Switch) {
        val i = Intent(this, FloatingService::class.java)
        if (on) {
            if (!Settings.canDrawOverlays(this)) {
                sw.isChecked = false
                Toast.makeText(this, "Hãy cấp quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show()
                openOverlay()
                return
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
    }
}
