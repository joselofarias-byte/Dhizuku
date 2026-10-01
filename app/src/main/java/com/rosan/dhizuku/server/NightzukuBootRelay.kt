package com.rosan.dhizuku.server

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device Owner bootstrap relay for Nightzuku.
 *
 * HONOR/MagicOS can suppress third-party package boot launch even when its OEM
 * auto-start toggles are enabled. Dhizuku already runs as Device Owner and has
 * its own direct-boot receiver, so it explicitly wakes Nightzuku from that
 * trusted boot path.
 */
object NightzukuBootRelay {

    private const val TAG = "DhizukuNightzukuRelay"
    private const val NIGHTZUKU_PACKAGE = "com.joselofarias.nightzuku"
    private const val NIGHTZUKU_RECEIVER =
        "moe.shizuku.manager.receiver.DhizukuBootstrapReceiver"
    private const val ACTION_BOOTSTRAP =
        "com.joselofarias.nightzuku.action.DHIZUKU_BOOTSTRAP"
    private const val EXTRA_TRIGGER = "trigger"

    private val acceptedTriggers = setOf(
        Intent.ACTION_LOCKED_BOOT_COMPLETED,
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_USER_UNLOCKED,
        Intent.ACTION_MY_PACKAGE_REPLACED
    )

    fun relayIfBootEvent(context: Context, sourceAction: String?) {
        val action = sourceAction ?: return
        if (action !in acceptedTriggers) return

        val intent = Intent(ACTION_BOOTSTRAP)
            .setComponent(ComponentName(NIGHTZUKU_PACKAGE, NIGHTZUKU_RECEIVER))
            .putExtra(EXTRA_TRIGGER, "dhizuku:$action")

        runCatching {
            context.sendBroadcast(intent)
        }.onSuccess {
            Log.i(TAG, "Nightzuku bootstrap broadcast sent for $action")
        }.onFailure {
            Log.w(TAG, "Nightzuku bootstrap relay failed for $action", it)
        }
    }
}
