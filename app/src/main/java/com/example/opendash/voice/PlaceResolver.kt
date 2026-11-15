package com.example.opendash.voice

import android.content.Context
import android.location.Geocoder
import java.util.Locale

data class ResolvedPlace(val name: String, val lat: Double, val lng: Double)

/**
 * Finds coordinates for a spoken place. Saved places ("home", "work", or any name the
 * rider saved in Settings) win, so the common trips work with no signal. Anything else
 * goes to the phone's geocoder, which needs internet.
 */
object PlaceResolver {
    private const val PREFS = "saved_places"

    fun saved(context: Context): Map<String, ResolvedPlace> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, value) ->
            parse(key, value as? String ?: return@mapNotNull null)?.let { key to it }
        }.toMap()
    }

    /** [latLng] is "lat,lng". Returns false if it cannot be read. */
    fun save(context: Context, name: String, latLng: String): Boolean {
        val key = name.lowercase().trim()
        if (key.isBlank() || parse(key, latLng) == null) return false
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, latLng.trim()).apply()
        return true
    }

    fun remove(context: Context, name: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(name.lowercase().trim()).apply()
    }

    internal fun parse(name: String, latLng: String): ResolvedPlace? {
        val parts = latLng.split(',').map { it.trim().toDoubleOrNull() }
        val lat = parts.getOrNull(0) ?: return null
        val lng = parts.getOrNull(1) ?: return null
        if (parts.size != 2 || lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return ResolvedPlace(name.replaceFirstChar { it.uppercase() }, lat, lng)
    }

    /** Blocking (geocoder does network I/O): call off the main thread. */
    fun resolve(context: Context, spoken: String, nearLat: Double?, nearLng: Double?): ResolvedPlace? {
        val key = spoken.lowercase().trim().removePrefix("my ").trim()
        saved(context)[key]?.let { return it }
        if (!Geocoder.isPresent()) return null
        return runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val results = if (nearLat != null && nearLng != null) {
                // Bias towards places within roughly 150 km of the rider.
                geocoder.getFromLocationName(
                    spoken, 1, nearLat - 1.5, nearLng - 1.5, nearLat + 1.5, nearLng + 1.5,
                ).orEmpty().ifEmpty { geocoder.getFromLocationName(spoken, 1).orEmpty() }
            } else geocoder.getFromLocationName(spoken, 1).orEmpty()
            results.firstOrNull()?.let {
                ResolvedPlace(it.featureName?.takeIf { n -> n.any(Char::isLetter) } ?: spoken, it.latitude, it.longitude)
            }
        }.getOrNull()
    }
}
