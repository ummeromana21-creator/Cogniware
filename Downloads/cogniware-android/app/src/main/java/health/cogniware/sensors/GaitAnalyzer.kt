package health.cogniware.sensors

import health.cogniware.data.models.*
import java.util.UUID
import kotlin.math.*

/**
 * GaitAnalyzer
 *
 * Derives gait biomarkers from accelerometer data during WALKING activity state.
 *
 * Clinical significance:
 *   Cadence reduction    → Bradykinesia (Parkinson's), fatigue, medication effect
 *   Step asymmetry       → Unilateral motor impairment, early PD, stroke sequelae
 *   Stride irregularity  → Cerebellar ataxia, MS, dementia-related gait
 *   Vertical impact (G)  → Foot clearance, shuffle gait (PD), fall risk
 *
 * Method:
 *   Peak detection on vertical acceleration (Z-axis during upright walking).
 *   Each heel-strike produces a characteristic impact peak in vertical acceleration.
 *   Step timing statistics → cadence, regularity, asymmetry.
 *
 * Context constraint:
 *   ONLY called when context.activityState == WALKING.
 *   The context check is enforced by PassiveSensingService before calling here.
 *   A gait metric recorded during IN_VEHICLE would be meaningless vibration.
 */
class GaitAnalyzer(private val patientId: String) {

    companion object {
        private const val SAMPLE_RATE_HZ = 50
        private const val MIN_WALKING_SECONDS = 10
        private const val MIN_SAMPLES = SAMPLE_RATE_HZ * MIN_WALKING_SECONDS

        // Step detection
        private const val MIN_STEP_INTERVAL_MS = 300.0   // max ~200 steps/min
        private const val MAX_STEP_INTERVAL_MS = 2000.0  // min ~30 steps/min
        private const val IMPACT_THRESHOLD_G = 1.2       // impact peak threshold above 1g

        // Estimated average stride length (very rough — improves with structural baseline height)
        private const val ESTIMATED_STRIDE_LENGTH_M = 0.75
    }

    /**
     * Analyse a walking segment.
     *
     * @param samplesZ   Vertical axis (z) acceleration in g — captures heel-strike impacts
     * @param samplesX   Lateral axis for asymmetry estimation
     * @param context    Must have activityState == WALKING (caller's responsibility)
     */
    fun analyse(
        samplesZ: DoubleArray,
        samplesX: DoubleArray,
        context: ContextVector
    ): GaitSession? {
        if (samplesZ.size < MIN_SAMPLES) return null

        // Detect step peaks in vertical acceleration
        val stepTimestamps = detectSteps(samplesZ)
        if (stepTimestamps.size < 4) return null  // need at least 4 steps for statistics

        // ── Step intervals ──
        val intervals = (1 until stepTimestamps.size).map {
            (stepTimestamps[it] - stepTimestamps[it - 1]) * 1000.0 / SAMPLE_RATE_HZ  // convert to ms
        }.filter { it in MIN_STEP_INTERVAL_MS..MAX_STEP_INTERVAL_MS }

        if (intervals.size < 3) return null

        // ── Cadence ──
        val meanIntervalMs = intervals.average()
        val cadenceStepsPerMin = if (meanIntervalMs > 0) 60_000.0 / meanIntervalMs else 0.0

        // ── Stride regularity (autocorrelation at ~2 step lag) ──
        val strideRegularity = computeStrideRegularity(stepTimestamps)

        // ── Step asymmetry ──
        // Compare left/right step intervals by alternating pairs
        // Even intervals ≈ right foot, odd intervals ≈ left foot (assumption: starts on right)
        val stepAsymmetry = computeStepAsymmetry(intervals)

        // ── Vertical impact ──
        val verticalImpact = stepTimestamps.map { idx ->
            samplesZ[minOf(idx, samplesZ.size - 1)]
        }.average()

        // ── Stride Time Variability (STV) — CV of stride intervals ──
        // STV is the most clinically validated gait biomarker for Parkinson's
        // CV > 3% is considered abnormal; PD patients typically show CV 5-15%
        val strideIntervals = (2 until stepTimestamps.size step 2).map {
            ((stepTimestamps[it] - stepTimestamps[it - 2]) * 1000.0 / SAMPLE_RATE_HZ)
        }
        val strideCv = if (strideIntervals.size >= 2) {
            val mean = strideIntervals.average()
            val std = kotlin.math.sqrt(strideIntervals.map { (it - mean) * (it - mean) }.average())
            if (mean > 0) (std / mean) * 100.0 else 0.0
        } else 0.0

        // ── Walking speed proxy ──
        val walkingSpeedProxy = (cadenceStepsPerMin / 2.0) * ESTIMATED_STRIDE_LENGTH_M / 60.0  // m/s

        val durationMs = (samplesZ.size * 1000L / SAMPLE_RATE_HZ)

        return GaitSession(
            sessionId = UUID.randomUUID().toString(),
            patientId = patientId,
            startMs = System.currentTimeMillis() - durationMs,
            endMs = System.currentTimeMillis(),
            stepCount = stepTimestamps.size,
            cadenceStepsPerMin = cadenceStepsPerMin,
            strideRegularityIndex = strideRegularity,
            stepAsymmetryIndex = stepAsymmetry,
            walkingSpeedProxy = walkingSpeedProxy,
            verticalImpactG = verticalImpact,
            strideCvPercent = strideCv,
            context = context,
            syncedToCloud = false
        )
    }

