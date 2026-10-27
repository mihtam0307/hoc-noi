package com.mihtam.floatstudy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Đọc từng dòng, lặp theo số lần (count = -1 là vô hạn), dừng bất cứ lúc nào. */
class Speaker(ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null

    var langOk = true
        private set
    var speaking = false
        private set
    var total = 1
        private set

    var onLine: ((Int, Int) -> Unit)? = null   // (chỉ số dòng, lần lặp hiện tại)
    var onEnd: (() -> Unit)? = null

    private var lines: List<String> = emptyList()
    private var spoken: List<String> = emptyList()
    private var idx = 0
    private var from = 0
    private var pass = 1
    private var token = 0

    init {
        tts = TextToSpeech(ctx.applicationContext) { st ->
            if (st == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("vi", "VN"))
                langOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { advance(id) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { advance(id) }
                })
                ready = true
                pending?.invoke()
                pending = null
            }
        }
    }

    private fun advance(id: String?) {
        main.post {
            if (speaking && id != null && id.startsWith("$token:")) next()
        }
    }

    fun start(l: List<String>, startIdx: Int, count: Int, rate: Float) {
        val run = { begin(l, startIdx, count, rate) }
        if (ready) run() else pending = run
    }

    private fun begin(l: List<String>, startIdx: Int, count: Int, rate: Float) {
        token++
        lines = l
        spoken = l.map { MathSpeech.speak(it) }
        if (l.isEmpty() || spoken.all { it.isBlank() }) return
        var i = startIdx.coerceIn(0, l.size - 1)
        while (i < spoken.size && spoken[i].isBlank()) i++
        if (i >= spoken.size) i = spoken.indexOfFirst { it.isNotBlank() }
        from = i
        idx = i
        pass = 1
        total = count
        speaking = true
        tts?.setSpeechRate(rate)
        speakCurrent()
    }

    private fun speakCurrent() {
        onLine?.invoke(idx, pass)
        tts?.speak(spoken[idx].take(3900), TextToSpeech.QUEUE_FLUSH, null, "$token:$idx")
    }

    private fun next() {
        var guard = 0
        do {
            idx++
            if (idx >= lines.size) {
                idx = from
                pass++
                if (total != -1 && pass > total) {
                    finish()
                    return
                }
            }
            guard++
        } while (spoken[idx].isBlank() && guard <= lines.size + 1)
        speakCurrent()
    }

    private fun finish() {
        speaking = false
        onEnd?.invoke()
    }

    fun stop() {
        token++
        pending = null
        val was = speaking
        speaking = false
        tts?.stop()
        if (was) onEnd?.invoke()
    }

    fun shutdown() {
        token++
        speaking = false
        tts?.stop()
        tts?.shutdown()
    }
}
