package com.example.opendash.dash

/** One entry from the phone's call history. */
data class RecentCall(val name: String, val number: String, val kind: Kind, val whenMs: Long) {
    enum class Kind { INCOMING, OUTGOING, MISSED }
    val label: String get() = name.ifBlank { number }
}

/**
 * The small menu drawn on the dash and driven by the joystick: DOWN opens it, UP/DOWN
 * move, RIGHT (or press-in) chooses, LEFT (or BACK) goes back. Pure state, no Android.
 */
class DashMenu {
    enum class Page { CLOSED, ROOT, CALLS, CONFIRM }

    sealed class Action {
        object None : Action()
        object LoadCalls : Action()
        object PlayPause : Action()
        object Voice : Action()
        object StopNavigation : Action()
        data class Dial(val call: RecentCall) : Action()
    }

    data class Row(val left: String, val right: String = "")

    var page = Page.CLOSED; private set
    var index = 0; private set
    var calls: List<RecentCall> = emptyList(); private set
    var callsNote: String? = null; private set
    private var navActive = false
    private var pending: RecentCall? = null

    val isOpen: Boolean get() = page != Page.CLOSED

    private fun rootItems(): List<Pair<String, Action>> = buildList {
        add("Recent calls" to Action.LoadCalls)
        add("Play / pause music" to Action.PlayPause)
        add("Voice command" to Action.Voice)
        if (navActive) add("Stop navigation" to Action.StopNavigation)
    }

    fun open(navActive: Boolean) {
        this.navActive = navActive
        page = Page.ROOT; index = 0; pending = null
    }

    fun close() { page = Page.CLOSED; index = 0; pending = null }

    fun setCalls(list: List<RecentCall>, note: String? = null) {
        calls = list; callsNote = note
        if (page == Page.CALLS) index = index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    }

    private fun count(): Int = when (page) {
        Page.ROOT -> rootItems().size
        Page.CALLS -> calls.size
        else -> 0
    }

    fun move(delta: Int) {
        val n = count()
        if (n > 0) index = ((index + delta) % n + n) % n
    }

    /** RIGHT / press-in. Returns what the app should do. */
    fun select(): Action = when (page) {
        Page.ROOT -> {
            val action = rootItems().getOrNull(index)?.second ?: Action.None
            if (action == Action.LoadCalls) { page = Page.CALLS; index = 0 } else close()
            action
        }
        Page.CALLS -> {
            calls.getOrNull(index)?.let { pending = it; page = Page.CONFIRM }
            Action.None
        }
        Page.CONFIRM -> {
            val call = pending
            close()
            if (call != null) Action.Dial(call) else Action.None
        }
        Page.CLOSED -> Action.None
    }

    /** LEFT / BACK. */
    fun back() {
        when (page) {
            Page.CONFIRM -> { page = Page.CALLS; pending = null }
            Page.CALLS -> { page = Page.ROOT; index = 0 }
            else -> close()
        }
    }

    fun title(): String = when (page) {
        Page.ROOT -> "Menu"
        Page.CALLS -> "Recent calls"
        Page.CONFIRM -> "Call ${pending?.label.orEmpty()}?"
        Page.CLOSED -> ""
    }

    fun hint(): String = when (page) {
        Page.CONFIRM -> "RIGHT call  |  LEFT cancel"
        Page.CALLS -> if (calls.isEmpty()) (callsNote ?: "No recent calls") else "RIGHT select  |  LEFT back"
        else -> "RIGHT select  |  LEFT close"
    }

    fun rows(nowMs: Long): List<Row> = when (page) {
        Page.ROOT -> rootItems().map { Row(it.first) }
        Page.CALLS -> calls.map { c ->
            val mark = when (c.kind) {
                RecentCall.Kind.MISSED -> "missed "
                RecentCall.Kind.OUTGOING -> "out "
                RecentCall.Kind.INCOMING -> "in "
            }
            Row(c.label, mark + age(nowMs - c.whenMs))
        }
        Page.CONFIRM -> listOf(Row(pending?.number.orEmpty()))
        Page.CLOSED -> emptyList()
    }

    /** Changes whenever the picture of the menu would change. */
    fun signature(): String = "$page:$index:${calls.size}:${callsNote.orEmpty()}"

    companion object {
        fun age(ms: Long): String {
            val min = (ms / 60_000L).coerceAtLeast(0)
            return when {
                min < 1 -> "now"
                min < 60 -> "${min}m"
                min < 24 * 60 -> "${min / 60}h"
                else -> "${min / (24 * 60)}d"
            }
        }
    }
}
