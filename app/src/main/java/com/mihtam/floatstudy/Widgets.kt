package com.mihtam.floatstudy

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

/** Bộ icon tự vẽ bằng nét vector (lưới 24x24), không dùng icon hay emoji có sẵn của hệ thống. */
enum class Ic {
    PLAY, PAUSE, STOP, SEARCH, SLIDERS, DOWN, BACK, FORWARD, CLOSE, IMAGE, UPLOAD, DOWNLOAD,
    TRASH, BOOK, VIDEO, REPEAT, HOME, EXPAND, CHECK, USER, FLOAT, WAVE, PLUS, SEEK, LIST
}

class IconView(c: Context, var ic: Ic, var color: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    fun set(ic: Ic, color: Int = this.color) {
        if (this.ic == ic && this.color == color) return
        this.ic = ic
        this.color = color
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        val d = Ui.dp(context, 24)
        setMeasuredDimension(resolveSize(d, w), resolveSize(d, h))
    }

    private fun poly(cv: Canvas, closed: Boolean, vararg v: Float) {
        path.reset()
        path.moveTo(v[0], v[1])
        var i = 2
        while (i + 1 < v.size) {
            path.lineTo(v[i], v[i + 1])
            i += 2
        }
        if (closed) path.close()
        cv.drawPath(path, p)
    }

    private fun rr(cv: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float) {
        rect.set(l, t, r, b)
        cv.drawRoundRect(rect, rad, rad, p)
    }

    override fun onDraw(cv: Canvas) {
        val s = minOf(width, height) / 24f
        cv.save()
        cv.translate((width - 24 * s) / 2f, (height - 24 * s) / 2f)
        cv.scale(s, s)
        p.color = color
        p.strokeWidth = 2f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.style = Paint.Style.STROKE
        when (ic) {
            Ic.PLAY -> {
                p.style = Paint.Style.FILL_AND_STROKE
                poly(cv, true, 7.5f, 5.5f, 18.5f, 12f, 7.5f, 18.5f)
            }
            Ic.PAUSE -> {
                p.style = Paint.Style.FILL
                rr(cv, 6f, 5f, 10.2f, 19f, 1.6f)
                rr(cv, 13.8f, 5f, 18f, 19f, 1.6f)
            }
            Ic.STOP -> {
                p.style = Paint.Style.FILL
                rr(cv, 6f, 6f, 18f, 18f, 2.6f)
            }
            Ic.SEARCH -> {
                cv.drawCircle(11f, 11f, 6.5f, p)
                cv.drawLine(16f, 16f, 20.5f, 20.5f, p)
            }
            Ic.SLIDERS -> {
                cv.drawLine(4f, 7f, 6f, 7f, p)
                cv.drawCircle(9f, 7f, 3f, p)
                cv.drawLine(12f, 7f, 20f, 7f, p)
                cv.drawLine(4f, 17f, 12f, 17f, p)
                cv.drawCircle(15f, 17f, 3f, p)
                cv.drawLine(18f, 17f, 20f, 17f, p)
            }
            Ic.DOWN -> poly(cv, false, 6f, 9f, 12f, 15f, 18f, 9f)
            Ic.BACK -> {
                cv.drawLine(19f, 12f, 5f, 12f, p)
                poly(cv, false, 11f, 6f, 5f, 12f, 11f, 18f)
            }
            Ic.FORWARD -> {
                cv.drawLine(5f, 12f, 19f, 12f, p)
                poly(cv, false, 13f, 6f, 19f, 12f, 13f, 18f)
            }
            Ic.CLOSE -> {
                cv.drawLine(6f, 6f, 18f, 18f, p)
                cv.drawLine(18f, 6f, 6f, 18f, p)
            }
            Ic.IMAGE -> {
                rr(cv, 4f, 5f, 20f, 19f, 3f)
                p.style = Paint.Style.FILL
                cv.drawCircle(9f, 10f, 1.7f, p)
                p.style = Paint.Style.STROKE
                poly(cv, false, 5f, 17f, 10f, 12.5f, 13.5f, 16f, 16f, 13.5f, 19f, 16.5f)
            }
            Ic.UPLOAD -> {
                cv.drawLine(12f, 16f, 12f, 5f, p)
                poly(cv, false, 7f, 10f, 12f, 5f, 17f, 10f)
                cv.drawLine(5f, 19.5f, 19f, 19.5f, p)
            }
            Ic.DOWNLOAD -> {
                cv.drawLine(12f, 5f, 12f, 16f, p)
                poly(cv, false, 7f, 11f, 12f, 16f, 17f, 11f)
                cv.drawLine(5f, 19.5f, 19f, 19.5f, p)
            }
            Ic.TRASH -> {
                cv.drawLine(5f, 7f, 19f, 7f, p)
                poly(cv, false, 10f, 7f, 10f, 5f, 14f, 5f, 14f, 7f)
                poly(cv, false, 7f, 7f, 8f, 19f, 16f, 19f, 17f, 7f)
                cv.drawLine(10.5f, 11f, 10.5f, 15f, p)
                cv.drawLine(13.5f, 11f, 13.5f, 15f, p)
            }
            Ic.BOOK -> {
                path.reset()
                path.moveTo(12f, 7f)
                path.cubicTo(10f, 5.5f, 7f, 5f, 4.5f, 5.5f)
                path.lineTo(4.5f, 18f)
                path.cubicTo(7f, 17.5f, 10f, 18f, 12f, 19.5f)
                path.cubicTo(14f, 18f, 17f, 17.5f, 19.5f, 18f)
                path.lineTo(19.5f, 5.5f)
                path.cubicTo(17f, 5f, 14f, 5.5f, 12f, 7f)
                path.close()
                cv.drawPath(path, p)
                cv.drawLine(12f, 7f, 12f, 19.5f, p)
            }
            Ic.VIDEO -> {
                rr(cv, 3f, 6f, 21f, 18f, 4.5f)
                p.style = Paint.Style.FILL_AND_STROKE
                p.strokeWidth = 1.2f
                poly(cv, true, 10.5f, 9.6f, 15f, 12f, 10.5f, 14.4f)
            }
            Ic.REPEAT -> {
                path.reset()
                path.moveTo(5f, 12f)
                path.lineTo(5f, 11f)
                path.quadTo(5f, 8f, 8f, 8f)
                path.lineTo(18f, 8f)
                cv.drawPath(path, p)
                poly(cv, false, 15f, 5f, 18f, 8f, 15f, 11f)
                path.reset()
                path.moveTo(19f, 12f)
                path.lineTo(19f, 13f)
                path.quadTo(19f, 16f, 16f, 16f)
                path.lineTo(6f, 16f)
                cv.drawPath(path, p)
                poly(cv, false, 9f, 13f, 6f, 16f, 9f, 19f)
            }
            Ic.HOME -> {
                poly(cv, false, 4f, 11f, 12f, 4.5f, 20f, 11f)
                poly(cv, false, 6f, 10f, 6f, 19.5f, 18f, 19.5f, 18f, 10f)
            }
            Ic.EXPAND -> {
                poly(cv, false, 9f, 4f, 4f, 4f, 4f, 9f)
                poly(cv, false, 15f, 4f, 20f, 4f, 20f, 9f)
                poly(cv, false, 9f, 20f, 4f, 20f, 4f, 15f)
                poly(cv, false, 15f, 20f, 20f, 20f, 20f, 15f)
            }
            Ic.CHECK -> poly(cv, false, 5f, 12.5f, 10f, 17.5f, 19f, 7.5f)
            Ic.USER -> {
                cv.drawCircle(12f, 8.5f, 3.6f, p)
                path.reset()
                path.moveTo(5f, 20f)
                path.quadTo(5f, 14f, 12f, 14f)
                path.quadTo(19f, 14f, 19f, 20f)
                cv.drawPath(path, p)
            }
            Ic.FLOAT -> {
                rr(cv, 3f, 5f, 21f, 19f, 3f)
                p.style = Paint.Style.FILL
                rr(cv, 12f, 12f, 18.5f, 16.5f, 1.6f)
            }
            Ic.SEEK -> {
                p.style = Paint.Style.FILL_AND_STROKE
                p.strokeWidth = 1.2f
                poly(cv, true, 5f, 6.5f, 12f, 12f, 5f, 17.5f)
                poly(cv, true, 12.5f, 6.5f, 19.5f, 12f, 12.5f, 17.5f)
            }
            Ic.LIST -> {
                cv.drawLine(9f, 7f, 20f, 7f, p)
                cv.drawLine(9f, 12f, 20f, 12f, p)
                cv.drawLine(9f, 17f, 20f, 17f, p)
                p.style = Paint.Style.FILL
                cv.drawCircle(5f, 7f, 1.3f, p)
                cv.drawCircle(5f, 12f, 1.3f, p)
                cv.drawCircle(5f, 17f, 1.3f, p)
                p.style = Paint.Style.STROKE
            }
            Ic.PLUS -> {
                cv.drawLine(12f, 5f, 12f, 19f, p)
                cv.drawLine(5f, 12f, 19f, 12f, p)
            }
            Ic.WAVE -> {
                cv.drawLine(5f, 10f, 5f, 14f, p)
                cv.drawLine(8.5f, 7f, 8.5f, 17f, p)
                cv.drawLine(12f, 4f, 12f, 20f, p)
                cv.drawLine(15.5f, 7f, 15.5f, 17f, p)
                cv.drawLine(19f, 10f, 19f, 14f, p)
            }
        }
        cv.restore()
    }
}

