package health.cogniware.debug

import android.content.Context
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.sin
import kotlin.random.Random

object DebugDataSeeder {

    suspend fun seed14Days(context: Context) = withContext(Dispatchers.IO) {
        val db = CogniwareDatabase.getInstance(context)
        val patientId = db.profileDao().getAny()?.patientId ?: "debug-patient-001"
        val rng = Random(42)
        val nowMs = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000

        repeat(14) { dayOffset ->
            val dayStartMs = nowMs - (13 - dayOffset) * dayMs
            val wakingHours = listOf(8, 10, 12, 14, 16, 18, 20, 22)
            wakingHours.forEach { hour ->
                val tMs = dayStartMs + hour * 3600_000L
                val wearOffFactor = if (hour in 15..18) 1.4 else 1.0
                val basePower = 0.018 + dayOffset * 0.0003
                val rmsAmp = (basePower * wearOffFactor * (0.9 + rng.nextDouble() * 0.2)).coerceIn(0.008, 0.12)
                val peakHz = 4.5 + rng.nextDouble() * 2.0
                db.tremorDao().insert(TremorReading(
                    readingId = UUID.randomUUID().toString(),
                    patientId = patientId,
                    timestampMs = tMs + rng.nextLong(0, 1800_000),
                    durationMs = 30_000,
                    peakFrequencyHz = peakHz,
                    powerAtPeakDb = -20.0 + rng.nextDouble() * 5,
                    power4to7Hz = rmsAmp * 0.7,
                    power4to12Hz = rmsAmp * 0.85,
                    power2to4Hz = rmsAmp * 0.15,
                    totalPowerDb = -15.0 + rng.nextDouble() * 4,
                    rmsAmplitude = rmsAmp,
                    laterality = Laterality.RIGHT_DOMINANT,
                    tremorBandHint = TremorBand.PARKINSONIAN,
                    context = makeContext(tMs, dayOffset),
                    syncedToCloud = false
                ))
            }

            listOf(9, 16).forEach { hour ->
                val gMs = dayStartMs + hour * 3600_000L
                val fatigueAdj = if (hour > 14) 0.92 else 1.0
                val cadence = (108.0 + rng.nextDouble() * 12 - dayOffset * 0.2) * fatigueAdj
                val asym = 0.06 + dayOffset * 0.002 + rng.nextDouble() * 0.03
                db.gaitDao().insert(GaitSession(
                    sessionId = UUID.randomUUID().toString(),
                    patientId = patientId,
                    startMs = gMs,
                    endMs = gMs + 8 * 60_000,
                    stepCount = (cadence * 8).toInt(),
                    cadenceStepsPerMin = cadence,
                    strideRegularityIndex = 0.78 - dayOffset * 0.003 + rng.nextDouble() * 0.05,
                    stepAsymmetryIndex = asym.coerceIn(0.0, 0.35),
                    walkingSpeedProxy = 1.1 - dayOffset * 0.005 + rng.nextDouble() * 0.1,
                    verticalImpactG = 1.8 + rng.nextDouble() * 0.3,
                    context = makeContext(gMs, dayOffset),
                    syncedToCloud = false
                ))
            }

            listOf(9, 11, 14, 20).forEach { hour ->
                val kMs = dayStartMs + hour * 3600_000L
                val basIki = 180.0 + dayOffset * 1.5
                val wpmBase = 42.0 - dayOffset * 0.3
                db.keystrokeDao().insert(KeystrokeSession(
                    sessionId = UUID.randomUUID().toString(),
                    patientId = patientId,
                    startMs = kMs,
                    endMs = kMs + 12 * 60_000,
                    ikiMeanMs = basIki + rng.nextDouble() * 30,
                    ikiCvPercent = 28.0 + rng.nextDouble() * 8,
                    ikiP10Ms = basIki * 0.4,
                    ikiP90Ms = basIki * 2.1,
                    flightTimeMeanMs = 92.0 + dayOffset * 0.8 + rng.nextDouble() * 15,
                    flightTimeCvPercent = 22.0 + rng.nextDouble() * 6,
                    backspaceRatePercent = 4.5 + dayOffset * 0.1 + rng.nextDouble() * 2,
                    correctionBurstCount = (1 + rng.nextDouble() * 3).toInt(),
                    wordBoundaryPauseMeanMs = 380.0 + rng.nextDouble() * 60,
                    withinWordPauseMeanMs = basIki * 0.9,
                    longPauseCount = (rng.nextDouble() * 4 + dayOffset * 0.2).toInt(),
                    estimatedWpm = (wpmBase + rng.nextDouble() * 6).coerceAtLeast(20.0),
                    totalKeystrokes = 280 + (rng.nextDouble() * 120).toInt(),
                    context = makeContext(kMs, dayOffset),
                    syncedToCloud = false
                ))
            }

            val dateKey = run {
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = dayStartMs
                "%04d-%02d-%02d".format(cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
            }
            val sleepMin = (420 + (sin(dayOffset * 0.8) * 35 + rng.nextDouble() * 20)).toInt()
            val hrvVal = 38.0 - dayOffset * 0.4 + sin(dayOffset.toDouble()) * 4 + rng.nextDouble() * 3
            val stepsVal = 6200 + (sin(dayOffset * 0.5) * 1500 + rng.nextDouble() * 800).toInt()
            db.healthDao().insert(HealthDailySummary(
                dateKey = dateKey, patientId = patientId,
                restingHeartRateBpm = 64.0 + rng.nextDouble() * 5,
                heartRateMeanBpm = 72.0 + rng.nextDouble() * 6,
                heartRateMinBpm = 58.0 + rng.nextDouble() * 4,
                heartRateMaxBpm = 118.0 + rng.nextDouble() * 20,
                hrvRmssdMs = hrvVal.coerceIn(20.0, 60.0),
                sleepTotalMinutes = sleepMin,
                sleepDeepMinutes = (sleepMin * 0.22).toInt(),
                sleepRemMinutes = (sleepMin * 0.21).toInt(),
                sleepLightMinutes = (sleepMin * 0.48).toInt(),
                sleepAwakeMinutes = (sleepMin * 0.09).toInt(),
                sleepOnsetMs = dayStartMs - 7 * 3600_000L,
                wakeTimeMs = dayStartMs + (sleepMin - 7 * 60) * 60_000L,
                stepCount = stepsVal.coerceAtLeast(800),
                activeCaloriesBurned = stepsVal * 0.045,
                totalCaloriesBurned = 1900.0 + rng.nextDouble() * 200,
                activeMinutes = (stepsVal / 100).coerceIn(20, 90),
                exerciseSessionMinutes = if (dayOffset % 3 == 0) 30 else null,
                spO2MeanPercent = 97.5 + rng.nextDouble() * 1.5,
                spO2MinPercent = 94.0 + rng.nextDouble() * 2,
                syncedToCloud = false
            ))
        }
    }

    suspend fun clearAll(context: Context) = withContext(Dispatchers.IO) {
        CogniwareDatabase.getInstance(context).clearAllTables()
    }

    private fun makeContext(timestampMs: Long, dayOffset: Int): ContextVector {
        val hour = ((timestampMs / 3600_000) % 24).toInt()
        return ContextVector(
            timestampMs = timestampMs,
            activityState = if (hour in 9..10 || hour in 15..17) ActivityState.WALKING else ActivityState.STILL,
            deviceOrientation = DeviceOrientation.UPRIGHT,
            hourOfDay = hour,
            dayOfWeek = ((dayOffset % 7) + 1),
            minutesSinceLastInteraction = 15L,
            heartRateZone = if (hour in 9..10 || hour in 15..17) HeartRateZone.LIGHT else HeartRateZone.RESTING,
            lastNightSleepMinutes = 420 + (dayOffset % 3) * 20,
            stepsTodaySoFar = hour * 280,
            baselinePhase = BaselinePhase.BUILDING
        )
    }
}
