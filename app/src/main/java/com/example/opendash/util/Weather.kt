package com.example.opendash.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WeatherNow(val tempC: Int, val label: String, val wet: Boolean, val atMs: Long)

/** Current weather at the rider's position from Open-Meteo (free, no key). */
object Weather {
    private val _now = MutableStateFlow<WeatherNow?>(null)
    val now = _now.asStateFlow()

    /** Only weather newer than 90 minutes is worth showing. */
    fun fresh(nowMs: Long = System.currentTimeMillis()): WeatherNow? =
        _now.value?.takeIf { nowMs - it.atMs < 90 * 60_000L }

    /** Blocking; call from a background thread. Position is rounded to about 1 km. */
    fun refresh(lat: Double, lng: Double) {
        runCatching {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f".format(java.util.Locale.US, lat, lng) +
                    "&current=temperature_2m,weather_code"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000; readTimeout = 6000
            }
            try {
                if (conn.responseCode != 200) return
                val cur = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    .getJSONObject("current")
                val code = cur.optInt("weather_code", -1)
                _now.value = WeatherNow(
                    Math.round(cur.getDouble("temperature_2m")).toInt(),
                    label(code), isWet(code), System.currentTimeMillis(),
                )
            } finally { conn.disconnect() }
        }.onFailure { DebugLog.w("Weather") { "Weather fetch failed: ${it.javaClass.simpleName}" } }
    }

    /** WMO weather code → one short word. */
    fun label(code: Int): String = when (code) {
        0, 1 -> "Clear"
        2 -> "Cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        95, 96, 99 -> "Storm"
        else -> ""
    }

    fun isWet(code: Int): Boolean = code in 51..67 || code in 80..82 || code in 95..99
}
