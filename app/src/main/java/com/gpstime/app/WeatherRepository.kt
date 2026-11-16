package com.gpstime.app

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** 和风天气（QWeather）配置：API Host + API Key。 */
data class WeatherConfig(val host: String, val key: String) {

    val isConfigured: Boolean
        get() = host.isNotBlank() && key.isNotBlank()

    val baseUrl: String
        get() = "https://" + host
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')

    companion object {
        private const val PREF_HOST = "qweather_host"
        private const val PREF_KEY = "qweather_key"

        fun load(preferences: SharedPreferences): WeatherConfig = WeatherConfig(
            host = preferences.getString(PREF_HOST, "").orEmpty(),
            key = preferences.getString(PREF_KEY, "").orEmpty()
        )

        fun save(preferences: SharedPreferences, host: String, key: String) {
            preferences.edit()
                .putString(PREF_HOST, host.trim())
                .putString(PREF_KEY, key.trim())
                .apply()
        }
    }
}

data class WeatherNow(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val weatherText: String,
    val icon: Int,
    val windDir: String,
    val windScale: String,
    val isDay: Boolean
)

data class WeatherDay(
    val date: String,
    val weatherText: String,
    val icon: Int,
    val maxTemp: Double,
    val minTemp: Double,
    val precipitationMm: Double?
)

data class WeatherReport(
    val now: WeatherNow,
    val days: List<WeatherDay>
)

data class PlaceResult(
    val name: String,
    val adm1: String?,
    val adm2: String?,
    val id: String,
    val latitude: Double?,
    val longitude: Double?
) {
    /** 例如“河南省 郑州市 金水区”。 */
    val displayName: String
        get() {
            val parts = mutableListOf<String>()
            adm1?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            adm2?.takeIf { it.isNotBlank() && it != adm1 }?.let { parts.add(it) }
            name.takeIf { it.isNotBlank() && it != adm2 }?.let { parts.add(it) }
            return if (parts.isEmpty()) name else parts.joinToString(" ")
        }
}

/** 和风天气返回非 200 时抛出，便于界面上给出“Key 无效/次数用完”等提示。 */
class WeatherApiException(val code: String) : Exception("QWeather code $code")

/**
 * 和风天气（QWeather）接口封装。
 * 注册与控制台：https://console.qweather.com/
 * 使用前需要在控制台创建项目、创建凭据（API Key），并查看账号专属的 API Host。
 */
object WeatherRepository {

    private const val TIMEOUT_MS = 10_000

    suspend fun fetch(query: String, config: WeatherConfig): WeatherReport =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val nowJson = getJson("${config.baseUrl}/v7/weather/now?location=$encoded&key=${config.key}&lang=zh")
            val dailyJson = getJson("${config.baseUrl}/v7/weather/3d?location=$encoded&key=${config.key}&lang=zh")
            WeatherReport(parseNow(nowJson), parseDaily(dailyJson))
        }

    suspend fun lookupCity(name: String, config: WeatherConfig): PlaceResult? =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(name.trim(), "UTF-8")
            val json = getJson("${config.baseUrl}/geo/v2/city/lookup?location=$encoded&key=${config.key}&lang=zh&number=1")
            parsePlace(json)
        }

    suspend fun lookupCoordinates(latitude: Double, longitude: Double, config: WeatherConfig): PlaceResult? =
        withContext(Dispatchers.IO) {
            val location = String.format(Locale.US, "%.4f,%.4f", longitude, latitude)
            val encoded = URLEncoder.encode(location, "UTF-8")
            val json = getJson("${config.baseUrl}/geo/v2/city/lookup?location=$encoded&key=${config.key}&lang=zh&number=1")
            parsePlace(json)
        }

    internal fun parseNow(json: String): WeatherNow {
        val root = JSONObject(json)
        checkCode(root)
        val now = root.getJSONObject("now")
        val icon = now.optString("icon", "").toIntOrNull() ?: 999
        return WeatherNow(
            temperature = now.optString("temp", "0").toDoubleOrNull() ?: 0.0,
            feelsLike = now.optString("feelsLike", "0").toDoubleOrNull() ?: 0.0,
            humidity = now.optString("humidity", "0").toIntOrNull() ?: 0,
            weatherText = now.optString("text", "").ifBlank { WeatherCode.textForIcon(icon) },
            icon = icon,
            windDir = now.optString("windDir", ""),
            windScale = now.optString("windScale", ""),
            isDay = icon !in 150..153 && icon < 900
        )
    }

    internal fun parseDaily(json: String): List<WeatherDay> {
        val root = JSONObject(json)
        checkCode(root)
        val daily: JSONArray = root.optJSONArray("daily") ?: return emptyList()
        val days = mutableListOf<WeatherDay>()
        for (i in 0 until minOf(daily.length(), 3)) {
            val item = daily.getJSONObject(i)
            val dayIcon = item.optString("iconDay", "").toIntOrNull() ?: 999
            days.add(
                WeatherDay(
                    date = item.optString("fxDate", ""),
                    weatherText = item.optString("textDay", "").ifBlank { WeatherCode.textForIcon(dayIcon) },
                    icon = dayIcon,
                    maxTemp = item.optString("tempMax", "0").toDoubleOrNull() ?: 0.0,
                    minTemp = item.optString("tempMin", "0").toDoubleOrNull() ?: 0.0,
                    precipitationMm = item.optString("precip", "").toDoubleOrNull()
                )
            )
        }
        return days
    }

    internal fun parsePlace(json: String): PlaceResult? {
        val root = JSONObject(json)
        checkCode(root)
        val locations: JSONArray = root.optJSONArray("location") ?: return null
        if (locations.length() == 0) return null
        val item = locations.getJSONObject(0)
        val name = item.optString("name", "")
        if (name.isBlank()) return null
        return PlaceResult(
            name = name,
            adm1 = item.optString("adm1", "").ifBlank { null },
            adm2 = item.optString("adm2", "").ifBlank { null },
            id = item.optString("id", ""),
            latitude = item.optString("lat", "").toDoubleOrNull(),
            longitude = item.optString("lon", "").toDoubleOrNull()
        )
    }

    private fun checkCode(root: JSONObject) {
        val code = root.optString("code", "")
        if (code != "200") {
            throw WeatherApiException(code)
        }
    }

    private fun getJson(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "GPSTime/1.2 (Android)")
        }
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use(BufferedReader::readText)
                ?: ""
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }
}

/** 和风天气 icon 代码与文字兜底。 */
object WeatherCode {

    fun textForIcon(icon: Int): String = when (icon) {
        100, 150 -> "晴"
        101, 151 -> "多云"
        102, 152 -> "少云"
        103, 153 -> "晴间多云"
        104, 154 -> "阴"
        in 300..399 -> "雨"
        in 400..499 -> "雪"
        in 500..515 -> "雾霾"
        900 -> "热"
        901 -> "冷"
        else -> "未知天气"
    }

    fun emoji(icon: Int, isDay: Boolean = true): String = when (icon) {
        100 -> if (isDay) "☀️" else "🌙"
        150 -> "🌙"
        101, 151 -> "⛅"
        102, 152 -> "🌤️"
        103, 153 -> "🌤️"
        104, 154 -> "☁️"
        in 300..399 -> "🌧️"
        in 400..499 -> "🌨️"
        in 500..515 -> "🌫️"
        900 -> "🥵"
        901 -> "🥶"
        else -> "🌡️"
    }
}
