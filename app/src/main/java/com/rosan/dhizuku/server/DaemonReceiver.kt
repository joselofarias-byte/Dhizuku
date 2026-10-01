package com.rosan.dhizuku.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DaemonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val action = intent?.action

        // Relay first: this path only needs package-manager/broadcast state and
        // must not be blocked by Room/credential storage during direct boot.
        NightzukuBootRelay.relayIfBootEvent(context, action)

        // Refresh Dhizuku owner state and daemon as before.
        DhizukuState.sync(context)
    }
}
