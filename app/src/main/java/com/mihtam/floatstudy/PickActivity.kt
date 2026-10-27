package com.mihtam.floatstudy

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import java.io.File
import java.io.FileOutputStream

/** Activity trong suốt, chỉ để chọn ảnh rồi lưu vào bộ nhớ app. */
class PickActivity : Activity() {
    private var key = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        key = intent.getStringExtra("key") ?: ""
        if (key.isEmpty()) {
            finish()
            return
        }
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, 1)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        val uri = data?.data
        if (res != RESULT_OK || uri == null) {
            finish()
            return
        }
        Thread {
            val bubble = key == "bubble_img"
            val bmp = Img.decode(contentResolver, uri, if (bubble) 512 else 1600)
            if (bmp != null) {
                val f = File(filesDir, key + "_" + System.currentTimeMillis() + if (bubble) ".png" else ".jpg")
                FileOutputStream(f).use {
                    if (bubble) bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    else bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
                }
                val old = Prefs.str(this, key)
                Prefs.putS(this, key, f.absolutePath)
                Prefs.putI(this, "img_dirty", 1)
                old?.let { File(it).delete() }
            }
            runOnUiThread { finish() }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        FloatingService.instance?.onPickDone()
    }
}
