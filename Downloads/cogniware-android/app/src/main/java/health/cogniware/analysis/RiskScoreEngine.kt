package health.cogniware.analysis

/**
 * RiskScoreEngine
 *
 * Combines multiple passive signals into a 0–100 risk score representing
 * the patient's current likelihood of experiencing a tremor episode or fall
 * in the next 30 minutes.
 *
 * Score interpretation:
 *   0–30:  Low — normal monitoring
 *   31–60: Elevated — log to database, subtle UI indicator
 *   61–80: High — notify patient: "Consider resting, avoid carrying hot liquids"
 *   81–100: Critical — notify patient + caregiver immediately
 *
 * Weights (tunable — will be replaced by ML model weights once training data exists):
 *   subThresholdTremorTrend  0.30  — rising sub-threshold FFT power in tremor band (phone accel)
 *   wearOffProximity         0.25  — how close we are to predicted wear-off time
 *   watchTremor              0.20  — raw tremor power ratio from paired Wear OS watch
 *   hrvDeviation             0.15  — HRV below patient's own baseline
 *   fatigueAccumulation      0.10  — step count + activity pattern suggesting fatigue
 *
 * All inputs are normalised to 0–1 before weighting.
 */
data class RiskInput(
    val patientId: String,
    val timestampMs: Long,

    // ── Phone accelerometer (sub-threshold, background) ───────────────────────
    // Ratio of current 4-6Hz band power to patient's quiet baseline.
    // 1.0 = at baseline, 2.0 = double baseline, etc.
    val subThresholdTremorRatio: Double,

    // ── Medication timing ─────────────────────────────────────────────────────
    // Minutes since last dose. Null if no dose logged yet.
    val minutesSinceLastDose: Double?,
    // Expected duration of effect in minutes (from MedicationSchedule).
    val expectedDurationMinutes: Int?,

    // ── Watch tremor (Wear OS accelerometer FFT) ──────────────────────────────
    // Fraction of FFT power in 4–12 Hz band. Null if no watch paired.
    val watchTremorPowerRatio: Double?,
    // Peak tremor frequency in Hz. Null if no watch or no tremor detected.
    val watchDominantFreqHz: Double?,

    // ── Heart rate variability ────────────────────────────────────────────────
    // Current HRV RMSSD in ms. Null if no wearable connected.
    val currentHrvMs: Double?,
    // Patient's 30-day baseline HRV RMSSD in ms. Null if baseline not established.
    val baselineHrvMs: Double?,

    // ── Activity / fatigue ────────────────────────────────────────────────────
    val stepsToday: Int,
    val baselineSteps: Int
)

data class RiskScore(
    val score: Int,                              // 0–100
    val level: RiskLevel,
    val dominantFactor: String,                  // which factor contributed most
    val componentScores: Map<String, Double>,    // for debugging/display
    val timestampMs: Long
)

enum class RiskLevel { LOW, ELEVATED, HIGH, CRITICAL }

object RiskScoreEngine {

    fun compute(input: RiskInput): RiskScore {

        // 1. Sub-threshold phone tremor component (0–1)
        //    ratio 1.0 → 0.0, ratio 2.0 → 0.5, ratio 3.0+ → 1.0
        val tremorComponent = ((input.subThresholdTremorRatio - 1.0) / 2.0).coerceIn(0.0, 1.0)

        // 2. Wear-off proximity component (0–1)
        val wearOffComponent = computeWearOffComponent(
            input.minutesSinceLastDose,
            input.expectedDurationMinutes
        )

        // 3. Watch tremor component (0–1)
        //    tremorPowerRatio 0.0 → 0.0 (no tremor), 0.3+ → 1.0 (significant tremor)
        //    Null (no watch) → 0.0 (don't penalise unpaired patients)
        val watchTremorComponent = input.watchTremorPowerRatio
            ?.let { (it / 0.30).coerceIn(0.0, 1.0) }
            ?: 0.0

        // 4. HRV deviation component (0–1)
        val hrvComponent = computeHrvComponent(input.currentHrvMs, input.baselineHrvMs)

        // 5. Fatigue component (0–1)
        val fatigueComponent = (input.stepsToday.toDouble() / input.baselineSteps.coerceAtLeast(1))
            .coerceIn(0.0, 1.5)
            .let { ratio -> if (ratio > 1.0) (ratio - 1.0) * 2.0 else 0.0 }
            .coerceIn(0.0, 1.0)

        val weighted = tremorComponent      * 0.30 +
                       wearOffComponent     * 0.25 +
                       watchTremorComponent * 0.20 +
                       hrvComponent         * 0.15 +
                       fatigueComponent     * 0.10

        val score = (weighted * 100).toInt().coerceIn(0, 100)

        val components = mapOf(
            "subThresholdTremor" to tremorComponent,
            "wearOffProximity"   to wearOffComponent,
            "watchTremor"        to watchTremorComponent,
            "hrvDeviation"       to hrvComponent,
            "fatigue"            to fatigueComponent
        )
        val dominant = components.maxByOrNull { it.value }?.key ?: "unknown"

        val level = when {
            score >= 81 -> RiskLevel.CRITICAL
            score >= 61 -> RiskLevel.HIGH
            score >= 31 -> RiskLevel.ELEVATED
            else        -> RiskLevel.LOW
        }

        return RiskScore(score, level, dominant, components, input.timestampMs)
    }

    private fun computeWearOffComponent(
        minutesSinceLastDose: Double?,
        expectedDurationMinutes: Int?
    ): Double {
        if (minutesSinceLastDose == null || expectedDurationMinutes == null) return 0.3
        val progress = minutesSinceLastDose / expectedDurationMinutes.toDouble()
        return when {
            progress < 0.5  -> 0.0
            progress < 0.75 -> (progress - 0.5) * 2.0
            else            -> ((progress - 0.75) * 4.0).coerceIn(0.0, 1.0)
        }
    }

    private fun computeHrvComponent(currentHrv: Double?, baselineHrv: Double?): Double {
        if (currentHrv == null || baselineHrv == null || baselineHrv <= 0) return 0.0
        return (1.0 - currentHrv / baselineHrv).coerceIn(0.0, 1.0)
    }
}
