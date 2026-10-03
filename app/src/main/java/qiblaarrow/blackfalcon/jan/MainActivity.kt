package qiblaarrow.blackfalcon.jan

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.toRadians

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var compassContainer: RelativeLayout
    private lateinit var degreeTextView: TextView
    private lateinit var distanceTextView: TextView
    private lateinit var dmsCoordinatesTextView: TextView

    private var sensorManager: SensorManager? = null
    private var compassSensor: Sensor? = null
    private var locationManager: LocationManager? = null

    private var currentLocation: Location? = null

    companion object {
        private const val KAABA_LATITUDE = 21.422487
        private const val KAABA_LONGITUDE = 39.826206
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        compassContainer = findViewById(R.id.compassContainer)
        degreeTextView = findViewById(R.id.degreeTextView)
        distanceTextView = findViewById(R.id.distanceTextView)
        dmsCoordinatesTextView = findViewById(R.id.dmsCoordinatesTextView)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager?
        compassSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?

        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locManager = locationManager ?: return
            if (locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, this)
                locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { updateLocationData(it) }
            } else if (locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 2f, this)
                locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { updateLocationData(it) }
            }
        }
    }

    private fun updateLocationData(location: Location) {
        this.currentLocation = location

        val results = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, KAABA_LATITUDE, KAABA_LONGITUDE, results)
        val distanceInKm = results[0] / 1000f
        distanceTextView.text = String.format(Locale.US, "Distance: %,.0f km", distanceInKm)

        val latDMS = decimalToDMS(location.latitude, true)
        val lonDMS = decimalToDMS(location.longitude, false)
        dmsCoordinatesTextView.text = String.format("GPS: %s  %s", latDMS, lonDMS)
    }

    private fun decimalToDMS(value: Double, isLatitude: Boolean): String {
        val direction = if (isLatitude) {
            if (value >= 0) "N" else "S"
        } else {
            if (value >= 0) "E" else "W"
        }

        var absValue = abs(value)
        val degrees = absValue.toInt()
        absValue = (absValue - degrees) * 60
        val minutes = absValue.toInt()
        val seconds = (absValue - minutes) * 60

        return String.format(Locale.US, "%d°%d'%.1f"%s", degrees, minutes, seconds, direction)
    }

    override fun onResume() {
        super.onResume()
        compassSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        locationManager?.removeUpdates(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val azimuth = event.values[0].roundToInt()

        val loc = currentLocation
        if (loc != null) {
            val qiblaBearing = calculateQiblaBearing(loc.latitude, loc.longitude)
            val direction = azimuth - qiblaBearing

            compassContainer.rotation = -direction
            degreeTextView.text = "${qiblaBearing.roundToInt()}° Qibla"
        } else {
            compassContainer.rotation = -azimuth.toFloat()
            degreeTextView.text = "$azimuth°"
        }
    }

    private fun calculateQiblaBearing(lat: Double, lon: Double): Float {
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val lat2 = Math.toRadians(KAABA_LATITUDE)
        val lon2 = Math.toRadians(KAABA_LONGITUDE)

        val deltaLon = lon2 - lon1

        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)

        var bearing = Math.toDegrees(atan2(y, x))
        bearing = (bearing + 360) % 360
        return bearing.toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onLocationChanged(location: Location) {
        updateLocationData(location)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Location permission required for Qibla direction", Toast.LENGTH_SHORT).show()
        }
    }
}
