package com.mihtam.floatstudy

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Giá trị giao diện của 1 đối tượng: t = "m" (app gốc) hoặc "f" (app nổi). Mặc định là màu trắng. */
class Look(val c: Context, val t: String) {
    val tint: Int = Prefs.int(c, "${t}_tint", 1).coerceIn(0, 3)
    val glass: Int = Prefs.int(c, "${t}_glass", if (t == "f") 94 else 85)
    val bgAlpha: Int = Prefs.int(c, "${t}_bgalpha", 70)
    val bgPath: String? = Prefs.str(c, "${t}_bgimg")

    private val baseRgb = intArrayOf(0x0E1017, 0xFFFFFF, 0x0B2E73, 0x8E1F5C)
    private val glassRgb = intArrayOf(0x1B1F2E, 0xF2F4F9, 0x1D55CF, 0xDB4690)
    private val accentRgb = intArrayOf(0x93A4FF, 0x3555FF, 0xFFFFFF, 0xFFFFFF)
    private val onAccentRgb = intArrayOf(0x0E1017, 0xFFFFFF, 0x0B2E73, 0x8E1F5C)

    val light: Boolean get() = tint == 1
    val base: Int get() = 0xFF000000.toInt() or baseRgb[tint]
    val glassColor: Int get() = glassAt(glass)
    val text: Int get() = if (tint == 1) 0xFF151823.toInt() else Color.WHITE
    val accent: Int get() = 0xFF000000.toInt() or accentRgb[tint]
    val onAccent: Int get() = 0xFF000000.toInt() or onAccentRgb[tint]
    val muted: Int get() = withAlpha(text, 62)
    val hair: Int get() = withAlpha(text, if (light) 10 else 20)
    val tonal: Int get() = withAlpha(text, if (light) 7 else 12)

    fun glassAt(pct: Int): Int = ((pct.coerceIn(0, 100) * 255 / 100) shl 24) or glassRgb[tint]
    fun withAlpha(color: Int, pct: Int): Int = (color and 0x00FFFFFF) or ((pct * 255 / 100) shl 24)
}

/** Nút tròn chứa 1 icon tự vẽ. */
class IconBtn(
    c: Context, private val look: Look, ic: Ic, sizeDp: Int = 40, strong: Boolean = false,
    onClick: () -> Unit
) : FrameLayout(c) {
    val iv = IconView(c, ic, if (strong) look.onAccent else look.text)

    init {
        val s = Ui.dp(c, sizeDp)
        layoutParams = LinearLayout.LayoutParams(s, s)
        minimumWidth = s
        minimumHeight = s
        val bg = Ui.rr(if (strong) look.accent else look.tonal, sizeDp)
        val mask = Ui.rr(Color.WHITE, sizeDp)
        background = RippleDrawable(ColorStateList.valueOf(look.withAlpha(look.text, 18)), bg, mask)
        val i = Ui.dp(c, (sizeDp * 0.5f).toInt())
        addView(iv, LayoutParams(i, i, Gravity.CENTER))
        isClickable = true
        setOnClickListener { onClick() }
    }
}

object Ui {
    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun rr(color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            // bán kính tính theo px được đặt lại ở nơi dùng nếu cần; ở đây dùng dp -> px qua density toàn cục
            cornerRadius = radiusDp * android.content.res.Resources.getSystem().displayMetrics.density
            if (strokeColor != 0) setStroke(
                (strokeDp * android.content.res.Resources.getSystem().displayMetrics.density + 0.5f).toInt(),
                strokeColor
            )
        }

    fun glass(c: Context, look: Look, radius: Int): GradientDrawable = rr(look.glassColor, radius, look.hair)

