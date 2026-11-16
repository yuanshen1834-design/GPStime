package com.gpstime.app

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var tvGPSDate: TextView
    private lateinit var tvGPSTime: TextView
    private lateinit var tvGPSTimezone: TextView
    private lateinit var tvGPSStatus: TextView
    private lateinit var btnGPSToggle: MaterialButton
    private lateinit var tvNetworkDate: TextView
    private lateinit var tvNetworkTime: TextView
    private lateinit var tvNetworkTimezone: TextView
    private lateinit var tvNetworkStatus: TextView
    private lateinit var btnNetworkTime: MaterialButton
    private lateinit var tvTimeDiff: TextView
    private lateinit var tvLocationInfo: TextView
    private lateinit var tvSatelliteInfo: TextView

    private lateinit var tvWeatherLocation: TextView
    private lateinit var tvWeatherTemp: TextView
    private lateinit var tvWeatherDesc: TextView
    private lateinit var tvWeatherDetail: TextView
    private lateinit var tvWeatherToday: TextView
    private lateinit var tvWeatherDay2: TextView
    private lateinit var tvWeatherDay3: TextView
    private lateinit var tvWeatherUpdated: TextView
    private lateinit var btnWeatherCity: MaterialButton
    private lateinit var btnWeatherSettings: MaterialButton
    private lateinit var btnWeatherRefresh: MaterialButton

    private lateinit var preferences: SharedPreferences
    private lateinit var weatherConfig: WeatherConfig

    private var locationManager: LocationManager? = null
    private var isGPSTracking = false

    private var gpsTime: Long = 0
    private var networkTime: Long = 0

    private lateinit var dateFormat: SimpleDateFormat
    private lateinit var timeFormat: SimpleDateFormat
    private lateinit var fullFormat: SimpleDateFormat

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var lastLocation: Location? = null
    private var bestLocation: Location? = null
    private var firstFixAt = 0L
    private var lastWeatherFetchAt = 0L
    private var weatherFetching = false
    private var lastPlaceName: String? = null
    private var manualPlace: PlaceResult? = null
    private var permissionDialogShown = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location)
        }

        override fun onProviderEnabled(provider: String) {
            tvGPSStatus.text = getString(R.string.gps_available)
            tvGPSStatus.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
        }

        override fun onProviderDisabled(provider: String) {
            tvGPSStatus.text = getString(R.string.waiting_for_gps)
            tvGPSStatus.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark))
        }
    }

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            if (isGPSTracking) {
                updateSatelliteInfo(status)
            }
        }

        override fun onStarted() {
            tvGPSStatus.text = getString(R.string.waiting_for_gps)
        }

        override fun onStopped() {
            tvGPSStatus.text = getString(R.string.gps_unavailable)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = getSharedPreferences("AuroraBrowser", Context.MODE_PRIVATE)
        weatherConfig = WeatherConfig.load(preferences)

        val timeZone = TimeZone.getDefault()
        dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { this.timeZone = timeZone }
        timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply { this.timeZone = timeZone }
        fullFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply { this.timeZone = timeZone }

        initViews()
        setupClickListeners()
        updateTimezoneDisplay()
        updateWeatherConfigUi()

        if (checkLocationPermission()) {
            startGPSTracking()
        } else if (savedInstanceState == null) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!checkLocationPermission()) {
                    showLocationPermissionDialog()
                }
            }, 500)
        }
    }

    private fun initViews() {
        tvGPSDate = findViewById(R.id.tvGPSDate)
        tvGPSTime = findViewById(R.id.tvGPSTime)
        tvGPSTimezone = findViewById(R.id.tvGPSTimezone)
        tvGPSStatus = findViewById(R.id.tvGPSStatus)
        btnGPSToggle = findViewById(R.id.btnGPSToggle)
        tvNetworkDate = findViewById(R.id.tvNetworkDate)
        tvNetworkTime = findViewById(R.id.tvNetworkTime)
        tvNetworkTimezone = findViewById(R.id.tvNetworkTimezone)
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus)
        btnNetworkTime = findViewById(R.id.btnNetworkTime)
        tvTimeDiff = findViewById(R.id.tvTimeDiff)
        tvLocationInfo = findViewById(R.id.tvLocationInfo)
        tvSatelliteInfo = findViewById(R.id.tvSatelliteInfo)

        tvWeatherLocation = findViewById(R.id.tvWeatherLocation)
        tvWeatherTemp = findViewById(R.id.tvWeatherTemp)
        tvWeatherDesc = findViewById(R.id.tvWeatherDesc)
        tvWeatherDetail = findViewById(R.id.tvWeatherDetail)
        tvWeatherToday = findViewById(R.id.tvWeatherToday)
        tvWeatherDay2 = findViewById(R.id.tvWeatherDay2)
        tvWeatherDay3 = findViewById(R.id.tvWeatherDay3)
        tvWeatherUpdated = findViewById(R.id.tvWeatherUpdated)
        btnWeatherCity = findViewById(R.id.btnWeatherCity)
        btnWeatherSettings = findViewById(R.id.btnWeatherSettings)
        btnWeatherRefresh = findViewById(R.id.btnWeatherRefresh)
    }

    private fun setupClickListeners() {
        btnGPSToggle.setOnClickListener {
            if (isGPSTracking) {
                stopGPSTracking()
            } else if (checkLocationPermission()) {
                startGPSTracking()
            } else {
                requestLocationPermission()
            }
        }

        btnNetworkTime.setOnClickListener { fetchNetworkTime() }

        btnWeatherRefresh.setOnClickListener { refreshWeather() }
        btnWeatherCity.setOnClickListener { showCityDialog() }
        btnWeatherSettings.setOnClickListener { showWeatherConfigDialog() }
    }

    private fun updateTimezoneDisplay() {
        val timezone = TimeZone.getDefault()
        val timezoneName = timezone.displayName
        val timezoneOffset = timezone.getOffset(Date().time) / 1000 / 60
        val offsetHours = timezoneOffset / 60
        val offsetMinutes = timezoneOffset % 60
        val offsetSign = if (offsetHours >= 0) "+" else "-"
        val offsetText = "$timezoneName (UTC$offsetSign${Math.abs(offsetHours)}:${String.format(Locale.US, "%02d", Math.abs(offsetMinutes))})"

        tvGPSTimezone.text = offsetText
        tvNetworkTimezone.text = offsetText
    }

    private fun checkLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun showLocationPermissionDialog(force: Boolean = false) {
        if ((permissionDialogShown && !force) || isFinishing) return
        permissionDialogShown = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.weather_permission_title)
            .setMessage(R.string.weather_permission_message)
            .setPositiveButton(R.string.weather_permission_allow) { _, _ ->
                requestLocationPermission()
            }
            .setNegativeButton(R.string.weather_permission_manual) { _, _ ->
                if (!weatherConfig.isConfigured) showWeatherConfigDialog() else showCityDialog()
            }
            .setCancelable(true)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                startGPSTracking()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.location_permission_required),
                    Toast.LENGTH_LONG
                ).show()
                if (tvWeatherTemp.text == "--°") {
                    tvWeatherUpdated.text = getString(R.string.weather_permission_denied)
                }
            }
        }
    }

    private fun startGPSTracking() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val manager = locationManager ?: return

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        if (providers.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_enable_gps), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            providers.forEach { provider ->
                manager.requestLocationUpdates(provider, 1000L, 0f, locationListener)
                runCatching { manager.getLastKnownLocation(provider) }
                    .getOrNull()
                    ?.takeIf { System.currentTimeMillis() - it.time < 10 * 60 * 1000L }
                    ?.let { seedLocation(it) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.registerGnssStatusCallback(gnssStatusCallback, Handler(Looper.getMainLooper()))
            } else {
                @Suppress("DEPRECATION")
                manager.registerGnssStatusCallback(gnssStatusCallback)
            }

            isGPSTracking = true
            btnGPSToggle.text = getString(R.string.stop_gps_time)
            tvGPSStatus.text = getString(R.string.waiting_for_gps)
            tvGPSStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.location_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopGPSTracking() {
        locationManager?.removeUpdates(locationListener)
        runCatching { locationManager?.unregisterGnssStatusCallback(gnssStatusCallback) }
        isGPSTracking = false
        btnGPSToggle.text = getString(R.string.start_gps_time)
        tvGPSStatus.text = getString(R.string.gps_unavailable)
        tvGPSStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        tvGPSDate.text = "--"
        tvGPSTime.text = "--:--:--"
        tvLocationInfo.text = ""
        tvSatelliteInfo.text = "等待获取卫星信息…"
        gpsTime = 0
        tvTimeDiff.text = "--"
    }

    private fun handleNewLocation(location: Location) {
        gpsTime = location.time
        lastLocation = location
        updateGPSTimeDisplay()
        updateLocationInfo(location)
        updateTimeDifference()

        if (!location.hasAccuracy() || bestLocation == null || location.accuracy <= (bestLocation?.accuracy ?: Float.MAX_VALUE)) {
            bestLocation = location
        }
        if (firstFixAt == 0L) firstFixAt = System.currentTimeMillis()

        maybeFetchWeatherAutomatically()
    }

    /** 用缓存的上次位置先占位，不覆盖 GPS 授时时间。 */
    private fun seedLocation(location: Location) {
        lastLocation = location
        if (!location.hasAccuracy() || bestLocation == null || location.accuracy <= (bestLocation?.accuracy ?: Float.MAX_VALUE)) {
            bestLocation = location
        }
        if (firstFixAt == 0L) firstFixAt = System.currentTimeMillis()
    }

    private fun updateGPSTimeDisplay() {
        if (gpsTime > 0) {
            val date = Date(gpsTime)
            tvGPSDate.text = dateFormat.format(date)
            tvGPSTime.text = timeFormat.format(date)
            tvGPSStatus.text = getString(R.string.gps_available)
            tvGPSStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        }
    }

    private fun updateLocationInfo(location: Location) {
        val locationInfo = StringBuilder()
        locationInfo.append("📍 ${getString(R.string.latitude)}: ${String.format(Locale.US, "%.6f", location.latitude)}\n")
        locationInfo.append("📍 ${getString(R.string.longitude)}: ${String.format(Locale.US, "%.6f", location.longitude)}\n")
        if (location.hasAltitude()) {
            locationInfo.append("📍 ${getString(R.string.altitude)}: ${String.format(Locale.US, "%.1f", location.altitude)}米\n")
        }
        if (location.hasAccuracy()) {
            locationInfo.append("📍 ${getString(R.string.accuracy)}: ${String.format(Locale.US, "%.1f", location.accuracy)}米")
        }
        tvLocationInfo.text = locationInfo.toString()
    }

    private fun updateSatelliteInfo(status: GnssStatus?) {
        if (!checkLocationPermission()) return

        try {
            val satelliteInfo = StringBuilder()
            status?.let { gnssStatus ->
                val totalCount = gnssStatus.satelliteCount
                var usedCount = 0
                val gpsSatellites = mutableListOf<Int>()
                val glonassSatellites = mutableListOf<Int>()
                val galileoSatellites = mutableListOf<Int>()
                val beiDouSatellites = mutableListOf<Int>()
                val otherSatellites = mutableListOf<Int>()

                for (i in 0 until totalCount) {
                    val svid = gnssStatus.getSvid(i)
                    if (gnssStatus.usedInFix(i)) usedCount++
                    when (gnssStatus.getConstellationType(i)) {
                        GnssStatus.CONSTELLATION_GPS -> gpsSatellites.add(svid)
                        GnssStatus.CONSTELLATION_GLONASS -> glonassSatellites.add(svid)
                        GnssStatus.CONSTELLATION_GALILEO -> galileoSatellites.add(svid)
                        GnssStatus.CONSTELLATION_BEIDOU -> beiDouSatellites.add(svid)
                        else -> otherSatellites.add(svid)
                    }
                }

                satelliteInfo.append("🛰️ ${getString(R.string.satellites)}: $totalCount\n")
                satelliteInfo.append("🛰️ ${getString(R.string.used_in_fix)}: $usedCount\n\n")
                if (gpsSatellites.isNotEmpty()) satelliteInfo.append("GPS: ${gpsSatellites.size}颗\n")
                if (glonassSatellites.isNotEmpty()) satelliteInfo.append("GLONASS: ${glonassSatellites.size}颗\n")
                if (galileoSatellites.isNotEmpty()) satelliteInfo.append("Galileo: ${galileoSatellites.size}颗\n")
                if (beiDouSatellites.isNotEmpty()) satelliteInfo.append("BeiDou: ${beiDouSatellites.size}颗\n")
                if (otherSatellites.isNotEmpty()) satelliteInfo.append("其他: ${otherSatellites.size}颗\n")
            }
            tvSatelliteInfo.text = satelliteInfo.ifEmpty { "等待获取卫星信息…" }
        } catch (e: Exception) {
            tvSatelliteInfo.text = "无法获取卫星信息"
        }
    }

    // ---------------- 网络时间 ----------------

    private fun fetchNetworkTime() {
        tvNetworkStatus.text = getString(R.string.fetching_network_time)
        btnNetworkTime.isEnabled = false

        scope.launch {
            val time = withContext(Dispatchers.IO) {
                runCatching { getNetworkTimeFromHeaders() }.getOrNull()
            }
            btnNetworkTime.isEnabled = true
            if (time == null) {
                tvNetworkStatus.text = getString(R.string.network_error)
                Toast.makeText(this@MainActivity, getString(R.string.network_error), Toast.LENGTH_SHORT).show()
                return@launch
            }

            networkTime = time
            updateNetworkTimeDisplay()
            updateTimeDifference()
            tvNetworkStatus.text = getString(R.string.network_time_success)
        }
    }

    private fun getNetworkTimeFromHeaders(): Long? {
        val servers = listOf(
            "http://worldtimeapi.org/api/timezone/Asia/Shanghai",
            "http://api.m.taobao.com/rest/api3.do?api=mtop.common.getTimestamp",
            "https://www.baidu.com"
        )

        for (server in servers) {
            try {
                val connection = (URL(server).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                }
                connection.connect()
                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val dateHeader = connection.getHeaderField("Date")
                        if (dateHeader != null) {
                            val headerFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                            return headerFormat.parse(dateHeader)?.time
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                continue
            }
        }

        return System.currentTimeMillis()
    }

    private fun updateNetworkTimeDisplay() {
        if (networkTime > 0) {
            val date = Date(networkTime)
            tvNetworkDate.text = dateFormat.format(date)
            tvNetworkTime.text = timeFormat.format(date)
        }
    }

    private fun updateTimeDifference() {
        if (gpsTime > 0 && networkTime > 0) {
            val diff = Math.abs(gpsTime - networkTime)
            val diffSeconds = diff / 1000
            val diffMillis = diff % 1000

            tvTimeDiff.text = if (diffSeconds > 0) {
                "$diffSeconds.${String.format(Locale.US, "%03d", diffMillis)} 秒"
            } else {
                "$diffMillis 毫秒"
            }

            when {
                diff < 1000 -> tvTimeDiff.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                diff < 5000 -> tvTimeDiff.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                else -> tvTimeDiff.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
        } else if (gpsTime > 0) {
            tvTimeDiff.text = "GPS: ${fullFormat.format(Date(gpsTime))}"
        } else if (networkTime > 0) {
            tvTimeDiff.text = "网络: ${fullFormat.format(Date(networkTime))}"
        } else {
            tvTimeDiff.text = "--"
        }
    }

    // ---------------- 天气 ----------------

    private fun updateWeatherConfigUi() {
        if (!weatherConfig.isConfigured) {
            tvWeatherLocation.text = getString(R.string.weather_not_configured)
            tvWeatherUpdated.text = getString(R.string.weather_config_hint)
            tvWeatherTemp.text = "--°"
            tvWeatherDesc.text = getString(R.string.weather_unknown)
            tvWeatherDetail.text = ""
            tvWeatherToday.visibility = View.GONE
            tvWeatherDay2.visibility = View.GONE
            tvWeatherDay3.visibility = View.GONE
        } else if (lastWeatherFetchAt == 0L && tvWeatherTemp.text == "--°") {
            tvWeatherLocation.text = getString(R.string.weather_locating)
            tvWeatherUpdated.text = getString(R.string.weather_need_location)
        }
    }

    private fun maybeFetchWeatherAutomatically() {
        if (!weatherConfig.isConfigured || manualPlace != null || weatherFetching) return

        val now = System.currentTimeMillis()
        if (lastWeatherFetchAt != 0L && now - lastWeatherFetchAt < WEATHER_REFRESH_INTERVAL_MS) return

        val location = bestLocation ?: return
        val accuracy = if (location.hasAccuracy()) location.accuracy else 9999f
        val waited = firstFixAt > 0 && now - firstFixAt > 10_000L

        if (accuracy > 100f && !(waited && accuracy <= 3000f)) {
            if (tvWeatherTemp.text == "--°") {
                tvWeatherUpdated.text = getString(R.string.weather_bad_location)
            }
            return
        }

        fetchWeatherByCoordinates(location.latitude, location.longitude)
    }

    private fun refreshWeather() {
        if (!weatherConfig.isConfigured) {
            showWeatherConfigDialog()
            return
        }

        manualPlace?.let {
            fetchWeather(it.id.ifBlank { coordinateQuery(it.latitude ?: 0.0, it.longitude ?: 0.0) }, it.latitude, it.longitude, it.displayName)
            return
        }

        val location = bestLocation ?: lastLocation
        if (location != null) {
            fetchWeatherByCoordinates(location.latitude, location.longitude)
        } else if (checkLocationPermission()) {
            tvWeatherUpdated.text = getString(R.string.weather_locating)
            Toast.makeText(this, getString(R.string.weather_need_location), Toast.LENGTH_SHORT).show()
        } else {
            showLocationPermissionDialog(force = true)
        }
    }

    private fun fetchWeatherByCoordinates(latitude: Double, longitude: Double) {
        fetchWeather(coordinateQuery(latitude, longitude), latitude, longitude, null)
    }

    private fun coordinateQuery(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.4f,%.4f", longitude, latitude)

    private fun fetchWeather(query: String, latitude: Double?, longitude: Double?, placeName: String?) {
        if (!weatherConfig.isConfigured) {
            updateWeatherConfigUi()
            return
        }
        if (weatherFetching) return
        weatherFetching = true

        if (placeName != null) {
            tvWeatherLocation.text = placeName
        } else if (lastPlaceName == null) {
            tvWeatherLocation.text = getString(R.string.weather_locating)
        }
        tvWeatherUpdated.text = getString(R.string.weather_loading)

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { WeatherRepository.fetch(query, weatherConfig) }
            }
            weatherFetching = false

            result.onSuccess { report ->
                lastWeatherFetchAt = System.currentTimeMillis()
                renderWeather(report)
                tvWeatherUpdated.text = getString(
                    R.string.weather_updated,
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastWeatherFetchAt))
                )

                when {
                    placeName != null -> {
                        lastPlaceName = placeName
                        tvWeatherLocation.text = placeName
                    }
                    lastPlaceName == null && latitude != null && longitude != null -> {
                        resolvePlaceName(latitude, longitude)
                    }
                }
            }.onFailure { error ->
                tvWeatherUpdated.text = weatherErrorMessage(error)
                if (tvWeatherTemp.text == "--°") {
                    tvWeatherDesc.text = getString(R.string.weather_unknown)
                }
            }
        }
    }

    private fun resolvePlaceName(latitude: Double, longitude: Double) {
        scope.launch {
            val place = withContext(Dispatchers.IO) {
                runCatching { WeatherRepository.lookupCoordinates(latitude, longitude, weatherConfig) }.getOrNull()
            }
            if (place != null) {
                lastPlaceName = place.displayName
                tvWeatherLocation.text = place.displayName
            } else {
                tvWeatherLocation.text = String.format(
                    Locale.US, "北纬 %.2f° · 东经 %.2f°", latitude, longitude
                )
            }
        }
    }

    private fun renderWeather(report: WeatherReport) {
        val now = report.now
        tvWeatherTemp.text = "${formatTemperature(now.temperature)}°"
        tvWeatherDesc.text = "${WeatherCode.emoji(now.icon, now.isDay)} ${now.weatherText}"

        val feels = getString(R.string.weather_feels, formatTemperature(now.feelsLike))
        val humidity = getString(R.string.weather_humidity, now.humidity)
        val wind = getString(R.string.weather_wind, now.windDir, now.windScale)
        tvWeatherDetail.text = "$feels · $humidity · $wind"

        val days = report.days
        tvWeatherToday.setDayLine(getString(R.string.weather_today), days.getOrNull(0))
        tvWeatherDay2.setDayLine(getString(R.string.weather_tomorrow), days.getOrNull(1))
        tvWeatherDay3.setDayLine(getString(R.string.weather_day_after), days.getOrNull(2))
    }

    private fun TextView.setDayLine(label: String, day: WeatherDay?) {
        if (day == null) {
            visibility = View.GONE
            return
        }
        val temps = "${formatTemperature(day.maxTemp)}℃ / ${formatTemperature(day.minTemp)}℃"
        val line = "$label ${day.weatherText}  $temps" + (
            day.precipitationMm?.let { " · ${getString(R.string.weather_precip, formatPrecipitation(it))}" } ?: ""
            )
        this.text = line
        visibility = View.VISIBLE
    }

    private fun formatTemperature(value: Double): String =
        String.format(Locale.US, "%.0f", value)

    private fun formatPrecipitation(value: Double): String =
        String.format(Locale.US, "%.1f", value)

    private fun weatherErrorMessage(error: Throwable): String {
        val code = (error as? WeatherApiException)?.code
        val base = when (code) {
            "401", "403" -> getString(R.string.weather_auth_error)
            "402" -> getString(R.string.weather_quota_error)
            else -> getString(R.string.weather_error)
        }
        return if (code.isNullOrBlank()) base else "$base（$code）"
    }

    private fun showWeatherConfigDialog() {
        val padding = (20 * resources.displayMetrics.density).toInt()

        val hostInput = TextInputEditText(this).apply {
            hint = getString(R.string.weather_host_hint)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(weatherConfig.host)
        }
        val hostLayout = TextInputLayout(this).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(hostInput)
        }

        val keyInput = TextInputEditText(this).apply {
            hint = getString(R.string.weather_key_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(weatherConfig.key)
        }
        val keyLayout = TextInputLayout(this).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(keyInput)
        }

        val help = TextView(this).apply {
            text = getString(R.string.weather_config_help)
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(hostLayout)
            addView(keyLayout, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * resources.displayMetrics.density).toInt() })
            addView(help)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.weather_config_title)
            .setView(container)
            .setPositiveButton(R.string.weather_save, null)
            .setNegativeButton(R.string.weather_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val host = hostInput.text?.toString().orEmpty().trim()
                val key = keyInput.text?.toString().orEmpty().trim()
                if (host.isBlank() || key.isBlank()) {
                    Toast.makeText(this, getString(R.string.weather_key_required), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                WeatherConfig.save(preferences, host, key)
                weatherConfig = WeatherConfig.load(preferences)
                lastWeatherFetchAt = 0L
                lastPlaceName = null
                dialog.dismiss()

                tvWeatherLocation.text = getString(R.string.weather_locating)
                tvWeatherUpdated.text = getString(R.string.weather_loading)
                manualPlace?.let {
                    fetchWeather(it.id.ifBlank { coordinateQuery(it.latitude ?: 0.0, it.longitude ?: 0.0) }, it.latitude, it.longitude, it.displayName)
                } ?: bestLocation?.let {
                    fetchWeatherByCoordinates(it.latitude, it.longitude)
                } ?: run {
                    tvWeatherUpdated.text = getString(R.string.weather_need_location)
                    if (!checkLocationPermission()) showLocationPermissionDialog(force = true)
                }
            }
        }
        dialog.show()
    }

    private fun showCityDialog() {
        if (!weatherConfig.isConfigured) {
            showWeatherConfigDialog()
            return
        }

        val input = TextInputEditText(this).apply {
            hint = getString(R.string.weather_search_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(manualPlace?.name ?: lastPlaceName ?: "")
        }
        val inputLayout = TextInputLayout(this).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(input)
        }
        val container = FrameLayout(this).apply {
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 3, padding, 0)
            addView(inputLayout)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.weather_search_title)
            .setView(container)
            .setPositiveButton(R.string.weather_search) { _, _ ->
                queryCity(input.text?.toString().orEmpty())
            }
            .setNeutralButton(R.string.weather_place_gps) { _, _ ->
                manualPlace = null
                lastPlaceName = null
                val location = bestLocation ?: lastLocation
                if (location != null) {
                    fetchWeatherByCoordinates(location.latitude, location.longitude)
                } else if (checkLocationPermission()) {
                    tvWeatherUpdated.text = getString(R.string.weather_locating)
                } else {
                    showLocationPermissionDialog(force = true)
                }
            }
            .setNegativeButton(R.string.weather_cancel, null)
            .show()
    }

    private fun queryCity(name: String) {
        val query = name.trim()
        if (query.isEmpty()) return
        tvWeatherLocation.text = getString(R.string.weather_searching)

        scope.launch {
            val place = withContext(Dispatchers.IO) {
                runCatching { WeatherRepository.lookupCity(query, weatherConfig) }.getOrNull()
            }
            if (place == null) {
                tvWeatherLocation.text = manualPlace?.displayName ?: getString(R.string.weather_unknown)
                Toast.makeText(this@MainActivity, getString(R.string.weather_not_found), Toast.LENGTH_SHORT).show()
                return@launch
            }
            manualPlace = place
            lastPlaceName = place.displayName
            fetchWeather(
                place.id.ifBlank { coordinateQuery(place.latitude ?: 0.0, place.longitude ?: 0.0) },
                place.latitude,
                place.longitude,
                place.displayName
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        locationManager?.removeUpdates(locationListener)
        runCatching { locationManager?.unregisterGnssStatusCallback(gnssStatusCallback) }
        super.onDestroy()
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val WEATHER_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
    }
}



