package health.cogniware.analysis

import kotlin.math.*

/**
 * SubThresholdFFTMonitor
 *
 * Rolling circular buffer of 1000 accelerometer magnitude samples (10 seconds at 100 Hz).
 * Every time the buffer is full, runs a DFT over bins covering 0.5-8 Hz and returns
 * a SubThresholdResult with tremor-band and rest-band power estimates.
 *
 * No external FFT library: uses a simple O(N^2) DFT restricted to the frequency bins of
 * interest (bins 5-80, i.e. 0.5-8 Hz at 0.1 Hz resolution) for battery efficiency.
 *
 * Call processSample() from PassiveSensingService at each accelerometer reading.
 * Returns non-null only when the buffer is full and a new result is ready (every ~10 s).
 */
class SubThresholdFFTMonitor {

    companion object {
        private const val BUFFER_SIZE = 1000           // 10 s x 100 Hz
        private const val SAMPLE_RATE_HZ = 100.0
        // Frequency bin resolution = SAMPLE_RATE / BUFFER_SIZE = 0.1 Hz
        private const val BIN_RESOLUTION_HZ = SAMPLE_RATE_HZ / BUFFER_SIZE

        // Indices into the DFT output (0-indexed; bin k -> frequency k * BIN_RESOLUTION_HZ)
        private const val REST_LOW_BIN = 5             // 0.5 Hz
        private const val REST_HIGH_BIN = 20           // 2.0 Hz
        private const val TREMOR_LOW_BIN = 30          // 3.0 Hz
        private const val TREMOR_HIGH_BIN = 70         // 7.0 Hz
        private const val COMPUTE_UP_TO_BIN = 80       // 8.0 Hz -- upper limit for DFT

        /**
         * Sub-threshold power threshold in g^2/Hz.
         * Values above this flag a sub-threshold tremor event.
         * Tunable -- start conservative, refine from patient data.
         */
        const val SUB_THRESHOLD_POWER_G2_HZ = 0.002
    }

    private val buffer = FloatArray(BUFFER_SIZE)
    private var writeIndex = 0
    private var sampleCount = 0L   // total samples ever fed in

    /**
     * Feed one accelerometer sample.
     * @return a [SubThresholdResult] every time the buffer completes a full window (~10 s),
     *         null otherwise.
     */
    fun processSample(x: Float, y: Float, z: Float): SubThresholdResult? {
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        buffer[writeIndex] = magnitude
        writeIndex = (writeIndex + 1) % BUFFER_SIZE
        sampleCount++

        // Emit a result only on each complete buffer fill (every 1000 samples)
        if (sampleCount % BUFFER_SIZE != 0L) return null

        return computeResult()
    }

    private fun computeResult(): SubThresholdResult {
        // Read buffer in chronological order into a flat array
        val ordered = FloatArray(BUFFER_SIZE)
        for (i in 0 until BUFFER_SIZE) {
            ordered[i] = buffer[(writeIndex + i) % BUFFER_SIZE]
        }

        // Remove DC offset (subtract mean)
        val mean = ordered.average().toFloat()
        val demeaned = FloatArray(BUFFER_SIZE) { ordered[it] - mean }

        // Apply Hann window to reduce spectral leakage
        val windowed = DoubleArray(BUFFER_SIZE) { i ->
            val w = 0.5 * (1.0 - cos(2.0 * PI * i / (BUFFER_SIZE - 1)))
            demeaned[i] * w
        }

        // Compute DFT power spectrum only for bins REST_LOW_BIN..COMPUTE_UP_TO_BIN
        // Power[k] = (Re^2+Im^2) / N^2 -- normalised so units are (g^2) per bin
        // Density = Power / BIN_RESOLUTION_HZ -> g^2/Hz
        val powerSpectrum = DoubleArray(COMPUTE_UP_TO_BIN + 1)
        val n = BUFFER_SIZE.toDouble()

        for (k in REST_LOW_BIN..COMPUTE_UP_TO_BIN) {
            var re = 0.0
            var im = 0.0
            val twoPiKoverN = 2.0 * PI * k / n
            for (t in 0 until BUFFER_SIZE) {
                re += windowed[t] * cos(twoPiKoverN * t)
                im -= windowed[t] * sin(twoPiKoverN * t)
            }
            // One-sided spectrum: multiply by 2 (except DC)
            powerSpectrum[k] = 2.0 * (re * re + im * im) / (n * n)
        }

        // Sum power in each band (in g^2/Hz by dividing by resolution)
        var tremorPower = 0.0
        for (k in TREMOR_LOW_BIN..TREMOR_HIGH_BIN) {
            tremorPower += powerSpectrum[k] / BIN_RESOLUTION_HZ
        }

        var restPower = 0.0
        for (k in REST_LOW_BIN..REST_HIGH_BIN) {
            restPower += powerSpectrum[k] / BIN_RESOLUTION_HZ
        }

        // Find dominant frequency in the full 0.5-8 Hz range
        var maxPower = 0.0
        var dominantBin = REST_LOW_BIN
        for (k in REST_LOW_BIN..COMPUTE_UP_TO_BIN) {
            if (powerSpectrum[k] > maxPower) {
                maxPower = powerSpectrum[k]
                dominantBin = k
            }
        }
        val dominantFreqHz = dominantBin * BIN_RESOLUTION_HZ

        return SubThresholdResult(
            tremorPowerG2 = tremorPower,
            restPowerG2 = restPower,
            dominantFreqHz = dominantFreqHz,
            isSubThreshold = tremorPower > SUB_THRESHOLD_POWER_G2_HZ,
            timestampMs = System.currentTimeMillis()
        )
    }
}

data class SubThresholdResult(
    val tremorPowerG2: Double,
    val restPowerG2: Double,
    val dominantFreqHz: Double,
    val isSubThreshold: Boolean,
    val timestampMs: Long
)
