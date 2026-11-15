package com.example.opendash.voice

/** One spoken instruction, already understood. */
sealed interface VoiceCommand {
    data class NavigateTo(val place: String) : VoiceCommand
    data object StopNavigation : VoiceCommand
    data object NextTrack : VoiceCommand
    data object PreviousTrack : VoiceCommand
    data object Pause : VoiceCommand
    data object Play : VoiceCommand
    data object VolumeUp : VoiceCommand
    data object VolumeDown : VoiceCommand
    data object ZoomIn : VoiceCommand
    data object ZoomOut : VoiceCommand
    data object MuteGuidance : VoiceCommand
    data object UnmuteGuidance : VoiceCommand
    data object HowFar : VoiceCommand
    data object AnswerCall : VoiceCommand
    data object RejectCall : VoiceCommand
    data class Unknown(val heard: String) : VoiceCommand
}

/**
 * Turns recognised speech into a [VoiceCommand]. Deliberately a small fixed phrase
 * list: short commands survive helmet wind noise far better than free-form speech.
 * Pure Kotlin so it is unit-tested without a device.
 */
object VoiceCommandParser {
    private val navPrefixes = listOf(
        "navigate to", "navigation to", "directions to", "take me to", "go to",
        "drive to", "ride to", "route to",
    )

    fun parse(raw: String): VoiceCommand {
        val text = raw.lowercase().trim().trimEnd('.', '!', '?').replace(Regex("\\s+"), " ")
        if (text.isBlank()) return VoiceCommand.Unknown(raw)

        for (prefix in navPrefixes) {
            val at = text.indexOf(prefix)
            if (at >= 0) {
                val place = text.substring(at + prefix.length).trim()
                if (place.isNotBlank()) return VoiceCommand.NavigateTo(place)
            }
        }
        fun has(vararg words: String) = words.any { text.contains(it) }

        return when {
            has("stop navigation", "cancel navigation", "end navigation", "stop route",
                "cancel route", "exit navigation") -> VoiceCommand.StopNavigation
            has("unmute", "voice on", "directions on") -> VoiceCommand.UnmuteGuidance
            has("mute", "voice off", "directions off", "be quiet") -> VoiceCommand.MuteGuidance
            has("answer", "pick up", "accept call") -> VoiceCommand.AnswerCall
            has("reject", "decline", "hang up", "end call") -> VoiceCommand.RejectCall
            has("next song", "next track", "skip") || text == "next" -> VoiceCommand.NextTrack
            has("previous song", "previous track", "last song", "go back") ||
                text == "previous" -> VoiceCommand.PreviousTrack
            has("volume up", "louder", "increase volume") -> VoiceCommand.VolumeUp
            has("volume down", "quieter", "softer", "decrease volume", "lower volume") -> VoiceCommand.VolumeDown
            has("zoom in") -> VoiceCommand.ZoomIn
            has("zoom out") -> VoiceCommand.ZoomOut
            has("pause", "stop music", "stop song") -> VoiceCommand.Pause
            has("play", "resume") -> VoiceCommand.Play
            has("how far", "how long", "distance", "eta", "time left", "when will") -> VoiceCommand.HowFar
            else -> VoiceCommand.Unknown(raw)
        }
    }
}
