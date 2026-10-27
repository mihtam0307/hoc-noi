package com.mihtam.floatstudy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val ui = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (ui != null) {
                    ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    c.startActivity(ui)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Toast.makeText(
                c, "Cài đặt thất bại: " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
