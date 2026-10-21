package health.cogniware.analysis

import kotlin.math.*

/**
 * FreezeOfGaitDetector
 *
 * Implements the clinically-validated Freeze Index (FI) algorithm for detecting
 * Freeze of Gait (FOG) episodes in Parkinson's Disease patients.
 *
 * Algorithm:
 *   - Operates on the vertical (z) accelerometer axis after gravity removal.
 *   - Maintains a 256-sample rolling window at 50 Hz (≈5.12 s), with 50% overlap
 *     (new result every 128 samples, i.e. every ~2.56 s).
 *   - Gravity removal: running mean subtraction over a 100-sample (2 s) window.
 *   - Power is computed via DFT (no external libs) over two bands:
 *       Locomotion band : 0.5–3 Hz  (normal walking cadence)
 *       Freeze band     : 3–8 Hz    (trembling-in-place during FOG)
 *   - Freeze Index (FI) = freeze_band_power / locomotion_band_power
 *   - FOG is flagged when FI > FREEZE_THRESHOLD AND locomotion_band_power < LOW_LOCOMOTION_THRESHOLD,
 *     ensuring the freeze band dominates during low overall movement (not during fast walking).
 *   - A freeze episode is confirmed on the first IMMINENT window and resolved after
 *     two consecutive non-freeze windows.
 *
 * Reference: Moore et al. (2008) "Ambulatory monitoring of freezing of gait in Parkinson's disease."
 */
class FreezeOfGaitDetector {

    companion object {
        // Window and overlap sizes
        private const val WINDOW_SIZE = 256          // samples (5.12 s at 50 Hz)
        private const val HOP_SIZE = 128             // 50% overlap → result every 128 samples
        private const val SAMPLE_RATE_HZ = 50.0

        // Gravity removal: running mean over 2 s
        private const val GRAVITY_WINDOW = 100       // samples (2 s at 50 Hz)

        // Bin resolution: SAMPLE_RATE / WINDOW_SIZE = 50/256 ≈ 0.195 Hz
        // Bins are 0-indexed: bin k → frequency k * (50/256) Hz
        // Locomotion band  0.5–3 Hz → bins  3..15  (0.585–2.93 Hz, closest)
        // Freeze band      3–8 Hz   → bins 16..41  (3.125–8.008 Hz, closest)
        private const val LOCO_LOW_BIN  = 3
        private const val LOCO_HIGH_BIN = 15
        private const val FREEZE_LOW_BIN  = 16
        private const val FREEZE_HIGH_BIN = 41

        const val FREEZE_THRESHOLD = 2.5            // FI above this → FOG candidate
        const val LOW_LOCOMOTION_THRESHOLD = 0.5    // g²/Hz — guard against fast-walk false positives

        // Two consecutive non-freeze windows needed to resolve an episode
        private const val RESOLVE_CONSECUTIVE = 2
    }

    // --- Rolling input buffer (raw z, pre-gravity-removal) ---
    private val rawBuffer = FloatArray(WINDOW_SIZE)
    private var rawWriteIdx = 0
    private var samplesIn = 0L   // total samples received

    // --- Gravity removal: circular mean buffer (2 s) ---
    private val gravityBuf = FloatArray(GRAVITY_WINDOW)
    private var gravWriteIdx = 0
    private var gravSum = 0.0
    private var gravCount = 0

    // --- FOG state tracking ---
    private var isFreezing = false
    private var freezeStartMs = 0L
    private var nonFreezeWindowCount = 0

    private var listener: FogListener? = null

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    interface FogListener {
        fun onFreezeDetected(timestampMs: Long)
        fun onFreezeResolved(durationMs: Long)
    }

    data class FogResult(
        val freezeIndex: Double,
        val isFreezing: Boolean,
        val timestampMs: Long
    )

    fun setListener(listener: FogListener?) {
        this.listener = listener
    }

