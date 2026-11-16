package com.example.opendash.media

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Watches ordinary phone calls through the phone-state broadcast. This is what feeds the
 * dash call card when notification access is not granted (the "lite" build, or a full
 * build where the rider has not allowed it). Needs the Phone permission; the caller's
 * name additionally needs call-log access.
 */
class PhoneStateMonitor(private val context: Context) {
    private var registered = false
    private var lastNumber = ""

    private val receiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            // With notification access the listener gives richer data (and app calls too).
            if (MediaInfoProvider.isAccessGranted(context)) return
            intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                ?.takeIf { it.isNotBlank() }?.let { lastNumber = it }
            when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_RINGING ->
                    CallInfoProvider.update(IncomingCall(caller().ifBlank { "Incoming call" }, incoming = true))
                TelephonyManager.EXTRA_STATE_OFFHOOK ->
                    CallInfoProvider.update(IncomingCall(caller().ifBlank { "On call" }, incoming = false))
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    lastNumber = ""
                    CallInfoProvider.update(null)
                }
            }
        }
    }

    private fun caller(): String =
        RecentCalls.nameFor(context, lastNumber) ?: lastNumber

    fun start() {
        if (registered) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        registered = true
    }

    fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }
}
