package br.com.pilovieira.launcher

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

data class WeatherData(val temperatureC: Double, val weatherCode: Int, val fetchedAt: Long)

object WeatherHelper {
    private const val PREFS = "weather_prefs"
    private const val KEY_TEMP = "temp_c"
    private const val KEY_CODE = "weather_code"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val CACHE_TTL_MS = 30L * 60 * 1000

    fun getCached(context: Context): WeatherData? {
        val prefs = prefs(context)
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        if (fetchedAt == 0L) return null
        return WeatherData(
            temperatureC = prefs.getFloat(KEY_TEMP, 0f).toDouble(),
            weatherCode = prefs.getInt(KEY_CODE, 0),
            fetchedAt = fetchedAt
        )
    }

    fun isStale(data: WeatherData?): Boolean {
        if (data == null) return true
        return System.currentTimeMillis() - data.fetchedAt > CACHE_TTL_MS
    }

    fun getLastKnownLocation(context: Context): Pair<Double, Double>? {
        return runCatching {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null
            var best: Location? = null
            for (provider in locationManager.getProviders(true)) {
                val location = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() ?: continue
                val currentBest = best
                if (currentBest == null || location.accuracy < currentBest.accuracy) {
                    best = location
                }
            }
            best?.let { it.latitude to it.longitude }
        }.getOrNull()
    }

    /** Actively requests a single location fix, for when no last-known location is cached yet. */
    suspend fun requestFreshLocation(context: Context, timeoutMs: Long = 10_000): Pair<Double, Double>? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        return withContext(Dispatchers.Main) {
            runCatching {
                suspendCancellableCoroutine<Pair<Double, Double>?> { continuation ->
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (continuation.isActive) continuation.resume(location.latitude to location.longitude)
                            locationManager.removeUpdates(this)
                        }
                    }
                    continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())

                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        if (continuation.isActive) {
                            locationManager.removeUpdates(listener)
                            continuation.resume(null)
                        }
                    }, timeoutMs)
                }
            }.getOrNull()
        }
    }

    suspend fun fetchWeather(context: Context, latitude: Double, longitude: Double): WeatherData? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                        "&current=temperature_2m,weather_code&temperature_unit=celsius"
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.requestMethod = "GET"
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val current = JSONObject(body).getJSONObject("current")
                val data = WeatherData(
                    temperatureC = current.getDouble("temperature_2m"),
                    weatherCode = current.getInt("weather_code"),
                    fetchedAt = System.currentTimeMillis()
                )
                saveCache(context, data)
                data
            }.getOrNull()
        }
    }

    private fun saveCache(context: Context, data: WeatherData) {
        prefs(context).edit()
            .putFloat(KEY_TEMP, data.temperatureC.toFloat())
            .putInt(KEY_CODE, data.weatherCode)
            .putLong(KEY_FETCHED_AT, data.fetchedAt)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
