package com.berkbb.pitwall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reopens Pitwall Updater after it updated itself. Only when the update was started from the
 * app (flag set in [UpdaterViewModel.installSelfUpdate]), so an adb or store install stays quiet.
 */
class SelfUpdateRestartReceiver : BroadcastReceiver() {
    companion object {
        const val PREFS = "self_update"
        const val KEY_PENDING = "restart_after_update"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return
        prefs.edit().remove(KEY_PENDING).apply()
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
