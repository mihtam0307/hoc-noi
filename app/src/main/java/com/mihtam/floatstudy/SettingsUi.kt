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

    fun build(
        c: Context, look: Look, t: String,
        pick: (String) -> Unit, onChanged: () -> Unit
    ): LinearLayout {
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        fun add(v: View, after: Int = 14) {
            col.addView(v)
            col.addView(Ui.gap(c, after))
        }
        fun title(s: String) = add(Ui.tv(c, look, s, 13f, true, true), 8)
        val st = Look(c, t)

        title("MÀU GIAO DIỆN")
        add(Ui.chips(c, look, listOf("Tối", "Trắng", "Xanh", "Hồng"), st.tint) {
            Prefs.putI(c, "${t}_tint", it); onChanged()
        }, 18)
        add(Ui.slider(c, look, "Độ kính (độ đặc của nền)", 0, 100, st.glass, { "$it%" }) {
            Prefs.putI(c, "${t}_glass", it); onChanged()
        })

        title("ẢNH NỀN")
        add(Ui.row2(c,
            Ui.btn(c, look, "Chọn ảnh", false, Ic.IMAGE) { pick("${t}_bgimg") },
            Ui.btn(c, look, "Xóa ảnh", false, Ic.TRASH) { clear(c, "${t}_bgimg"); onChanged() }), 14)
        add(Ui.slider(c, look, "Độ mờ ảnh nền", 0, 100, st.bgAlpha, { "$it%" }) {
            Prefs.putI(c, "${t}_bgalpha", it); onChanged()
        }, 18)

        if (t == "f") {
            title("HÌNH NỔI")
            add(Ui.slider(c, look, "Độ mờ hình nổi", 15, 100, Prefs.int(c, "bubble_alpha", 90), { "$it%" },
                { Prefs.putI(c, "bubble_alpha", it) }) {
                Prefs.putI(c, "bubble_alpha", it); onChanged()
            })
            add(Ui.slider(c, look, "Kích thước hình nổi", 40, 120, Prefs.int(c, "bubble_size", 64), { "${it}dp" },
                { Prefs.putI(c, "bubble_size", it) }) {
                Prefs.putI(c, "bubble_size", it); onChanged()
            })
            add(Ui.row2(c,
                Ui.btn(c, look, "Đổi hình", false, Ic.IMAGE) { pick("bubble_img") },
                Ui.btn(c, look, "Mặc định", false, Ic.BACK) { clear(c, "bubble_img"); onChanged() }), 18)

            title("THANH VUỐT (MÀN HÌNH NGANG)")
            add(Ui.chips(c, look, listOf("Cạnh trái", "Cạnh phải"), Prefs.int(c, "handle_side", 0).coerceIn(0, 1)) {
                Prefs.putI(c, "handle_side", it); onChanged()
            }, 18)

            title("CHỮ & GIỌNG ĐỌC")
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
