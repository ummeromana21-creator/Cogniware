package health.cogniware.analysis

import health.cogniware.data.local.PersonalBaselineDao
import health.cogniware.data.models.GaitSession
import health.cogniware.data.models.KeystrokeSession
import health.cogniware.data.models.PersonalBaseline

/**
 * BaselineEngine — personal Bayesian adaptation layer.
 *
 * After every new sensor session, call [updateFromGait] or [updateFromKeystroke].
 * The engine maintains a per-feature Welford running mean/variance, which acts
 * as the patient's personal baseline.
 *
 * When risk scoring, call [zScoreGait] / [zScoreKeystroke] to get z-scores
 * relative to that patient's own history instead of population norms.
 *
 * Why Welford?
 *   - Numerically stable (no catastrophic cancellation for large n)
 *   - O(1) per update, no stored history needed
 *   - Natural Bayesian flavour: early sessions have high uncertainty (wide CI);
 *     as n grows the estimate tightens around the true personal mean
 */
class BaselineEngine(private val dao: PersonalBaselineDao) {

    // ──────────────────────────────────────────────────────────────────────
    // Update helpers
    // ──────────────────────────────────────────────────────────────────────

    suspend fun updateFromGait(session: GaitSession) {
        update("gait_stride_cv",   session.strideCvPercent)
        update("gait_cadence",     session.cadenceStepsPerMin)
        update("gait_step_asym",   session.stepAsymmetryIndex)
    }

    suspend fun updateFromKeystroke(session: KeystrokeSession) {
        update("keystroke_iki_mean_ms", session.ikiMeanMs)
        update("keystroke_iki_cv_pct",   session.ikiCvPercent)
        update("keystroke_ppl",      session.pausePerLetterMs)
        update("keystroke_asym",     session.bilateralAsymmetryIndex)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Z-score helpers (null = baseline not yet meaningful, treat as neutral)
    // ──────────────────────────────────────────────────────────────────────

    suspend fun zScoreGait(session: GaitSession): GaitZScores {
        return GaitZScores(
            strideCv   = zScore("gait_stride_cv",   session.strideCvPercent),
            cadence    = zScore("gait_cadence",     session.cadenceStepsPerMin),
            swingSym   = zScore("gait_step_asym",   session.stepAsymmetryIndex)
        )
    }

    suspend fun zScoreKeystroke(session: KeystrokeSession): KeystrokeZScores {
        return KeystrokeZScores(
            ikiMean = zScore("keystroke_iki_mean_ms", session.ikiMeanMs),
            ikiCv   = zScore("keystroke_iki_cv_pct",   session.ikiCvPercent),
            ppl     = zScore("keystroke_ppl",      session.pausePerLetterMs),
            asym    = zScore("keystroke_asym",     session.bilateralAsymmetryIndex)
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Core Welford online update
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun update(key: String, value: Double) {
        if (value.isNaN() || value.isInfinite()) return
        val prior = dao.getBaseline(key) ?: PersonalBaseline(featureKey = key)
        val nNew   = prior.n + 1
        val delta  = value - prior.mean
        val meanNew = prior.mean + delta / nNew
        val delta2 = value - meanNew
        val m2New  = prior.m2 + delta * delta2
        dao.upsert(
            prior.copy(
                n              = nNew,
                mean           = meanNew,
                m2             = m2New,
                lastUpdatedMs  = System.currentTimeMillis()
            )
        )
    }

    private suspend fun zScore(key: String, value: Double): Double? {
        val baseline = dao.getBaseline(key) ?: return null
        return baseline.zScore(value)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Result containers
    // ──────────────────────────────────────────────────────────────────────

    data class GaitZScores(
        val strideCv: Double?,
        val cadence:  Double?,
        val swingSym: Double?
    ) {
        /** Combined deviation: RMS of non-null z-scores, null if all null */
        val combined: Double? get() {
            val nonNull = listOfNotNull(strideCv, cadence, swingSym)
            return if (nonNull.isEmpty()) null
            else kotlin.math.sqrt(nonNull.sumOf { it * it } / nonNull.size)
        }
    }

    data class KeystrokeZScores(
        val ikiMean: Double?,
        val ikiCv:   Double?,
        val ppl:     Double?,
        val asym:    Double?
    ) {
        val combined: Double? get() {
            val nonNull = listOfNotNull(ikiMean, ikiCv, ppl, asym)
            return if (nonNull.isEmpty()) null
            else kotlin.math.sqrt(nonNull.sumOf { it * it } / nonNull.size)
        }
    }
}
