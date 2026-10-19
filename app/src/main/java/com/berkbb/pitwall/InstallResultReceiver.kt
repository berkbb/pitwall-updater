package com.berkbb.pitwall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** [selfUpdate]: the session installed Pitwall Updater itself, not F1 TV. */
sealed interface InstallEvent {
    val selfUpdate: Boolean

    data class Success(override val selfUpdate: Boolean = false) : InstallEvent
    data class Failure(
        val message: String,
        val isSignatureConflict: Boolean = false,
        override val selfUpdate: Boolean = false
    ) : InstallEvent
}

object InstallEvents {
    private val _events = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 5)
    val events = _events.asSharedFlow()

    fun emit(event: InstallEvent) {
        _events.tryEmit(event)
    }
}

/**
 * Receives PackageInstaller session callbacks. Not exported: only the app's own
 * PendingIntent (handed to the system installer) can deliver results here.
 */
class InstallResultReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_SELF_UPDATE = "com.berkbb.pitwall.extra.SELF_UPDATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val selfUpdate = intent.getBooleanExtra(EXTRA_SELF_UPDATE, false)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmIntent)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                InstallEvents.emit(InstallEvent.Success(selfUpdate))
            }
            else -> {
                // STATUS_FAILURE_CONFLICT covers INSTALL_FAILED_UPDATE_INCOMPATIBLE (different signing key)
                val isConflict = status == PackageInstaller.STATUS_FAILURE_CONFLICT ||
                    message?.contains("UPDATE_INCOMPATIBLE") == true
                InstallEvents.emit(InstallEvent.Failure(message ?: "Code $status", isConflict, selfUpdate))
            }
        }
    }
}
