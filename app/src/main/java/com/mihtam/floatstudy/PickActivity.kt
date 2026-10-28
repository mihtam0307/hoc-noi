package com.mihtam.floatstudy

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
        if (key == "ocr") {
            readText(uri)
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

    /** Lấy chữ từ ảnh: mỗi khối chữ thành 1 dòng để đọc (các dòng trong cùng khối được nối lại). */
    private fun readText(uri: Uri) {
        Toast.makeText(this, "Đang lấy chữ từ ảnh…", Toast.LENGTH_SHORT).show()
        Thread {
            val bmp = Img.decode(contentResolver, uri, 2400)
            runOnUiThread {
                if (bmp == null) {
                    Toast.makeText(this, "Không mở được ảnh", Toast.LENGTH_LONG).show()
                    finish()
                    return@runOnUiThread
                }
                val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                rec.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { r ->
                        val text = r.textBlocks
                            .map { b -> b.lines.joinToString(" ") { it.text }.trim() }
                            .filter { it.isNotEmpty() }
                            .joinToString("\n")
                        rec.close()
                        FloatingService.instance?.onOcrText(text)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        rec.close()
                        Toast.makeText(
                            this, "Chưa lấy được chữ (mô hình có thể đang tải, thử lại sau ít phút). " + (e.message ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        FloatingService.instance?.onPickDone()
    }
}
