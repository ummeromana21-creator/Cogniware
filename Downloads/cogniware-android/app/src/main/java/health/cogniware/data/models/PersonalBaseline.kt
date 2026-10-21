package health.cogniware.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores per-patient rolling baseline statistics for each clinical feature.
 *
 * One row per feature per patient. Updated after every new session using
 * Welford's online algorithm for numerically stable running mean/variance.
 *
 * Features tracked:
 *   gait_stride_cv       — stride time variability (CV %)
 *   gait_cadence         — steps per minute
 *   gait_swing_sym       — swing symmetry index (0 = perfect)
 *   keystroke_iki_mean   — mean inter-key interval (ms)
 *   keystroke_iki_cv     — CV of IKI
 *   keystroke_ppl        — pause-per-letter (ms)
 *   keystroke_asym       — bilateral asymmetry index
 *   tremor_rms           — wrist tremor RMS (g)
 */
@Entity(tableName = "personal_baselines")
data class PersonalBaseline(
    @PrimaryKey
    val featureKey: String,           // e.g. "gait_stride_cv"

    val n: Int = 0,                   // Welford sample count
    val mean: Double = 0.0,           // running mean
    val m2: Double = 0.0,             // running sum of squared deviations (for variance)

    val lastUpdatedMs: Long = 0L
) {
    /** Population variance (biased, stable for display) */
    val variance: Double get() = if (n > 0) m2 / n else 0.0

    /** Population std-dev */
    val stdDev: Double get() = kotlin.math.sqrt(variance)

    /**
     * Z-score of a new observation against this baseline.
     * Returns null when std-dev is zero (baseline not yet meaningful).
     */
    fun zScore(value: Double): Double? =
        if (stdDev > 0.0) (value - mean) / stdDev else null
}
