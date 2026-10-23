package health.cogniware.analysis

data class RiskInput(
    val patientId: String,
    val timestampMs: Long,
    val subThresholdTremorRatio: Double,
    val minutesSinceLastDose: Double?,
    val expectedDurationMinutes: Int?,
    val watchTremorPowerRatio: Double?,
    val watchDominantFreqHz: Double?,
    val currentHrvMs: Double?,
    val baselineHrvMs: Double?,
    val stepsToday: Int,
    val baselineSteps: Int,
    val gaitPersonalZScore: Double? = null,
    val keystrokePersonalZScore: Double? = null,
    val hyStage: String = "",
    val dominantHand: String = "right",
    val primarySymptomType: String = ""
)

data class RiskScore(
    val score: Int,
    val level: RiskLevel,
    val dominantFactor: String,
    val componentScores: Map<String, Double>,
    val timestampMs: Long
)

enum class RiskLevel { LOW, ELEVATED, HIGH, CRITICAL }

object RiskScoreEngine {

    fun compute(input: RiskInput): RiskScore {

        val tremorBoost = when (input.primarySymptomType) {
            "tremor_dominant" -> 1.10
            "akinetic_rigid"  -> 0.90
            else              -> 1.00
        }
        val motorBoost = when (input.primarySymptomType) {
            "akinetic_rigid"  -> 1.10
            "tremor_dominant" -> 0.90
            else              -> 1.00
        }

        val tremorComponent = (((input.subThresholdTremorRatio - 1.0) / 2.0) * tremorBoost)
            .coerceIn(0.0, 1.0)

        val wearOffComponent = computeWearOffComponent(
            input.minutesSinceLastDose,
            input.expectedDurationMinutes
        )

        val watchTremorComponent = (input.watchTremorPowerRatio
            ?.let { (it / 0.30) * tremorBoost }
            ?: 0.0).coerceIn(0.0, 1.0)

        val hrvComponent = computeHrvComponent(input.currentHrvMs, input.baselineHrvMs)

        val fatigueComponent = (input.stepsToday.toDouble() / input.baselineSteps.coerceAtLeast(1))
            .coerceIn(0.0, 1.5)
            .let { ratio -> if (ratio > 1.0) (ratio - 1.0) * 2.0 else 0.0 }
            .coerceIn(0.0, 1.0)

        val gaitPersonalComponent = (input.gaitPersonalZScore
            ?.let { z -> (z / 3.0) * motorBoost }
            ?: 0.0).coerceIn(0.0, 1.0)

        val keystrokePersonalComponent = (input.keystrokePersonalZScore
            ?.let { z -> (z / 3.0) * motorBoost }
            ?: 0.0).coerceIn(0.0, 1.0)

        val weighted = tremorComponent            * 0.25 +
                       wearOffComponent           * 0.20 +
                       watchTremorComponent       * 0.15 +
                       hrvComponent               * 0.10 +
                       fatigueComponent           * 0.05 +
                       gaitPersonalComponent      * 0.15 +
                       keystrokePersonalComponent * 0.10

        val stageMult = when (input.hyStage.trim()) {
            "1", "1.5"  -> 1.00
            "2", "2.5"  -> 1.10
            "3"         -> 1.20
            "4"         -> 1.30
            "5"         -> 1.40
            else        -> 1.00
        }

        val rawScore = (weighted * stageMult * 100).toInt().coerceIn(0, 100)

        val components = mapOf(
            "subThresholdTremor"   to tremorComponent,
            "wearOffProximity"     to wearOffComponent,
            "watchTremor"          to watchTremorComponent,
            "hrvDeviation"         to hrvComponent,
            "fatigue"              to fatigueComponent,
            "gaitPersonal"         to gaitPersonalComponent,
            "keystrokePersonal"    to keystrokePersonalComponent
        )
        val dominant = components.maxByOrNull { it.value }?.key ?: "unknown"

        val level = when {
            rawScore >= 81 -> RiskLevel.CRITICAL
            rawScore >= 61 -> RiskLevel.HIGH
            rawScore >= 31 -> RiskLevel.ELEVATED
            else           -> RiskLevel.LOW
        }

        return RiskScore(rawScore, level, dominant, components, input.timestampMs)
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
