package com.example.opendash.dash

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the dash has sent the phone, kept in the app so the rider can see it without a
 * computer. Every packet is counted; anything other than the routine "frame decoded"
 * acknowledgements is listed in full, newest first. Used to map joystick buttons.
 */
object DashEventLog {
    private const val MAX = 60
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    data class Stats(val packets: Long = 0, val acks: Long = 0, val buttons: Long = 0, val other: Long = 0)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines = _lines.asStateFlow()
    private val _stats = MutableStateFlow(Stats())
    val stats = _stats.asStateFlow()

    @Synchronized fun packet() { _stats.value = _stats.value.copy(packets = _stats.value.packets + 1) }
    @Synchronized fun ack() { _stats.value = _stats.value.copy(acks = _stats.value.acks + 1) }

    @Synchronized fun button(code: Int, raw: String) {
        _stats.value = _stats.value.copy(buttons = _stats.value.buttons + 1)
        add("BUTTON 0x%02X  (09 00 %s)".format(code, raw))
    }

    @Synchronized fun event(type: Int, sub: Int, raw: String) {
        _stats.value = _stats.value.copy(other = _stats.value.other + 1)
        add("%02X %02X  %s".format(type, sub, raw.take(120)))
    }

    @Synchronized fun note(text: String) = add(text)

    @Synchronized fun clear() { _lines.value = emptyList(); _stats.value = Stats() }

    private fun add(text: String) {
        _lines.value = (listOf(time.format(Date()) + "  " + text) + _lines.value).take(MAX)
    }

    fun asText(): String =
        "Aditya Dash event log\n" + _stats.value.let {
            "packets=${it.packets} acks=${it.acks} buttons=${it.buttons} other=${it.other}\n"
        } + _lines.value.joinToString("\n")
}
