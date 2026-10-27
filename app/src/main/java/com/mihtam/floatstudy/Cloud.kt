package com.mihtam.floatstudy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Đăng nhập bằng mã email + lưu/tải dữ liệu qua Apps Script (Google Drive). */
object Cloud {
    private val intKeys = listOf(
        "m_tint", "m_glass", "m_bgalpha", "f_tint", "f_glass", "f_bgalpha",
        "bubble_alpha", "bubble_size", "text_size", "speed", "repeat"
    )
    private val imgKeys = listOf("m_bgimg", "f_bgimg", "bubble_img")
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var lastAuto = 0L

    fun server(c: Context): String {
        val s = Prefs.str(c, "server")?.trim()
        return if (s.isNullOrEmpty()) Config.SERVER_URL else s
    }

    fun token(c: Context): String? = Prefs.str(c, "token")
    fun email(c: Context): String? = Prefs.str(c, "email")
    fun loggedIn(c: Context): Boolean = !token(c).isNullOrEmpty()

    private fun err(m: String): JSONObject = JSONObject().put("ok", false).put("error", m)

    private fun call(c: Context, body: JSONObject): JSONObject {
        val u = server(c)
        if (u.isEmpty()) return err("Chưa nhập địa chỉ máy chủ (link Apps Script).")
        return try {
            val conn = URL(u).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 90000
            conn.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val st = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = st.bufferedReader().use { it.readText() }
            val r = JSONObject(text)
            if (r.optBoolean("auth")) {
                Prefs.putS(c, "token", null)
                Prefs.putS(c, "email", null)
            }
            r
        } catch (e: Exception) {
            err("Không kết nối được máy chủ hoặc máy chủ trả dữ liệu lạ. Kiểm tra lại link Apps Script.")
        }
    }

    private fun async(work: () -> Unit) {
        Thread(work).start()
    }

    private fun errOf(r: JSONObject): String? =
        if (r.optBoolean("ok")) null else r.optString("error", "Lỗi không rõ.")

    fun send(c: Context, email: String, cb: (String?) -> Unit) = async {
        val r = call(c, JSONObject().put("a", "send").put("email", email))
        main.post { cb(errOf(r)) }
    }

    fun verify(c: Context, email: String, code: String, cb: (String?) -> Unit) = async {
        val r = call(c, JSONObject().put("a", "verify").put("email", email).put("code", code))
        if (r.optBoolean("ok")) {
            Prefs.putS(c, "token", r.getString("token"))
            Prefs.putS(c, "email", email.trim().lowercase())
        }
        main.post { cb(errOf(r)) }
    }

    fun logout(c: Context, cb: () -> Unit) = async {
        val t = token(c)
        if (t != null) call(c, JSONObject().put("a", "logout").put("token", t))
        Prefs.putS(c, "token", null)
        Prefs.putS(c, "email", null)
        main.post { cb() }
    }

    private fun snapshot(c: Context, withImages: Boolean): JSONObject {
        val d = JSONObject()
        val ints = JSONObject()
        val sp = Prefs.sp(c)
        for (k in intKeys) if (sp.contains(k)) ints.put(k, sp.getInt(k, 0))
        d.put("ints", ints)
        d.put("content", Prefs.str(c, "content") ?: "")
        if (withImages) {
            val imgs = JSONObject()
            for (k in imgKeys) {
                val p = Prefs.str(c, k) ?: continue
                val f = File(p)
                if (!f.exists()) continue
                imgs.put(k, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
            }
            d.put("images", imgs)
        }
        return d
    }

    private fun applyData(c: Context, d: JSONObject) {
        val ints = d.optJSONObject("ints")
        if (ints != null) for (k in intKeys) if (ints.has(k)) Prefs.putI(c, k, ints.getInt(k))
        if (d.has("content")) Prefs.putS(c, "content", d.optString("content"))
        if (d.has("images")) {
            val imgs = d.optJSONObject("images") ?: JSONObject()
            for (k in imgKeys) {
                val old = Prefs.str(c, k)
                if (imgs.has(k)) {
                    val bytes = Base64.decode(imgs.getString(k), Base64.DEFAULT)
                    val ext = if (k == "bubble_img") ".png" else ".jpg"
                    val f = File(c.filesDir, k + "_" + System.currentTimeMillis() + ext)
                    f.writeBytes(bytes)
                    Prefs.putS(c, k, f.absolutePath)
                } else {
                    Prefs.putS(c, k, null)
                }
                old?.let { File(it).delete() }
            }
        }
    }

    fun upload(c: Context, withImages: Boolean, cb: (String?) -> Unit) = async {
        val t = token(c)
        val r = if (t == null) err("Chưa đăng nhập.")
        else call(c, JSONObject().put("a", "save").put("token", t).put("data", snapshot(c, withImages)))
        if (r.optBoolean("ok") && withImages) Prefs.putI(c, "img_dirty", 0)
        main.post { cb(errOf(r)) }
    }

    /** cb(lỗi, có dữ liệu trên Drive không) */
    fun download(c: Context, cb: (String?, Boolean) -> Unit) = async {
        val t = token(c)
        val r = if (t == null) err("Chưa đăng nhập.")
        else call(c, JSONObject().put("a", "load").put("token", t))
        var found = false
        if (r.optBoolean("ok")) {
            val d = r.optJSONObject("data")
            if (d != null) {
                found = true
                applyData(c, d)
            }
        }
        main.post { cb(errOf(r), found) }
    }

    /** Tự lưu nền (chỉ gửi ảnh khi ảnh có thay đổi). */
    fun autoSave(c: Context) {
        val app = c.applicationContext
        if (!loggedIn(app) || Prefs.int(app, "autosave", 1) != 1) return
        val now = System.currentTimeMillis()
        if (now - lastAuto < 15000) return
        lastAuto = now
        upload(app, Prefs.int(app, "img_dirty", 0) == 1) { }
    }
}
