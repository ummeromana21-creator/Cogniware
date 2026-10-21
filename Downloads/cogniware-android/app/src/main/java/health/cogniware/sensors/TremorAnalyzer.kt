package health.cogniware.sensors

import health.cogniware.data.models.*
import java.util.UUID
import kotlin.math.*

/**
 * TremorAnalyzer
 *
 * Analyses accelerometer burst data to extract tremor features.
 * Uses FFT to decompose the signal into frequency bands associated
 * with specific neurological conditions.
 *
 * Frequency band mapping (clinical literature):
 *   2–4 Hz  → Cerebellar / MS intention tremor
 *   4–7 Hz  → Parkinsonian resting tremor (dopaminergic pathway)
 *   4–12 Hz → Essential tremor (action-dependent)
 *   8–12 Hz → Physiological tremor (exaggerated by anxiety, caffeine, lithium)
 *   < 0.5g RMS → Sub-clinical, no clinical significance
 *
 * Context-aware interpretation:
 * We do NOT classify tremor type here. We extract frequency features
 * and tag them with the context vector. The clinician and the
 * longitudinal model make the interpretation.
 *
 * A 5 Hz tremor while WALKING is different from a 5 Hz tremor while STILL.
 * The context vector carries that distinction.
 */
class TremorAnalyzer(private val patientId: String) {

    companion object {
        private const val SAMPLE_RATE_HZ = 50          // accelerometer at 50 Hz
        private const val BURST_DURATION_SEC = 8        // 8-second analysis window
        private const val MIN_SAMPLES = SAMPLE_RATE_HZ * BURST_DURATION_SEC
        private const val CLINICAL_THRESHOLD_G = 0.05  // below this = physiological noise

        // Frequency band boundaries (Hz)
        private const val CEREBELLAR_LOW = 2.0
        private const val CEREBELLAR_HIGH = 4.0
        private const val PARKINSONIAN_LOW = 4.0
        private const val PARKINSONIAN_HIGH = 7.0
        private const val ESSENTIAL_HIGH = 12.0
    }

    /**
     * Analyse a burst of accelerometer samples.
     *
     * @param samplesX  X-axis acceleration values (g)
     * @param samplesY  Y-axis acceleration values (g)
     * @param samplesZ  Z-axis acceleration values (g)
     * @param context   Context vector at time of measurement
     * @return TremorReading or null if insufficient data / below threshold
     */
    fun analyse(
        samplesX: DoubleArray,
        samplesY: DoubleArray,
        samplesZ: DoubleArray,
        context: ContextVector
    ): TremorReading? {
        if (samplesX.size < MIN_SAMPLES) return null

        // ── 1. Compute resultant magnitude ──
        // Combine 3 axes into single magnitude signal.
        // This makes analysis rotation-invariant — we don't assume
        // device orientation relative to limb.
        val magnitude = DoubleArray(samplesX.size) { i ->
            sqrt(samplesX[i].pow(2) + samplesY[i].pow(2) + samplesZ[i].pow(2))
        }

        // ── 2. Remove gravity (high-pass filter) ──
        val detrended = removeGravityAndDC(magnitude)

        // ── 3. RMS amplitude ──
        val rms = sqrt(detrended.map { it.pow(2) }.average())

        // Below clinical threshold — not clinically significant
        if (rms < CLINICAL_THRESHOLD_G) {
            return TremorReading(
                readingId = UUID.randomUUID().toString(),
                patientId = patientId,
                timestampMs = System.currentTimeMillis(),
                durationMs = (MIN_SAMPLES * 1000L / SAMPLE_RATE_HZ),
                peakFrequencyHz = 0.0,
                powerAtPeakDb = 0.0,
                power4to7Hz = 0.0,
                power4to12Hz = 0.0,
                power2to4Hz = 0.0,
                totalPowerDb = toDb(rms),
                rmsAmplitude = rms,
                laterality = Laterality.UNKNOWN,
                tremorBandHint = TremorBand.PHYSIOLOGICAL,
                context = context,
                syncedToCloud = false
            )
        }

        // ── 4. FFT ──
        val n = nextPowerOf2(detrended.size)
        val padded = detrended.copyOf(n)
        val (real, imag) = fft(padded)
        val powerSpectrum = computePowerSpectrum(real, imag, n)

        // ── 5. Frequency resolution ──
        val freqResolution = SAMPLE_RATE_HZ.toDouble() / n

        // ── 6. Band power extraction ──
        val power2to4 = bandPower(powerSpectrum, CEREBELLAR_LOW, CEREBELLAR_HIGH, freqResolution)
        val power4to7 = bandPower(powerSpectrum, PARKINSONIAN_LOW, PARKINSONIAN_HIGH, freqResolution)
        val power4to12 = bandPower(powerSpectrum, PARKINSONIAN_LOW, ESSENTIAL_HIGH, freqResolution)

        // ── 7. Peak frequency ──
        val (peakFreq, peakPower) = findPeak(powerSpectrum, freqResolution, 1.0, 15.0)

        // ── 8. Total power ──
        val totalPower = powerSpectrum.sum()

        // ── 9. Laterality ──
        // Compare left vs right axis dominance using X-axis asymmetry
        val laterality = estimateLaterality(samplesX, samplesZ, context)

        // ── 10. Band hint ──
        val bandHint = classifyBand(peakFreq, rms, context)

        return TremorReading(
            readingId = UUID.randomUUID().toString(),
            patientId = patientId,
            timestampMs = System.currentTimeMillis(),
            durationMs = (samplesX.size * 1000L / SAMPLE_RATE_HZ),
            peakFrequencyHz = peakFreq,
            powerAtPeakDb = toDb(peakPower),
            power4to7Hz = power4to7,
            power4to12Hz = power4to12,
            power2to4Hz = power2to4,
            totalPowerDb = toDb(totalPower),
            rmsAmplitude = rms,
            laterality = laterality,
            tremorBandHint = bandHint,
            context = context,
            syncedToCloud = false
        )
    }

