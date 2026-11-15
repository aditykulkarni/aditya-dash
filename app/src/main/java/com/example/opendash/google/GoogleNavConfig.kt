package com.example.opendash.google

import android.content.Context

/** Which engine draws the dash map and provides guidance. */
enum class MapEngine { GOOGLE, OFFLINE }

/**
 * Rider-supplied Google Navigation SDK settings, stored on-device only.
 *
 * The API key is the rider's own (Google Cloud project with the Navigation SDK
 * enabled). It is entered in Settings and is never part of the source or the APK.
 */
class GoogleNavConfig private constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("google_nav", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_API, v.trim()).apply()

    var engine: MapEngine
        get() = runCatching { MapEngine.valueOf(prefs.getString(KEY_ENGINE, null) ?: "") }
            .getOrDefault(MapEngine.GOOGLE)
        set(v) = prefs.edit().putString(KEY_ENGINE, v.name).apply()

    /** Night colours on the dash map: easier on the eyes and on the TFT in the dark. */
    var nightMap: Boolean
        get() = prefs.getBoolean(KEY_NIGHT, false)
        set(v) = prefs.edit().putBoolean(KEY_NIGHT, v).apply()

    val hasKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        private const val KEY_API = "api_key"
        private const val KEY_ENGINE = "engine"
        private const val KEY_NIGHT = "night_map"

        @Volatile private var instance: GoogleNavConfig? = null
        fun get(context: Context): GoogleNavConfig =
            instance ?: synchronized(this) {
                instance ?: GoogleNavConfig(context).also { instance = it }
            }
    }
}
