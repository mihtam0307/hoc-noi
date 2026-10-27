package com.mihtam.floatstudy

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import java.io.File

/** Bảng tùy chỉnh dùng chung cho app gốc và app nổi. t = đối tượng đang chỉnh ("m" hoặc "f"). */
object SettingsUi {
    private fun clear(c: Context, key: String) {
        Prefs.str(c, key)?.let { File(it).delete() }
        Prefs.putS(c, key, null)
        Prefs.putI(c, "img_dirty", 1)
    }

    private fun row(c: Context, a: View, b: View): LinearLayout {
        val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val l1 = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = Ui.dp(c, 6) }
        val l2 = LinearLayout.LayoutParams(0, -2, 1f)
        r.addView(a, l1)
        r.addView(b, l2)
        return r
    }

    fun build(
        c: Context, look: Look, t: String,
        pick: (String) -> Unit, onChanged: () -> Unit
    ): LinearLayout {
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        fun add(v: View) {
            col.addView(v)
            col.addView(Ui.gap(c, 10))
        }
        val st = Look(c, t)

        add(Ui.tv(c, look, "Màu kính", 14f, true))
        add(Ui.chips(c, look, listOf("Tối", "Sáng", "Xanh", "Hồng"), st.tint) {
            Prefs.putI(c, "${t}_tint", it); onChanged()
        })
        add(Ui.slider(c, look, "Độ kính (độ đặc của nền)", 0, 100, st.glass, { "$it%" }) {
            Prefs.putI(c, "${t}_glass", it); onChanged()
        })
        add(row(c,
            Ui.btn(c, look, "🖼 Ảnh nền") { pick("${t}_bgimg") },
            Ui.btn(c, look, "Xóa ảnh nền") { clear(c, "${t}_bgimg"); onChanged() }))
        add(Ui.slider(c, look, "Độ mờ ảnh nền", 0, 100, st.bgAlpha, { "$it%" }) {
            Prefs.putI(c, "${t}_bgalpha", it); onChanged()
        })

        if (t == "f") {
            add(Ui.tv(c, look, "Hình nổi", 16f, true))
            add(Ui.slider(c, look, "Độ mờ hình nổi", 15, 100, Prefs.int(c, "bubble_alpha", 90), { "$it%" }) {
                Prefs.putI(c, "bubble_alpha", it); onChanged()
            })
            add(Ui.slider(c, look, "Kích thước hình nổi", 40, 120, Prefs.int(c, "bubble_size", 64), { "${it}dp" }) {
                Prefs.putI(c, "bubble_size", it); onChanged()
            })
            add(row(c,
                Ui.btn(c, look, "🖼 Đổi hình nổi") { pick("bubble_img") },
                Ui.btn(c, look, "Hình mặc định") { clear(c, "bubble_img"); onChanged() }))
            add(Ui.tv(c, look, "Chữ & giọng đọc", 16f, true))
            add(Ui.slider(c, look, "Cỡ chữ", 14, 32, Prefs.int(c, "text_size", 18), { "${it}sp" }) {
                Prefs.putI(c, "text_size", it); onChanged()
            })
            add(Ui.slider(c, look, "Tốc độ đọc", 50, 200, Prefs.int(c, "speed", 100), { "%.1fx".format(it / 100.0) }) {
                Prefs.putI(c, "speed", it); onChanged()
            })
        }
        return col
    }
}
