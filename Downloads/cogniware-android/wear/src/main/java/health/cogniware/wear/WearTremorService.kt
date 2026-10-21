package health.cogniware.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * WearTremorService
 *
 * Runs on the paired Wear OS watch. Streams accelerometer at 50 Hz,
 * buffers 2-second windows, runs FFT to extract tremor metrics,
 * and sends results to the phone via the Wearable Data Layer.
 *
 * Tremor bands:
 *   Rest tremor (Parkinson's):  4–6 Hz
 *   Action tremor (ET/MS):      6–12 Hz
 *   Voluntary movement:         0.5–3 Hz  (excluded from tremor ratio)
 */
class WearTremorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 2-second window at 50 Hz = 100 samples per axis
    private val WINDOW_SIZE = 100
    private val SAMPLE_RATE_HZ = 50f

    private val bufferX = FloatArray(WINDOW_SIZE)
    private val bufferY = FloatArray(WINDOW_SIZE)
    private val bufferZ = FloatArray(WINDOW_SIZE)
    private var bufferIndex = 0
    private var windowCount = 0

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        accelerometer?.let {
            // SENSOR_DELAY_GAME ≈ 50 Hz — matches our window design
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        bufferX[bufferIndex] = event.values[0]
        bufferY[bufferIndex] = event.values[1]
        bufferZ[bufferIndex] = event.values[2]
        bufferIndex++

        if (bufferIndex >= WINDOW_SIZE) {
            bufferIndex = 0
            windowCount++
            // Process every window (every 2 seconds)
            val xCopy = bufferX.copyOf()
            val yCopy = bufferY.copyOf()
            val zCopy = bufferZ.copyOf()
            scope.launch { processWindow(xCopy, yCopy, zCopy) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private suspend fun processWindow(x: FloatArray, y: FloatArray, z: FloatArray) {
        // Compute vector magnitude to get axis-independent tremor signal
        val magnitude = FloatArray(WINDOW_SIZE) { i ->
            sqrt(x[i] * x[i] + y[i] * y[i] + z[i] * z[i])
        }

        // Remove gravity (mean subtraction)
        val mean = magnitude.average().toFloat()
        val detrended = FloatArray(WINDOW_SIZE) { i -> magnitude[i] - mean }

        // FFT
        val spectrum = fftMagnitude(detrended)
        val freqResolution = SAMPLE_RATE_HZ / WINDOW_SIZE  // 0.5 Hz per bin

        // Integrate power in each band
        var restTremorPower = 0.0    // 4–6 Hz
        var actionTremorPower = 0.0  // 6–12 Hz
        var totalPower = 0.0
        var peakFreq = 0f
        var peakPower = 0.0

        for (i in 1 until spectrum.size) {
            val freq = i * freqResolution
            val power = spectrum[i].toDouble()
            totalPower += power

            if (freq in 4f..6f) restTremorPower += power
            if (freq in 6f..12f) actionTremorPower += power

            if (freq in 4f..12f && power > peakPower) {
                peakPower = power
                peakFreq = freq
            }
        }

        val tremorPower = restTremorPower + actionTremorPower
        val tremorPowerRatio = if (totalPower > 0) tremorPower / totalPower else 0.0
        val dominantFreq = if (peakPower > 0) peakFreq else 0f

        sendToPhone(
            tremorPowerRatio = tremorPowerRatio,
            dominantFreqHz = dominantFreq.toDouble(),
            restTremorRatio = if (totalPower > 0) restTremorPower / totalPower else 0.0,
            actionTremorRatio = if (totalPower > 0) actionTremorPower / totalPower else 0.0
        )
    }

    private fun sendToPhone(
        tremorPowerRatio: Double,
        dominantFreqHz: Double,
        restTremorRatio: Double,
        actionTremorRatio: Double
    ) {
        val request = PutDataMapRequest.create("/tremor").apply {
            dataMap.putDouble("tremorPowerRatio", tremorPowerRatio)
            dataMap.putDouble("dominantFreqHz", dominantFreqHz)
            dataMap.putDouble("restTremorRatio", restTremorRatio)
            dataMap.putDouble("actionTremorRatio", actionTremorRatio)
            dataMap.putLong("timestampMs", System.currentTimeMillis())
        }
        Wearable.getDataClient(this).putDataItem(request.asPutDataRequest().setUrgent())
    }

    /**
     * Real-valued FFT returning magnitude spectrum (first N/2 bins).
     * Uses Cooley-Tukey radix-2 DIT. Pads/truncates input to nearest power of 2.
     */
    private fun fftMagnitude(input: FloatArray): FloatArray {
        val n = nextPowerOf2(input.size)
        val real = DoubleArray(n) { if (it < input.size) input[it].toDouble() else 0.0 }
        val imag = DoubleArray(n)

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { real[i] = real[j].also { real[j] = real[i] }; imag[i] = imag[j].also { imag[j] = imag[i] } }
        }

        // FFT butterfly
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wRe = cos(angle); val wIm = sin(angle)
            var i = 0
            while (i < n) {
                var curRe = 1.0; var curIm = 0.0
                for (k in 0 until len / 2) {
                    val uRe = real[i + k]; val uIm = imag[i + k]
                    val vRe = real[i + k + len/2] * curRe - imag[i + k + len/2] * curIm
                    val vIm = real[i + k + len/2] * curIm + imag[i + k + len/2] * curRe
                    real[i + k] = uRe + vRe; imag[i + k] = uIm + vIm
                    real[i + k + len/2] = uRe - vRe; imag[i + k + len/2] = uIm - vIm
                    val newRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe; curRe = newRe
                }
                i += len
            }
            len = len shl 1
        }

        return FloatArray(n / 2) { i -> sqrt(real[i] * real[i] + imag[i] * imag[i]).toFloat() }
    }

    private fun nextPowerOf2(n: Int): Int {
        var p = 1; while (p < n) p = p shl 1; return p
    }

    private fun buildNotification(): Notification {
        val channelId = "cogniware_tremor"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Tremor Monitor", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("Cogniware")
            .setContentText("Monitoring tremor")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 42
    }
}
