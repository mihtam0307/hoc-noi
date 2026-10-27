package com.mihtam.floatstudy

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun int(c: Context, k: String, d: Int): Int = sp(c).getInt(k, d)
    fun str(c: Context, k: String): String? = sp(c).getString(k, null)
    fun putI(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    fun putS(c: Context, k: String, v: String?) = sp(c).edit().putString(k, v).apply()
}
