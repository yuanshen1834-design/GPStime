package com.gpstime.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherParseTest {

    private val nowJson = """
        {
          "code": "200",
          "now": {
            "obsTime": "2026-11-16T23:00+08:00",
            "temp": "12",
            "feelsLike": "10",
            "icon": "150",
            "text": "晴",
            "wind360": "45",
            "windDir": "东北风",
            "windScale": "2",
            "windSpeed": "8",
            "humidity": "56",
            "precip": "0.0",
            "pressure": "1012",
            "vis": "25",
            "cloud": "0",
            "dew": "4"
          }
        }
    """.trimIndent()

    private val dailyJson = """
        {
          "code": "200",
          "daily": [
            {
              "fxDate": "2026-11-16",
              "tempMax": "15",
              "tempMin": "5",
              "iconDay": "100",
              "textDay": "晴",
              "iconNight": "150",
              "textNight": "晴",
              "windDirDay": "北风",
              "windScaleDay": "2",
              "humidity": "50",
              "precip": "0.0"
            },
            {
              "fxDate": "2026-11-17",
              "tempMax": "14",
              "tempMin": "6",
              "iconDay": "101",
              "textDay": "多云",
              "iconNight": "151",
              "textNight": "多云",
              "windDirDay": "东风",
              "windScaleDay": "1",
              "humidity": "60",
              "precip": "1.2"
            },
            {
              "fxDate": "2026-11-18",
              "tempMax": "11",
              "tempMin": "4",
              "iconDay": "305",
              "textDay": "小雨",
              "iconNight": "305",
              "textNight": "小雨",
              "windDirDay": "东北风",
              "windScaleDay": "3",
              "humidity": "80",
              "precip": "6.4"
            }
          ]
        }
    """.trimIndent()

    private val geoJson = """
        {
          "code": "200",
          "location": [
            {
              "name": "金水区",
              "id": "101180101",
              "lat": "34.80",
              "lon": "113.66",
              "adm2": "郑州市",
              "adm1": "河南省",
              "country": "中国"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesCurrentConditions() {
        val now = WeatherRepository.parseNow(nowJson)
        assertEquals(12.0, now.temperature, 0.001)
        assertEquals(10.0, now.feelsLike, 0.001)
        assertEquals(56, now.humidity)
        assertEquals("晴", now.weatherText)
        assertEquals(150, now.icon)
        assertEquals("东北风", now.windDir)
        assertEquals("2", now.windScale)
        assertFalse(now.isDay)
    }

    @Test
    fun parsesThreeDayForecast() {
        val days = WeatherRepository.parseDaily(dailyJson)
        assertEquals(3, days.size)
        assertEquals("2026-11-16", days[0].date)
        assertEquals(15.0, days[0].maxTemp, 0.001)
        assertEquals(5.0, days[0].minTemp, 0.001)
        assertEquals("小雨", days[2].weatherText)
        assertEquals(6.4, days[2].precipitationMm!!, 0.001)
    }

    @Test
    fun parsesCoordinateLookup() {
        val place = WeatherRepository.parsePlace(geoJson)
        assertNotNull(place)
        assertEquals("金水区", place!!.name)
        assertEquals("101180101", place.id)
        assertEquals("河南省 郑州市 金水区", place.displayName)
    }

    @Test
    fun handlesEmptyGeoResult() {
        assertNull(WeatherRepository.parsePlace("""{"code":"200","location":[]}"""))
    }

    @Test
    fun throwsOnInvalidKey() {
        val failed = runCatching { WeatherRepository.parseNow("""{"code":"401"}""") }
        assertTrue(failed.isFailure)
        assertEquals("401", (failed.exceptionOrNull() as WeatherApiException).code)
    }

    @Test
    fun buildsBaseUrlFromHostInput() {
        assertEquals("https://devapi.qweather.com", WeatherConfig("https://devapi.qweather.com/", "k").baseUrl)
        assertEquals("https://abc.re.qweatherapi.com", WeatherConfig("abc.re.qweatherapi.com", "k").baseUrl)
        assertFalse(WeatherConfig("", "k").isConfigured)
        assertTrue(WeatherConfig("h", "k").isConfigured)
    }

    @Test
    fun mapsIconsToEmoji() {
        assertEquals("☀️", WeatherCode.emoji(100))
        assertEquals("🌧️", WeatherCode.emoji(305))
        assertEquals("🌨️", WeatherCode.emoji(400))
        assertEquals("晴", WeatherCode.textForIcon(100))
    }
}