    // ── Signal processing helpers ──

    /**
     * Remove gravity (DC component) using a simple running mean subtraction.
     * Equivalent to a high-pass filter above 0.5 Hz.
     */
    private fun removeGravityAndDC(signal: DoubleArray): DoubleArray {
        val windowSize = SAMPLE_RATE_HZ * 2  // 2-second window for gravity estimate
        val result = DoubleArray(signal.size)
        for (i in signal.indices) {
            val start = maxOf(0, i - windowSize / 2)
            val end = minOf(signal.size - 1, i + windowSize / 2)
            val localMean = signal.slice(start..end).average()
            result[i] = signal[i] - localMean
        }
        return result
    }

    /** Iterative Cooley-Tukey FFT (radix-2 DIT) */
    private fun fft(x: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val n = x.size
        val real = x.copyOf()
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
            val ang = -2.0 * PI / len
            val wReal = cos(ang); val wImag = sin(ang)
            var i = 0
            while (i < n) {
                var curReal = 1.0; var curImag = 0.0
                for (k in 0 until len / 2) {
                    val uR = real[i + k]; val uI = imag[i + k]
                    val vR = real[i + k + len / 2] * curReal - imag[i + k + len / 2] * curImag
                    val vI = real[i + k + len / 2] * curImag + imag[i + k + len / 2] * curReal
                    real[i + k] = uR + vR; imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR; imag[i + k + len / 2] = uI - vI
                    val newCurReal = curReal * wReal - curImag * wImag
                    curImag = curReal * wImag + curImag * wReal; curReal = newCurReal
                }
                i += len
            }
            len = len shl 1
        }
        return Pair(real, imag)
    }

    private fun computePowerSpectrum(real: DoubleArray, imag: DoubleArray, n: Int): DoubleArray {
        val half = n / 2
        return DoubleArray(half) { i -> (real[i].pow(2) + imag[i].pow(2)) / n }
    }

    private fun bandPower(
        spectrum: DoubleArray,
        lowHz: Double,
        highHz: Double,
        freqRes: Double
    ): Double {
        val lowBin = (lowHz / freqRes).toInt().coerceAtLeast(0)
        val highBin = (highHz / freqRes).toInt().coerceAtMost(spectrum.size - 1)
        return if (lowBin >= highBin) 0.0 else spectrum.slice(lowBin..highBin).sum()
    }

    private fun findPeak(
        spectrum: DoubleArray,
        freqRes: Double,
        minHz: Double,
        maxHz: Double
    ): Pair<Double, Double> {
        val lowBin = (minHz / freqRes).toInt().coerceAtLeast(0)
        val highBin = (maxHz / freqRes).toInt().coerceAtMost(spectrum.size - 1)
        var peakBin = lowBin
        var peakPower = 0.0
        for (i in lowBin..highBin) {
            if (spectrum[i] > peakPower) { peakPower = spectrum[i]; peakBin = i }
        }
        return Pair(peakBin * freqRes, peakPower)
    }

    private fun estimateLaterality(
        samplesX: DoubleArray,
        samplesZ: DoubleArray,
        context: ContextVector
    ): Laterality {
        // In UPRIGHT device orientation, X-axis captures left-right sway
        // This is a proxy — proper laterality needs two devices or specific protocols
        if (context.deviceOrientation != DeviceOrientation.UPRIGHT) return Laterality.UNKNOWN
        val xRms = sqrt(samplesX.map { it.pow(2) }.average())
        val zRms = sqrt(samplesZ.map { it.pow(2) }.average())
        val ratio = xRms / (zRms + 1e-9)
        return when {
            ratio > 1.5 -> Laterality.LEFT_DOMINANT
            ratio < 0.67 -> Laterality.RIGHT_DOMINANT
            else -> Laterality.BILATERAL
        }
    }

    /**
     * Context-aware band classification hint.
     *
     * This is NOT a diagnosis. It is a routing hint for the clinician.
     * The critical insight: the same frequency can mean different things
     * depending on whether it occurs at REST or during MOVEMENT.
     *
     * Parkinsonian tremor: present at rest, SUPPRESSED during movement
     * Essential tremor: ABSENT at rest, appears during movement
     *
     * Activity state from context vector is the key discriminator.
     */
    private fun classifyBand(
        peakHz: Double,
        rmsG: Double,
        context: ContextVector
    ): TremorBand {
        if (rmsG < CLINICAL_THRESHOLD_G) return TremorBand.PHYSIOLOGICAL
        val isResting = context.activityState == ActivityState.STILL
        return when {
            peakHz in 2.0..4.0 -> TremorBand.CEREBELLAR
            peakHz in 4.0..7.0 && isResting -> TremorBand.PARKINSONIAN
            peakHz in 4.0..12.0 && !isResting -> TremorBand.ESSENTIAL
            peakHz in 4.0..12.0 -> TremorBand.UNKNOWN  // need more context
            else -> TremorBand.PHYSIOLOGICAL
        }
    }

    private fun nextPowerOf2(n: Int): Int {
        var p = 1; while (p < n) p = p shl 1; return p
    }

    private fun toDb(linear: Double): Double {
        return if (linear > 0) 20.0 * log10(linear) else -120.0
    }
}