/** Công tắc tự vẽ, có hiệu ứng trượt. */
class ToggleView(
    c: Context, on: Boolean, private val accent: Int, private val off: Int,
    private val onChange: (Boolean) -> Unit
) : View(c) {
    var state: Boolean = on
        private set
    private var pos = if (on) 1f else 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val ev = ArgbEvaluator()

    init {
        isClickable = true
        setOnClickListener { switchTo(!state, true) }
    }

    fun switchTo(v: Boolean, notify: Boolean) {
        if (state == v) return
        state = v
        ValueAnimator.ofFloat(pos, if (v) 1f else 0f).apply {
            duration = 170
            addUpdateListener {
                pos = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        if (notify) onChange(v)
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(Ui.dp(context, 52), Ui.dp(context, 30))
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.color = ev.evaluate(pos, off, accent) as Int
        rect.set(0f, 0f, w, h)
        cv.drawRoundRect(rect, h / 2f, h / 2f, p)
        val r = h / 2f - Ui.dp(context, 3)
        val cx = h / 2f + pos * (w - h)
        p.color = 0xFFFFFFFF.toInt()
        cv.drawCircle(cx, h / 2f, r, p)
    }
}

/** Thanh trượt tự vẽ. onLive gọi khi đang kéo, onCommit gọi khi thả tay. */
class SliderView(
    c: Context, private val min: Int, private val max: Int, start: Int,
    private val accent: Int, private val track: Int
) : View(c) {
    var value: Int = start.coerceIn(min, max)
        private set
    var onLive: ((Int) -> Unit)? = null
    var onCommit: ((Int) -> Unit)? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val pad = Ui.dp(c, 14).toFloat()

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(resolveSize(Ui.dp(context, 200), w), Ui.dp(context, 36))
    }

    private fun frac(): Float = if (max == min) 0f else (value - min).toFloat() / (max - min)

    override fun onDraw(cv: Canvas) {
        val cy = height / 2f
        val th = Ui.dp(context, 4).toFloat()
        val x0 = pad
        val x1 = width - pad
        val x = x0 + frac() * (x1 - x0)
        p.style = Paint.Style.FILL
        p.color = track
        rect.set(x0, cy - th / 2, x1, cy + th / 2)
        cv.drawRoundRect(rect, th / 2, th / 2, p)
        p.color = accent
        rect.set(x0, cy - th / 2, x, cy + th / 2)
        cv.drawRoundRect(rect, th / 2, th / 2, p)
        val r = Ui.dp(context, 10).toFloat()
        p.color = 0xFFFFFFFF.toInt()
        cv.drawCircle(x, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = Ui.dp(context, 3).toFloat()
        p.color = accent
        cv.drawCircle(x, cy, r - p.strokeWidth / 2, p)
    }

    private fun update(px: Float) {
        val x0 = pad
        val x1 = width - pad
        val f = ((px - x0) / (x1 - x0)).coerceIn(0f, 1f)
        val nv = min + Math.round(f * (max - min))
        if (nv != value) {
            value = nv
            invalidate()
            onLive?.invoke(nv)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                update(e.x)
            }
            MotionEvent.ACTION_MOVE -> update(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                update(e.x)
                onCommit?.invoke(value)
            }
        }
        return true
    }
}

/** Thanh tiến độ mảnh. */
class ProgressLine(c: Context, private val track: Int, private val fill: Int) : View(c) {
    private var f = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun set(v: Float) {
        val n = v.coerceIn(0f, 1f)
        if (n != f) {
            f = n
            invalidate()
        }
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(resolveSize(Ui.dp(context, 100), w), Ui.dp(context, 4))
    }

    override fun onDraw(cv: Canvas) {
        val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.color = track
        rect.set(0f, 0f, width.toFloat(), h)
        cv.drawRoundRect(rect, h / 2, h / 2, p)
        p.color = fill
        rect.set(0f, 0f, width * f, h)
        cv.drawRoundRect(rect, h / 2, h / 2, p)
    }
}

/** Khung giới hạn bề rộng tối đa, tự canh giữa khi màn hình rộng (ngang / máy tính bảng). */
class MaxWidthFrame(c: Context, private val maxPx: Int) : FrameLayout(c) {
    override fun onMeasure(w: Int, h: Int) {
        val size = MeasureSpec.getSize(w)
        val mode = MeasureSpec.getMode(w)
        val nw = if (mode != MeasureSpec.UNSPECIFIED && size > maxPx)
            MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.EXACTLY) else w
        super.onMeasure(nw, h)
    }
}

