package com.zero.assistant.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale

class LiveWeatherService(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun forecast(spokenCity: String? = null): String {
        val city = spokenCity?.trim()?.takeIf(String::isNotEmpty)
        val query = city ?: currentLocationLabel()
            ?: return "Say a city name for the weather, or grant ZERO location permission to use your current location."
        return try {
            val coordinates = geocode(query)
            val current = fetchCurrentWeather(coordinates.first, coordinates.second)
            val result = "$query: ${current.first}, ${current.second}°C."
            preferences.edit().putString(CACHE_CITY, query.lowercase(Locale.ROOT))
                .putString(CACHE_RESULT, result).apply()
            result
        } catch (failure: Exception) {
            val cached = preferences.getString(CACHE_RESULT, null)
            val cachedCity = preferences.getString(CACHE_CITY, null)
            if (cached != null && (cachedCity == query.lowercase(Locale.ROOT) || city == null)) {
                "$cached (offline cached forecast; live update unavailable)"
            } else {
                "Live weather is unavailable right now: ${failure.message ?: "network request failed"}."
            }
        }
    }

    private fun currentLocationLabel(): String? {
        val hasLocation = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) return null
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return null
        val location = manager.getProviders(true).asSequence()
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
            ?: return null
        return "${location.latitude},${location.longitude}"
    }

    private fun geocode(query: String): Pair<Double, Double> {
        if (query.matches(Regex("-?\\d{1,2}\\.\\d+,-?\\d{1,3}\\.\\d+"))) {
            val parts = query.split(',')
            return parts[0].toDouble() to parts[1].toDouble()
        }
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val json = requestJson(
            "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json",
        )
        val result = json.optJSONArray("results")?.optJSONObject(0)
            ?: throw IllegalStateException("No matching city was found.")
        return result.getDouble("latitude") to result.getDouble("longitude")
    }

    private fun fetchCurrentWeather(latitude: Double, longitude: Double): Pair<String, Double> {
        val json = requestJson(
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code&timezone=auto",
        )
        val current = json.getJSONObject("current")
        val description = weatherDescription(current.getInt("weather_code"))
        return description to current.getDouble("temperature_2m")
    }

    private fun requestJson(address: String): JSONObject {
        val url = URL(address)
        require(url.protocol == "https") { "Weather service must use HTTPS." }
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IllegalStateException("Weather service returned HTTP $code.")
            if (connection.contentLengthLong > MAX_RESPONSE_BYTES) {
                throw IllegalStateException("Weather response exceeded the size limit.")
            }
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_RESPONSE_BYTES) {
                        throw IllegalStateException("Weather response exceeded the size limit.")
                    }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "clear"
        1, 2 -> "mostly clear"
        3 -> "cloudy"
        45, 48 -> "foggy"
        51, 53, 55, 56, 57 -> "drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "rain"
        71, 73, 75, 77, 85, 86 -> "snow"
        95, 96, 99 -> "thunderstorms"
        else -> "weather code $code"
    }

    private companion object {
        const val PREFERENCES = "zero_weather"
        const val CACHE_CITY = "cached_city"
        const val CACHE_RESULT = "cached_result"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
        const val MAX_RESPONSE_BYTES = 64 * 1024
    }
}