    /**
     * Detect heel-strike peaks in vertical acceleration.
     *
     * Uses simple threshold + local maximum detection.
     * Each heel strike produces a distinct impact spike above 1.2g.
     * Returns sample indices of detected step peaks.
     */
    private fun detectSteps(samplesZ: DoubleArray): List<Int> {
        val peaks = mutableListOf<Int>()
        val minSamplesPerStep = (MIN_STEP_INTERVAL_MS * SAMPLE_RATE_HZ / 1000).toInt()

        var i = 1
        while (i < samplesZ.size - 1) {
            val current = samplesZ[i]
            val prev = samplesZ[i - 1]
            val next = samplesZ[i + 1]

            // Local maximum above impact threshold
            if (current > IMPACT_THRESHOLD_G && current > prev && current >= next) {
                // Enforce minimum step distance
                if (peaks.isEmpty() || (i - peaks.last()) >= minSamplesPerStep) {
                    peaks.add(i)
                }
            }
            i++
        }
        return peaks
    }

    /**
     * Stride regularity via normalized autocorrelation at 2-step lag.
     *
     * A healthy gait pattern repeats with very high regularity (>0.9).
     * Ataxic, Parkinsonian shuffle, or pain-compensated gait shows reduced regularity.
     *
     * Reference: Moe-Nilssen 2004, "Test-retest reliability of trunk accelerometry
     * during standing and walking."
     */
    private fun computeStrideRegularity(stepTimestamps: List<Int>): Double {
        val intervals = (1 until stepTimestamps.size).map {
            (stepTimestamps[it] - stepTimestamps[it - 1]).toDouble()
        }
        if (intervals.size < 4) return 0.0

        val mean = intervals.average()
        val variance = intervals.map { (it - mean).pow(2) }.average()
        if (variance < 1e-9) return 1.0

        // Autocorrelation at lag 2 (one full stride = 2 steps)
        var autocorr = 0.0
        val lag = minOf(2, intervals.size - 1)
        for (i in 0 until intervals.size - lag) {
            autocorr += (intervals[i] - mean) * (intervals[i + lag] - mean)
        }
        autocorr /= ((intervals.size - lag) * variance)

        return autocorr.coerceIn(0.0, 1.0)
    }

    /**
     * Step asymmetry index.
     *
     * Alternating step intervals should be equal for symmetric gait.
     * Asymmetry = |mean_left - mean_right| / ((mean_left + mean_right) / 2)
     * 0 = perfectly symmetric, 1 = maximum asymmetry.
     *
     * Clinical note: >10% asymmetry is considered clinically significant
     * in Parkinson's disease gait assessment.
     */
    private fun computeStepAsymmetry(intervals: List<Double>): Double {
        if (intervals.size < 4) return 0.0

        val evenIntervals = intervals.filterIndexed { idx, _ -> idx % 2 == 0 }
        val oddIntervals = intervals.filterIndexed { idx, _ -> idx % 2 == 1 }

        val meanEven = evenIntervals.average()
        val meanOdd = oddIntervals.average()
        val combined = (meanEven + meanOdd) / 2.0

        return if (combined > 0) abs(meanEven - meanOdd) / combined else 0.0
    }
}
