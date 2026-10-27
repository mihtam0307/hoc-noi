package com.mihtam.floatstudy

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Giá trị giao diện của 1 đối tượng: t = "m" (app gốc) hoặc "f" (app nổi). */
class Look(val c: Context, val t: String) {
    val tint: Int = Prefs.int(c, "${t}_tint", 0).coerceIn(0, 3)
    val glass: Int = Prefs.int(c, "${t}_glass", 45)
    val bgAlpha: Int = Prefs.int(c, "${t}_bgalpha", 70)
    val bgPath: String? = Prefs.str(c, "${t}_bgimg")

    private val rgb = intArrayOf(0x141624, 0xFFFFFF, 0x1E5BD8, 0xE94F9A)

    val base: Int get() = 0xFF000000.toInt() or rgb[tint]
    val glassColor: Int get() = ((glass * 255 / 100) shl 24) or rgb[tint]
    val text: Int get() = if (tint == 1) 0xFF1B1D2A.toInt() else Color.WHITE

    fun withAlpha(color: Int, pct: Int): Int = (color and 0x00FFFFFF) or ((pct * 255 / 100) shl 24)
}

object Ui {
    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun glass(c: Context, look: Look, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(look.glassColor)
        cornerRadius = dp(c, radius).toFloat()
        setStroke(dp(c, 1), look.withAlpha(look.text, 35))
    }

    fun tv(c: Context, look: Look, s: String, sp: Float = 16f, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            textSize = sp
            setTextColor(look.text)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun style(c: Context, look: Look, b: TextView, strong: Boolean) {
        b.background = GradientDrawable().apply {
            setColor(look.withAlpha(look.text, if (strong) 38 else 16))
            cornerRadius = dp(c, 14).toFloat()
            setStroke(dp(c, 1), look.withAlpha(look.text, if (strong) 85 else 40))
        }
    }

    fun btn(c: Context, look: Look, s: String, strong: Boolean = false, onClick: () -> Unit): TextView {
        val b = tv(c, look, s, 15f, true)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10))
        style(c, look, b, strong)
        b.setOnClickListener { onClick() }
        return b
    }

    fun chips(c: Context, look: Look, opts: List<String>, sel: Int, onSel: (Int) -> Unit): View {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val btns = ArrayList<TextView>()
        opts.forEachIndexed { i, s ->
            val b = btn(c, look, s, i == sel) { }
            b.setOnClickListener {
                btns.forEachIndexed { j, x -> style(c, look, x, j == i) }
                onSel(i)
            }
            btns.add(b)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(c, 6)
            row.addView(b, lp)
        }
        val h = HorizontalScrollView(c)
        h.isHorizontalScrollBarEnabled = false
        h.addView(row)
        return h
    }

    /** Thanh trượt: chỉ lưu khi thả tay (onCommit), nhãn cập nhật trực tiếp. */
    fun slider(
        c: Context, look: Look, title: String, min: Int, max: Int, cur: Int,
        fmt: (Int) -> String, onCommit: (Int) -> Unit
    ): View {
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val label = tv(c, look, "$title: ${fmt(cur)}", 14f)
        val sb = SeekBar(c)
        sb.max = max - min
        sb.progress = (cur - min).coerceIn(0, max - min)
        sb.progressTintList = ColorStateList.valueOf(look.text)
        sb.thumbTintList = ColorStateList.valueOf(look.text)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                label.text = "$title: ${fmt(p + min)}"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                onCommit((s?.progress ?: 0) + min)
            }
        })
        col.addView(label)
        col.addView(sb)
        return col
    }

    fun bgImage(c: Context, look: Look): ImageView? {
        val bmp = Img.load(look.bgPath, 1400) ?: return null
        return ImageView(c).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = look.bgAlpha / 100f
        }
    }

    fun gap(c: Context, h: Int): View = View(c).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(c, h))
    }
}
