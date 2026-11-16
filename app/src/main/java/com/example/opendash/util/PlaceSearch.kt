package com.example.opendash.util

import android.content.Context
import android.location.Geocoder
import com.example.opendash.viewmodel.PlaceHit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos

/**
 * Destination search without any extra API key: the phone's own geocoder first (Google's
 * on most phones), then the open Photon index (OpenStreetMap data) for shops and
 * landmarks the geocoder misses. Blocking; call from a background thread.
 */
object PlaceSearch {
    private const val TAG = "PlaceSearch"

    fun search(context: Context, query: String, nearLat: Double?, nearLng: Double?): List<PlaceHit> {
        val hits = ArrayList<PlaceHit>()
        hits += geocoder(context, query, nearLat, nearLng)
        hits += photon(query, nearLat, nearLng)
        val unique = hits.distinctBy { "%.4f,%.4f".format(it.lat, it.lng) }
        return if (nearLat != null && nearLng != null) {
            unique.sortedBy { roughKm(nearLat, nearLng, it.lat, it.lng) }.take(8)
        } else unique.take(8)
    }

    fun parsePhoton(json: String): List<PlaceHit> {
        val out = ArrayList<PlaceHit>()
        val features = JSONObject(json).optJSONArray("features") ?: return out
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue
            val p = f.optJSONObject("properties") ?: continue
            val street = listOf(p.optString("housenumber"), p.optString("street"))
                .filter { it.isNotBlank() }.joinToString(" ")
            val name = p.optString("name").ifBlank { street }.ifBlank { p.optString("city") }
            if (name.isBlank()) continue
            val detail = listOf(
                street.takeIf { it != name }.orEmpty(),
                p.optString("district"), p.optString("city"), p.optString("state"),
            ).filter { it.isNotBlank() && it != name }.distinct().joinToString(", ")
            out += PlaceHit(name, detail, coords.getDouble(1), coords.getDouble(0))
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun geocoder(context: Context, query: String, nearLat: Double?, nearLng: Double?): List<PlaceHit> =
        runCatching {
            if (!Geocoder.isPresent()) return emptyList()
            val g = Geocoder(context, Locale.getDefault())
            val found = if (nearLat != null && nearLng != null) {
                // Prefer matches within roughly 150 km, then anywhere.
                g.getFromLocationName(
                    query, 5,
                    (nearLat - 1.4).coerceAtLeast(-90.0), (nearLng - 1.4).coerceAtLeast(-180.0),
                    (nearLat + 1.4).coerceAtMost(90.0), (nearLng + 1.4).coerceAtMost(180.0),
                ).orEmpty().ifEmpty { g.getFromLocationName(query, 5).orEmpty() }
            } else g.getFromLocationName(query, 5).orEmpty()
            found.filter { it.hasLatitude() && it.hasLongitude() }.map { a ->
                val line = a.getAddressLine(0).orEmpty()
                val name = a.featureName?.takeIf { it.isNotBlank() && !it.first().isDigit() }
                    ?: line.substringBefore(',').ifBlank { query }
                PlaceHit(name, line, a.latitude, a.longitude)
            }
        }.onFailure { DebugLog.w(TAG) { "Geocoder search failed: ${it.javaClass.simpleName}" } }
            .getOrDefault(emptyList())

    private fun photon(query: String, nearLat: Double?, nearLng: Double?): List<PlaceHit> = runCatching {
        val bias = if (nearLat != null && nearLng != null) "&lat=$nearLat&lon=$nearLng" else ""
        val url = URL(
            "https://photon.komoot.io/api/?limit=6&lang=en&q=" +
                URLEncoder.encode(query, "UTF-8") + bias
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000; readTimeout = 6000
            setRequestProperty("User-Agent", "AdityaDash/1.0 (personal motorcycle dash app)")
        }
        try {
            if (conn.responseCode != 200) return emptyList()
            parsePhoton(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }.onFailure { DebugLog.w(TAG) { "Photon search failed: ${it.javaClass.simpleName}" } }
        .getOrDefault(emptyList())

    private fun roughKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dy = (lat2 - lat1) * 111.0
        val dx = (lng2 - lng1) * 111.0 * cos(Math.toRadians(lat1))
        return Math.sqrt(dx * dx + dy * dy)
    }
}
