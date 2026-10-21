package health.cogniware.sensors

import kotlin.math.sqrt

/**
 * FallDetector
 *
 * Detects falls from raw accelerometer data using the classic two-phase algorithm:
 *
 * Phase 1 — Free-fall window: total acceleration drops below FREE_FALL_THRESHOLD (0.5g)
 *            for at least FREE_FALL_MIN_MS (70ms). This is the body going airborne.
 *
 * Phase 2 — Impact: within IMPACT_WINDOW_MS (1500ms) after free-fall, total acceleration
 *            spikes above IMPACT_THRESHOLD (3.0g). This is the body hitting the ground.
 *
 * Phase 3 — Post-impact stillness: after impact, acceleration stays low (< STILL_THRESHOLD 1.5g)
 *            for STILL_REQUIRED_MS (3000ms). Distinguishes a fall from a jump or dropping the phone.
 *
 * If all three phases complete, onFallDetected() is called with the timestamp.
 *
 * Sensitivity tuning:
 *   - Lower FREE_FALL_THRESHOLD → fewer false positives from slow movements
 *   - Higher IMPACT_THRESHOLD → only detect hard impacts
 *   - Longer STILL_REQUIRED_MS → require longer stillness (reduces phone-drop false positives)
 *
 * Usage: call processSample(ax, ay, az, timestampMs) from PassiveSensingService at each
 * accelerometer reading. Register a FallListener to receive callbacks.
 */
class FallDetector(private val listener: FallListener) {

    interface FallListener {
        fun onFallDetected(timestampMs: Long)
        fun onFallSuspected(phase: FallPhase, timestampMs: Long)  // for logging/debug
    }

    enum class FallPhase { MONITORING, FREE_FALL, IMPACT_EXPECTED, CONFIRMING_STILLNESS }

    companion object {
        private const val FREE_FALL_THRESHOLD = 0.5    // g
        private const val FREE_FALL_MIN_MS = 70L
        private const val IMPACT_THRESHOLD = 3.0        // g
        private const val IMPACT_WINDOW_MS = 1500L
        private const val STILL_THRESHOLD = 1.5         // g
        private const val STILL_REQUIRED_MS = 3000L
        private const val G_MS2 = 9.81                  // m/s² per g
    }

    private var phase = FallPhase.MONITORING

    // Free-fall tracking
    private var freeFallStartMs = 0L

    // Impact tracking
    private var impactWindowStartMs = 0L
    private var impactTimestampMs = 0L

    // Stillness tracking
    private var stillnessStartMs = 0L

    /**
     * Feed each accelerometer sample here. Inputs ax, ay, az are in m/s².
     */
    fun processSample(ax: Double, ay: Double, az: Double, timestampMs: Long) {
        val totalG = sqrt(ax * ax + ay * ay + az * az) / G_MS2

        when (phase) {
            FallPhase.MONITORING -> {
                if (totalG < FREE_FALL_THRESHOLD) {
                    // Possible free-fall start
                    freeFallStartMs = timestampMs
                    phase = FallPhase.FREE_FALL
                    listener.onFallSuspected(FallPhase.FREE_FALL, timestampMs)
                }
            }

            FallPhase.FREE_FALL -> {
                when {
                    totalG >= FREE_FALL_THRESHOLD -> {
                        // Free-fall interrupted before minimum duration — not a real free-fall
                        phase = FallPhase.MONITORING
                    }
                    (timestampMs - freeFallStartMs) >= FREE_FALL_MIN_MS -> {
                        // Sustained free-fall confirmed — now wait for impact
                        impactWindowStartMs = timestampMs
                        phase = FallPhase.IMPACT_EXPECTED
                        listener.onFallSuspected(FallPhase.IMPACT_EXPECTED, timestampMs)
                    }
                    // else: still below threshold but not long enough yet — keep waiting
                }
            }

            FallPhase.IMPACT_EXPECTED -> {
                when {
                    totalG >= IMPACT_THRESHOLD -> {
                        // Impact detected — move to stillness confirmation
                        impactTimestampMs = timestampMs
                        stillnessStartMs = timestampMs
                        phase = FallPhase.CONFIRMING_STILLNESS
                        listener.onFallSuspected(FallPhase.CONFIRMING_STILLNESS, timestampMs)
                    }
                    (timestampMs - impactWindowStartMs) > IMPACT_WINDOW_MS -> {
                        // Impact window expired without a spike — not a fall
                        phase = FallPhase.MONITORING
                    }
                }
            }

            FallPhase.CONFIRMING_STILLNESS -> {
                when {
                    totalG >= STILL_THRESHOLD -> {
                        // Movement broke the stillness requirement — reset
                        // Could be a jump or phone drop rather than a person falling
                        phase = FallPhase.MONITORING
                    }
                    (timestampMs - stillnessStartMs) >= STILL_REQUIRED_MS -> {
                        // All three phases confirmed — this is a fall
                        phase = FallPhase.MONITORING
                        listener.onFallDetected(impactTimestampMs)
                    }
                    // else: still quiet but not long enough yet — keep waiting
                }
            }
        }
    }

    /** Reset state machine to MONITORING (call when service restarts or patient is known safe). */
    fun reset() {
        phase = FallPhase.MONITORING
        freeFallStartMs = 0L
        impactWindowStartMs = 0L
        impactTimestampMs = 0L
        stillnessStartMs = 0L
    }

    fun currentPhase(): FallPhase = phase
}
