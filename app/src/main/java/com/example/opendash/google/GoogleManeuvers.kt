package com.example.opendash.google

import com.google.android.libraries.mapsplatform.turnbyturn.model.Maneuver

/** Short, glanceable wording for a Navigation SDK maneuver code. */
object GoogleManeuvers {
    fun phrase(maneuver: Int): String = when (maneuver) {
        Maneuver.DEPART -> "Start"
        Maneuver.DESTINATION, Maneuver.DESTINATION_LEFT, Maneuver.DESTINATION_RIGHT -> "Arrive"
        Maneuver.STRAIGHT, Maneuver.NAME_CHANGE -> "Straight"
        Maneuver.TURN_LEFT, Maneuver.ON_RAMP_LEFT, Maneuver.OFF_RAMP_LEFT -> "Turn left"
        Maneuver.TURN_RIGHT, Maneuver.ON_RAMP_RIGHT, Maneuver.OFF_RAMP_RIGHT -> "Turn right"
        Maneuver.TURN_KEEP_LEFT, Maneuver.FORK_LEFT, Maneuver.ON_RAMP_KEEP_LEFT,
        Maneuver.OFF_RAMP_KEEP_LEFT, Maneuver.MERGE_LEFT -> "Keep left"
        Maneuver.TURN_KEEP_RIGHT, Maneuver.FORK_RIGHT, Maneuver.ON_RAMP_KEEP_RIGHT,
        Maneuver.OFF_RAMP_KEEP_RIGHT, Maneuver.MERGE_RIGHT -> "Keep right"
        Maneuver.TURN_SLIGHT_LEFT, Maneuver.ON_RAMP_SLIGHT_LEFT, Maneuver.OFF_RAMP_SLIGHT_LEFT -> "Slight left"
        Maneuver.TURN_SLIGHT_RIGHT, Maneuver.ON_RAMP_SLIGHT_RIGHT, Maneuver.OFF_RAMP_SLIGHT_RIGHT -> "Slight right"
        Maneuver.TURN_SHARP_LEFT, Maneuver.ON_RAMP_SHARP_LEFT, Maneuver.OFF_RAMP_SHARP_LEFT -> "Sharp left"
        Maneuver.TURN_SHARP_RIGHT, Maneuver.ON_RAMP_SHARP_RIGHT, Maneuver.OFF_RAMP_SHARP_RIGHT -> "Sharp right"
        Maneuver.TURN_U_TURN_CLOCKWISE, Maneuver.TURN_U_TURN_COUNTERCLOCKWISE,
        Maneuver.ON_RAMP_U_TURN_CLOCKWISE, Maneuver.ON_RAMP_U_TURN_COUNTERCLOCKWISE,
        Maneuver.OFF_RAMP_U_TURN_CLOCKWISE, Maneuver.OFF_RAMP_U_TURN_COUNTERCLOCKWISE -> "U-turn"
        Maneuver.MERGE_UNSPECIFIED -> "Merge"
        Maneuver.ON_RAMP_UNSPECIFIED -> "Take the ramp"
        Maneuver.OFF_RAMP_UNSPECIFIED -> "Take the exit"
        Maneuver.FERRY_BOAT, Maneuver.FERRY_TRAIN -> "Ferry"
        in Maneuver.ROUNDABOUT_CLOCKWISE..Maneuver.ROUNDABOUT_EXIT_COUNTERCLOCKWISE -> "Roundabout"
        else -> "Continue"
    }

    /** "350 m" / "1.2 km" */
    fun distance(meters: Int): String =
        if (meters < 1000) "${(meters / 10) * 10} m" else "%.1f km".format(meters / 1000.0)
}
