package com.mihtam.floatstudy

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

object Img {
    fun load(path: String?, max: Int): Bitmap? {
        if (path == null) return null
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var s = 1
            while (o.outWidth / s > max * 2 || o.outHeight / s > max * 2) s *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
        } catch (e: Throwable) {
            null
        }
    }

    fun decode(cr: ContentResolver, uri: Uri, max: Int): Bitmap? {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / s > max * 2 || o.outHeight / s > max * 2) s *= 2
            val raw = cr.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
            } ?: return null
            val ori = try {
                cr.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                } ?: 1
            } catch (e: Exception) {
                1
            }
            val m = Matrix()
            when (ori) {
                6 -> m.postRotate(90f)
                3 -> m.postRotate(180f)
                8 -> m.postRotate(270f)
            }
            val sc = minOf(1f, max.toFloat() / maxOf(raw.width, raw.height))
            m.postScale(sc, sc)
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        } catch (e: Throwable) {
            null
        }
    }
}
