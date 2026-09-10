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

data class WeatherData(
    val temperatureC: Double,
    val weatherCode: Int,
    val cityName: String?,
    val latitude: Double,
    val longitude: Double,
    val fetchedAt: Long
)

data class DailyForecast(
    val date: String,
    val weatherCode: Int,
    val tempMaxC: Double,
    val tempMinC: Double,
    val precipitationProbability: Int
)

data class WeatherDetails(
    val temperatureC: Double,
    val feelsLikeC: Double,
    val weatherCode: Int,
    val humidity: Int,
    val windSpeedKmh: Double,
    val precipitationMm: Double,
    val cityName: String?,
    val daily: List<DailyForecast>
)

object WeatherHelper {
    private const val PREFS = "weather_prefs"
    private const val KEY_TEMP = "temp_c"
    private const val KEY_CODE = "weather_code"
    private const val KEY_CITY = "city_name"
    private const val KEY_LAT = "latitude"
    private const val KEY_LON = "longitude"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val CACHE_TTL_MS = 30L * 60 * 1000

    fun getCached(context: Context): WeatherData? {
        val prefs = prefs(context)
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        if (fetchedAt == 0L) return null
        return WeatherData(
            temperatureC = prefs.getFloat(KEY_TEMP, 0f).toDouble(),
            weatherCode = prefs.getInt(KEY_CODE, 0),
            cityName = prefs.getString(KEY_CITY, null),
            latitude = prefs.getFloat(KEY_LAT, 0f).toDouble(),
            longitude = prefs.getFloat(KEY_LON, 0f).toDouble(),
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
                val cityName = fetchCityName(latitude, longitude)
                val data = WeatherData(
                    temperatureC = current.getDouble("temperature_2m"),
                    weatherCode = current.getInt("weather_code"),
                    cityName = cityName,
                    latitude = latitude,
                    longitude = longitude,
                    fetchedAt = System.currentTimeMillis()
                )
                saveCache(context, data)
                data
            }.getOrNull()
        }
    }

    suspend fun fetchWeatherDetails(context: Context, latitude: Double, longitude: Double): WeatherDetails? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                        "&current=temperature_2m,weather_code,relative_humidity_2m,apparent_temperature," +
                        "wind_speed_10m,precipitation" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                        "&timezone=auto&temperature_unit=celsius"
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.requestMethod = "GET"
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(body)
                val current = json.getJSONObject("current")
                val daily = json.getJSONObject("daily")
                val dates = daily.getJSONArray("time")
                val codes = daily.getJSONArray("weather_code")
                val maxTemps = daily.getJSONArray("temperature_2m_max")
                val minTemps = daily.getJSONArray("temperature_2m_min")
                val precipProbabilities = daily.getJSONArray("precipitation_probability_max")

                val forecasts = (0 until dates.length()).map { i ->
                    DailyForecast(
                        date = dates.getString(i),
                        weatherCode = codes.getInt(i),
                        tempMaxC = maxTemps.getDouble(i),
                        tempMinC = minTemps.getDouble(i),
                        precipitationProbability = precipProbabilities.optInt(i, 0)
                    )
                }

                WeatherDetails(
                    temperatureC = current.getDouble("temperature_2m"),
                    feelsLikeC = current.getDouble("apparent_temperature"),
                    weatherCode = current.getInt("weather_code"),
                    humidity = current.getInt("relative_humidity_2m"),
                    windSpeedKmh = current.getDouble("wind_speed_10m"),
                    precipitationMm = current.getDouble("precipitation"),
                    cityName = fetchCityName(latitude, longitude),
                    daily = forecasts
                )
            }.getOrNull()
        }
    }

    private fun fetchCityName(latitude: Double, longitude: Double): String? {
        return runCatching {
            val url = URL(
                "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$latitude" +
                    "&longitude=$longitude&localityLanguage=en"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(body)
            listOf("city", "locality", "principalSubdivision")
                .map { json.optString(it) }
                .firstOrNull { it.isNotBlank() }
        }.getOrNull()
    }

    private fun saveCache(context: Context, data: WeatherData) {
        prefs(context).edit()
            .putFloat(KEY_TEMP, data.temperatureC.toFloat())
            .putInt(KEY_CODE, data.weatherCode)
            .putString(KEY_CITY, data.cityName)
            .putFloat(KEY_LAT, data.latitude.toFloat())
            .putFloat(KEY_LON, data.longitude.toFloat())
            .putLong(KEY_FETCHED_AT, data.fetchedAt)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
