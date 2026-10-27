package com.mihtam.floatstudy

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Tự cập nhật: đọc bản phát hành mới nhất trên GitHub, tải APK có thanh tiến độ rồi cài. */
object Updater {
    class Info(val code: Long, val name: String, val url: String, val size: Long, val notes: String)

    private val main = Handler(Looper.getMainLooper())

    fun currentCode(c: Context): Long = c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode
    fun currentName(c: Context): String = c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""

    fun check(c: Context, cb: (Info?, String?) -> Unit) {
        Thread {
            var info: Info? = null
            var err: String? = null
            try {
                val conn = URL("https://api.github.com/repos/${Config.UPDATE_REPO}/releases/latest")
                    .openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "HocNoi")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                if (conn.responseCode != 200) {
                    err = "Chưa kiểm tra được bản mới (mã ${conn.responseCode})."
                } else {
                    val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val code = Regex("\\d+").findAll(j.optString("tag_name")).lastOrNull()
                        ?.value?.toLongOrNull() ?: 0L
                    val assets = j.optJSONArray("assets")
                    var url: String? = null
                    var size = 0L
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            if (a.optString("name").endsWith(".apk")) {
                                url = a.getString("browser_download_url")
                                size = a.optLong("size")
                                break
                            }
                        }
                    }
                    if (code > currentCode(c) && url != null) {
                        info = Info(code, j.optString("tag_name"), url, size, j.optString("body"))
                    }
                }
            } catch (e: Exception) {
                err = "Không kết nối được tới GitHub."
            }
            main.post { cb(info, err) }
        }.start()
    }

    fun download(c: Context, info: Info, onProgress: (Int) -> Unit, onDone: (File?, String?) -> Unit) {
        Thread {
            var out: File? = null
            var err: String? = null
            try {
                val conn = URL(info.url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "HocNoi")
                conn.connectTimeout = 20000
                conn.readTimeout = 30000
                if (conn.responseCode !in 200..299) throw Exception("mã ${conn.responseCode}")
                val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else info.size
                val f = File(c.cacheDir, "update.apk")
                conn.inputStream.use { ins ->
                    f.outputStream().use { os ->
                        val buf = ByteArray(16384)
                        var done = 0L
                        var last = -1
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = (done * 100 / total).toInt().coerceIn(0, 100)
                                if (p != last) {
                                    last = p
                                    main.post { onProgress(p) }
                                }
                            }
                        }
                    }
                }
                out = f
            } catch (e: Exception) {
                err = "Tải thất bại: ${e.message}"
            }
            main.post { onDone(out, err) }
        }.start()
    }

    /** Trả về thông báo lỗi, hoặc null nếu đã mở trình cài đặt. */
    fun install(c: Context, f: File): String? {
        if (!c.packageManager.canRequestPackageInstalls()) {
            c.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return "Hãy bật \"Cho phép cài ứng dụng\" cho Học Nổi, rồi quay lại bấm Cập nhật."
        }
        return try {
            val pi = c.packageManager.packageInstaller
            val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            p.setSize(f.length())
            if (Build.VERSION.SDK_INT >= 31) {
                p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = pi.createSession(p)
            pi.openSession(id).use { s ->
                f.inputStream().use { ins ->
                    s.openWrite("hoc-noi", 0, f.length()).use { out ->
                        ins.copyTo(out)
                        s.fsync(out)
                    }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pend = PendingIntent.getBroadcast(c, id, Intent(c, InstallReceiver::class.java), flags)
                s.commit(pend.intentSender)
            }
            null
        } catch (e: Exception) {
            "Không cài được: ${e.message}"
        }
    }
}