    /**
     * Process one vertical-axis accelerometer sample (raw, including gravity).
     *
     * @param z           Raw z-axis reading in g (including gravity).
     * @param timestampMs Sensor timestamp in milliseconds.
     * @return            [FogResult] every [HOP_SIZE] samples; null between hops.
     */
    fun processSample(z: Float, timestampMs: Long): FogResult? {
        // --- Gravity removal via running mean ---
        val oldGrav = gravityBuf[gravWriteIdx]
        gravityBuf[gravWriteIdx] = z
        gravWriteIdx = (gravWriteIdx + 1) % GRAVITY_WINDOW
        gravSum += z - oldGrav
        if (gravCount < GRAVITY_WINDOW) gravCount++
        val gravityEstimate = (gravSum / gravCount).toFloat()
        val zCorrected = z - gravityEstimate

        // --- Fill window buffer ---
        rawBuffer[rawWriteIdx] = zCorrected
        rawWriteIdx = (rawWriteIdx + 1) % WINDOW_SIZE
        samplesIn++

        // Emit only after the initial window is full, then every HOP_SIZE
        if (samplesIn < WINDOW_SIZE) return null
        if ((samplesIn - WINDOW_SIZE) % HOP_SIZE != 0L) return null

        return computeResult(timestampMs)
    }

    // -------------------------------------------------------------------------
    // Internal computation
    // -------------------------------------------------------------------------

    private fun computeResult(timestampMs: Long): FogResult {
        // Read window in chronological order
        val windowed = DoubleArray(WINDOW_SIZE)
        for (i in 0 until WINDOW_SIZE) {
            val sample = rawBuffer[(rawWriteIdx + i) % WINDOW_SIZE].toDouble()
            // Hann window to reduce spectral leakage
            val w = 0.5 * (1.0 - cos(2.0 * PI * i / (WINDOW_SIZE - 1)))
            windowed[i] = sample * w
        }

        val n = WINDOW_SIZE.toDouble()
        val binResolution = SAMPLE_RATE_HZ / WINDOW_SIZE  // ≈0.195 Hz

        // DFT restricted to bins we care about (FREEZE_HIGH_BIN is the max we need)
        val powerDensity = DoubleArray(FREEZE_HIGH_BIN + 1)
        for (k in LOCO_LOW_BIN..FREEZE_HIGH_BIN) {
            var re = 0.0
            var im = 0.0
            val twoPiKoverN = 2.0 * PI * k / n
            for (t in 0 until WINDOW_SIZE) {
                re += windowed[t] * cos(twoPiKoverN * t)
                im -= windowed[t] * sin(twoPiKoverN * t)
            }
            // One-sided power spectrum normalised; convert to power spectral density (g²/Hz)
            val power = 2.0 * (re * re + im * im) / (n * n)
            powerDensity[k] = power / binResolution
        }

        // Sum band powers (g²/Hz integrated over bins → effectively mean density)
        var locoPower = 0.0
        for (k in LOCO_LOW_BIN..LOCO_HIGH_BIN) locoPower += powerDensity[k]
        locoPower /= (LOCO_HIGH_BIN - LOCO_LOW_BIN + 1)

        var freezePower = 0.0
        for (k in FREEZE_LOW_BIN..FREEZE_HIGH_BIN) freezePower += powerDensity[k]
        freezePower /= (FREEZE_HIGH_BIN - FREEZE_LOW_BIN + 1)

        val freezeIndex = if (locoPower > 0.0) freezePower / locoPower else 0.0

        val isFogWindow = freezeIndex > FREEZE_THRESHOLD && locoPower < LOW_LOCOMOTION_THRESHOLD

        // --- State machine ---
        if (isFogWindow) {
            nonFreezeWindowCount = 0
            if (!isFreezing) {
                isFreezing = true
                freezeStartMs = timestampMs
                listener?.onFreezeDetected(timestampMs)
            }
        } else {
            if (isFreezing) {
                nonFreezeWindowCount++
                if (nonFreezeWindowCount >= RESOLVE_CONSECUTIVE) {
                    isFreezing = false
                    val durationMs = timestampMs - freezeStartMs
                    nonFreezeWindowCount = 0
                    listener?.onFreezeResolved(durationMs)
                }
            }
        }

        return FogResult(
            freezeIndex = freezeIndex,
            isFreezing = isFreezing,
            timestampMs = timestampMs
        )
    }
}
