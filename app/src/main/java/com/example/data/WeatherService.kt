package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WeatherService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWeatherByCoords(
        lat: Double,
        lon: Double,
        cityLabel: String = "Local Forecast"
    ): Result<WeatherSnapshot> = withContext(Dispatchers.IO) {
        try {
            val url =
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code&timezone=auto"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Weather service error (${resp.code})"))
                }
                val body = resp.body?.string() ?: return@withContext Result.failure(Exception("Empty weather response"))
                val current = JSONObject(body).getJSONObject("current")
                val tempC = current.optDouble("temperature_2m", 16.0).toInt()
                val humidity = current.optInt("relative_humidity_2m", 55)
                val wind = current.optDouble("wind_speed_10m", 10.0).toInt()
                val code = current.optInt("weather_code", 2)
                val condition = mapWmoCode(code, tempC, wind, humidity)
                Result.success(
                    WeatherSnapshot(
                        city = cityLabel,
                        tempC = tempC,
                        condition = condition,
                        windKmh = wind,
                        humidityPct = humidity
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchCityAndFetchWeather(cityQuery: String): Result<WeatherSnapshot> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(cityQuery.trim(), "UTF-8")
                val geoUrl =
                    "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
                val geoReq = Request.Builder().url(geoUrl).get().build()
                val (lat, lon, label) = client.newCall(geoReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(Exception("City lookup failed (${resp.code})"))
                    }
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val results = json.optJSONArray("results")
                    if (results == null || results.length() == 0) {
                        return@withContext Result.failure(Exception("City \"$cityQuery\" not found."))
                    }
                    val first = results.getJSONObject(0)
                    val name = first.optString("name", cityQuery)
                    val cc = first.optString("country_code", "")
                    val display = if (cc.isNotBlank()) "$name, $cc" else name
                    Triple(first.getDouble("latitude"), first.getDouble("longitude"), display)
                }
                fetchWeatherByCoords(lat, lon, label)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun mapWmoCode(code: Int, tempC: Int, windKmh: Int, humidity: Int): WeatherCondition =
        when {
            code in listOf(71, 73, 75, 77, 85, 86) || tempC <= 0 -> WeatherCondition.SNOWY
            code in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99) -> WeatherCondition.RAINY
            windKmh >= 32 -> WeatherCondition.WINDY
            tempC >= 28 && humidity >= 65 -> WeatherCondition.HUMID
            code in listOf(0, 1) -> WeatherCondition.SUNNY
            else -> WeatherCondition.CLOUDY
        }
}
