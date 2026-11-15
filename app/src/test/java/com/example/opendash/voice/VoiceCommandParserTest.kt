package com.example.opendash.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {
    private fun p(s: String) = VoiceCommandParser.parse(s)

    @Test fun navigation() {
        assertEquals(VoiceCommand.NavigateTo("home"), p("Navigate to home"))
        assertEquals(VoiceCommand.NavigateTo("lonavala lake"), p("hey take me to Lonavala Lake."))
        assertEquals(VoiceCommand.StopNavigation, p("stop navigation"))
        assertEquals(VoiceCommand.StopNavigation, p("cancel route"))
    }

    @Test fun music() {
        assertEquals(VoiceCommand.NextTrack, p("next song"))
        assertEquals(VoiceCommand.NextTrack, p("Next"))
        assertEquals(VoiceCommand.PreviousTrack, p("previous track"))
        assertEquals(VoiceCommand.Pause, p("pause"))
        assertEquals(VoiceCommand.Pause, p("stop music"))
        assertEquals(VoiceCommand.Play, p("play"))
        assertEquals(VoiceCommand.VolumeUp, p("volume up"))
        assertEquals(VoiceCommand.VolumeDown, p("lower volume"))
    }

    @Test fun guidanceAndMap() {
        assertEquals(VoiceCommand.MuteGuidance, p("mute directions"))
        assertEquals(VoiceCommand.UnmuteGuidance, p("unmute"))
        assertEquals(VoiceCommand.ZoomIn, p("zoom in"))
        assertEquals(VoiceCommand.ZoomOut, p("zoom out"))
        assertEquals(VoiceCommand.HowFar, p("how far is it"))
    }

    @Test fun calls() {
        assertEquals(VoiceCommand.AnswerCall, p("answer"))
        assertEquals(VoiceCommand.RejectCall, p("reject call"))
    }

    @Test fun unknown() {
        assertTrue(p("purple monkey") is VoiceCommand.Unknown)
        assertTrue(p("  ") is VoiceCommand.Unknown)
        // A bare "navigate to" with no place is not a destination.
        assertTrue(p("navigate to") is VoiceCommand.Unknown)
    }

    @Test fun savedPlaceParsing() {
        val place = PlaceResolver.parse("home", "18.5204, 73.8567")!!
        assertEquals("Home", place.name)
        assertEquals(18.5204, place.lat, 1e-9)
        assertNull(PlaceResolver.parse("x", "91,0"))
        assertNull(PlaceResolver.parse("x", "abc"))
        assertNull(PlaceResolver.parse("x", "1,2,3"))
    }
}
