package com.example.opendash.dash

import android.content.Context

/** Rider switches for the extra things drawn on the dash. */
class DashExtras private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dash_extras", Context.MODE_PRIVATE)

    var showMessages: Boolean
        get() = prefs.getBoolean("messages", true)
        set(v) = prefs.edit().putBoolean("messages", v).apply()
    /** Show the message text too, not just who it is from. */
    var showMessageText: Boolean
        get() = prefs.getBoolean("message_text", true)
        set(v) = prefs.edit().putBoolean("message_text", v).apply()
    var showWeather: Boolean
        get() = prefs.getBoolean("weather", true)
        set(v) = prefs.edit().putBoolean("weather", v).apply()
    var showSpeedWarning: Boolean
        get() = prefs.getBoolean("speed_warning", true)
        set(v) = prefs.edit().putBoolean("speed_warning", v).apply()

    companion object {
        @Volatile private var instance: DashExtras? = null
        fun get(context: Context): DashExtras =
            instance ?: synchronized(this) { instance ?: DashExtras(context).also { instance = it } }
    }
}