/** Biểu tượng "thanh nhạc nhảy" phủ lên hình nổi khi đang phát âm thanh / đang đọc. */
class EqView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var t = 0f
    private var anim: ValueAnimator? = null
    private val ks = intArrayOf(1, 2, 3, 2, 1)
    private val ph = floatArrayOf(0f, 0.3f, 0.6f, 0.15f, 0.75f)

    fun setActive(on: Boolean) {
        if (on) {
            if (visibility != VISIBLE) visibility = VISIBLE
            if (anim == null) {
                anim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1100
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = android.view.animation.LinearInterpolator()
                    addUpdateListener {
                        t = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
        } else {
            anim?.cancel()
            anim = null
            if (visibility != GONE) visibility = GONE
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.color = 0x73000000
        cv.drawRect(0f, 0f, w, h, p)
        val n = ks.size
        val bw = w * 0.085f
        val gap = w * 0.055f
        val total = n * bw + (n - 1) * gap
        var x = (w - total) / 2f
        val minH = h * 0.12f
        val maxH = h * 0.42f
        p.color = 0xFFFFFFFF.toInt()
        for (i in 0 until n) {
            val s = 0.5f + 0.5f * Math.sin(2.0 * Math.PI * (ks[i] * t + ph[i])).toFloat()
            val bh = minH + (maxH - minH) * s
            rect.set(x, h / 2f - bh / 2f, x + bw, h / 2f + bh / 2f)
            cv.drawRoundRect(rect, bw / 2f, bw / 2f, p)
            x += bw + gap
        }
    }
}
