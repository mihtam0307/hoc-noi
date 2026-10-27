package com.mihtam.floatstudy

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/** Ghi lại lỗi gây văng app vào file để lần mở sau hiện ra cho dễ chụp / gửi đi sửa. */
object CrashLog {
    private const val F = "crash.txt"

    private fun file(c: Context) = File(c.applicationContext.filesDir, F)

    fun save(c: Context, e: Throwable) {
        try {
            val head = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n\n"
            file(c).writeText(head + Log.getStackTraceString(e))
        } catch (_: Throwable) {
        }
    }

    fun install(c: Context) {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            save(c, e)
            old?.uncaughtException(t, e)
        }
    }

    fun read(c: Context): String? = try {
        val f = file(c)
        if (f.exists()) f.readText().take(6000) else null
    } catch (_: Throwable) {
        null
    }

    fun clear(c: Context) {
        try { file(c).delete() } catch (_: Throwable) {}
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