    fun tv(c: Context, look: Look, s: String, sp: Float = 15f, bold: Boolean = false, muted: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            textSize = sp
            setTextColor(if (muted) look.muted else look.text)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun icon(c: Context, ic: Ic, color: Int, sizeDp: Int): IconView =
        IconView(c, ic, color).apply { layoutParams = LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)) }

    private fun ripple(look: Look, bg: GradientDrawable, radius: Int) =
        RippleDrawable(ColorStateList.valueOf(look.withAlpha(look.text, 18)), bg, rr(Color.WHITE, radius))

    /** Nút bấm dạng viên: strong = nền màu nhấn; ic = icon tự vẽ phía trước chữ. */
    fun btn(
        c: Context, look: Look, s: String, strong: Boolean = false, ic: Ic? = null,
        onClick: () -> Unit
    ): LinearLayout {
        val col = if (strong) look.onAccent else look.text
        val b = LinearLayout(c)
        b.orientation = LinearLayout.HORIZONTAL
        b.gravity = Gravity.CENTER
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12))
        b.background = ripple(look, rr(if (strong) look.accent else look.tonal, 14), 14)
        if (ic != null) {
            val i = icon(c, ic, col, 18)
            (i.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(c, 8)
            b.addView(i)
        }
        val t = TextView(c)
        t.text = s
        t.textSize = 14.5f
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.setTextColor(col)
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        b.addView(t)
        b.isClickable = true
        b.setOnClickListener { onClick() }
        return b
    }

    fun iconBtn(c: Context, look: Look, ic: Ic, sizeDp: Int = 40, strong: Boolean = false, onClick: () -> Unit): IconBtn =
        IconBtn(c, look, ic, sizeDp, strong, onClick)

    fun row2(c: Context, a: View, b: View): LinearLayout {
        val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(a, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(c, 8) })
        r.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        return r
    }

    private fun styleChip(c: Context, look: Look, t: TextView, on: Boolean) {
        t.background = rr(if (on) look.accent else look.tonal, 12)
        t.setTextColor(if (on) look.onAccent else look.text)
    }

    /** Hàng chip chọn 1 (cuộn ngang được). */
    fun chips(c: Context, look: Look, opts: List<String>, sel: Int, onSel: (Int) -> Unit): View {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val items = ArrayList<TextView>()
        opts.forEachIndexed { i, s ->
            val t = TextView(c)
            t.text = s
            t.textSize = 13.5f
            t.setTypeface(t.typeface, Typeface.BOLD)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9))
            styleChip(c, look, t, i == sel)
            t.setOnClickListener {
                items.forEachIndexed { j, x -> styleChip(c, look, x, j == i) }
                onSel(i)
            }
            items.add(t)
            row.addView(t, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(c, 6) })
        }
        val h = HorizontalScrollView(c)
        h.isHorizontalScrollBarEnabled = false
        h.addView(row)
        return h
    }

    /** Thanh chuyển tab có icon, các mục chia đều bề rộng. */
    fun segment(c: Context, look: Look, items: List<Pair<Ic, String>>, sel: Int, onSel: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(c)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4))
        box.background = rr(look.tonal, 16)
        items.forEachIndexed { i, (ic, label) ->
            val on = i == sel
            val col = if (on) look.onAccent else look.muted
            val cell = LinearLayout(c)
            cell.orientation = LinearLayout.HORIZONTAL
            cell.gravity = Gravity.CENTER
            cell.setPadding(dp(c, 8), dp(c, 9), dp(c, 8), dp(c, 9))
            if (on) cell.background = rr(look.accent, 12)
            val iv = icon(c, ic, col, 18)
            (iv.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(c, 7)
            cell.addView(iv)
            val t = TextView(c)
            t.text = label
            t.textSize = 14f
            t.setTypeface(t.typeface, Typeface.BOLD)
            t.setTextColor(col)
            t.maxLines = 1
            cell.addView(t)
            cell.setOnClickListener { if (!on) onSel(i) }
            box.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return box
    }

    /** Thanh trượt: live = cập nhật khi đang kéo (nếu cần), onCommit = khi thả tay. */
    fun slider(
        c: Context, look: Look, title: String, min: Int, max: Int, cur: Int,
        fmt: (Int) -> String, live: ((Int) -> Unit)? = null, onCommit: (Int) -> Unit
    ): View {
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(tv(c, look, title, 14f), LinearLayout.LayoutParams(0, -2, 1f))
        val value = tv(c, look, fmt(cur), 14f, true)
        value.setTextColor(look.accent)
        head.addView(value)
        val sb = SliderView(c, min, max, cur, look.accent, look.withAlpha(look.text, 16))
        sb.onLive = { v ->
            value.text = fmt(v)
            live?.invoke(v)
        }
        sb.onCommit = { v -> onCommit(v) }
        col.addView(head)
        col.addView(sb, LinearLayout.LayoutParams(-1, -2))
        return col
    }

    fun toggle(c: Context, look: Look, on: Boolean, onChange: (Boolean) -> Unit): ToggleView =
        ToggleView(c, on, look.accent, look.withAlpha(look.text, 22), onChange)

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
