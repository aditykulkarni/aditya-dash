package com.example.opendash.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A message notification (SMS, WhatsApp, …) to flash on the dash for a few seconds. */
data class DashMessage(val sender: String, val text: String, val atMs: Long)

object MessageInfoProvider {
    const val SHOW_MS = 10_000L
    private val _latest = MutableStateFlow<DashMessage?>(null)
    val latest = _latest.asStateFlow()

    fun post(sender: String, text: String) {
        _latest.value = DashMessage(sender, text, System.currentTimeMillis())
    }

    /** The message to draw right now, or null once it has been up long enough. */
    fun visible(nowMs: Long = System.currentTimeMillis()): DashMessage? =
        _latest.value?.takeIf { nowMs - it.atMs < SHOW_MS }
}
