package com.example.opendash.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.opendash.dash.RecentCall
import com.example.opendash.util.DebugLog

/** Reads the phone's call history for the dash "Recent calls" list. */
object RecentCalls {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    /** Latest calls, one row per number, newest first. */
    fun load(context: Context, limit: Int = 8): List<RecentCall> {
        if (!hasPermission(context)) return emptyList()
        val out = ArrayList<RecentCall>()
        val seen = HashSet<String>()
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null, null, CallLog.Calls.DATE + " DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val number = c.getString(0)?.trim().orEmpty()
                    if (number.isBlank() || !seen.add(number.takeLast(10))) continue
                    val kind = when (c.getInt(2)) {
                        CallLog.Calls.OUTGOING_TYPE -> RecentCall.Kind.OUTGOING
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> RecentCall.Kind.MISSED
                        else -> RecentCall.Kind.INCOMING
                    }
                    out += RecentCall(c.getString(1).orEmpty(), number, kind, c.getLong(3))
                }
            }
        }.onFailure { DebugLog.w("RecentCalls") { "Call log read failed: ${it.javaClass.simpleName}" } }
        return out
    }

    /** Name the call log remembers for [number], if any. */
    fun nameFor(context: Context, number: String): String? {
        if (number.isBlank() || !hasPermission(context)) return null
        val tail = number.filter { it.isDigit() }.takeLast(10)
        if (tail.length < 6) return null
        return runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.CACHED_NAME),
                CallLog.Calls.NUMBER + " LIKE ? AND " + CallLog.Calls.CACHED_NAME + " != ''",
                arrayOf("%$tail"), CallLog.Calls.DATE + " DESC",
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
