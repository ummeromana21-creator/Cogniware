package health.cogniware.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.data.models.*
import health.cogniware.sensors.ContextProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.*

/**
 * HealthConnectManager
 *
 * Reads wearable and health data via Google Health Connect API.
 *
 * Health Connect is the Android health data hub — it reads from:
 *   - Oura Ring (via Oura app)
 *   - Fitbit (via Fitbit app)
 *   - Garmin (via Garmin Connect app)
 *   - Samsung Health
 *   - Whoop
 *   - Google Fit
 *   - Any other Health Connect-compatible source
 *
 * We read passively — we never write to Health Connect.
 *
 * Clinical significance of each metric:
 *   HRV (RMSSD)    → Autonomic nervous system state, fatigue, medication compliance
 *   Resting HR     → Cardiovascular trend, thyroid, medication side effects
 *   Sleep stages   → REM critical for memory consolidation; slow-wave for brain clearance
 *   Steps          → Motor function trend, activity level confound
 *   SpO2           → Sleep apnea screening, hypoxia
 *   Sleep onset    → Circadian rhythm marker (critical for bipolar mood phase prediction)
 */
class HealthConnectManager(private val context: Context) {

    private val client: HealthConnectClient? by lazy {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            null  // Health Connect not available on this device
        }
    }

    private val db = CogniwareDatabase.getInstance(context)
    private val contextProvider = ContextProvider.getInstance(context)

    companion object {
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        )
    }

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        client != null
    }

    suspend fun hasPermissions(): Boolean = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext false
        val granted = c.permissionController.getGrantedPermissions()
        REQUIRED_PERMISSIONS.all { it in granted }
    }

    /**
     * Pull yesterday's summary from Health Connect.
     * Called once per day by the sync worker.
     */
    suspend fun pullDailySummary(patientId: String, dateKey: String): HealthDailySummary? {
        val c = client ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val date = LocalDate.parse(dateKey)
                val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
                val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                val timeRange = TimeRangeFilter.between(startOfDay, endOfDay)

                // ── Heart rate ──
                val hrRecords = c.readRecords(
                    ReadRecordsRequest(HeartRateRecord::class, timeRange)
                ).records
                val allHrSamples = hrRecords.flatMap { record ->
                    record.samples.map { it.beatsPerMinute.toDouble() }
                }

                // ── HRV ──
                val hrvRecords = c.readRecords(
                    ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, timeRange)
                ).records
                val meanHrv = if (hrvRecords.isNotEmpty())
                    hrvRecords.map { it.heartRateVariabilityMillis }.average() else null

                // ── Resting HR ──
                val restingHrRecords = c.readRecords(
                    ReadRecordsRequest(RestingHeartRateRecord::class, timeRange)
                ).records
                val restingHr = restingHrRecords.lastOrNull()?.beatsPerMinute?.toDouble()

                // ── Steps ──
                val stepsRecords = c.readRecords(
                    ReadRecordsRequest(StepsRecord::class, timeRange)
                ).records
                val totalSteps = stepsRecords.sumOf { it.count }.toInt()

                // ── Calories ──
                val activeCalRecords = c.readRecords(
                    ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRange)
                ).records
                val totalCalRecords = c.readRecords(
                    ReadRecordsRequest(TotalCaloriesBurnedRecord::class, timeRange)
                ).records
                val activeCal = activeCalRecords.sumOf { it.energy.inKilocalories }
                val totalCal = totalCalRecords.sumOf { it.energy.inKilocalories }

                // ── SpO2 ──
                val spo2Records = c.readRecords(
                    ReadRecordsRequest(OxygenSaturationRecord::class, timeRange)
                ).records
                val spo2Values = spo2Records.map { it.percentage.value }

                // ── Sleep ──
                val sleepRecords = c.readRecords(
                    ReadRecordsRequest(SleepSessionRecord::class, timeRange)
                ).records
                val sleepData = processSleepRecords(sleepRecords)

                // ── Exercise ──
                val exerciseRecords = c.readRecords(
                    ReadRecordsRequest(ExerciseSessionRecord::class, timeRange)
                ).records
                val exerciseMinutes = exerciseRecords.sumOf {
                    ChronoUnit.MINUTES.between(it.startTime, it.endTime).toInt()
                }

                // ── Compute heart rate zone for context ──
                val meanHr = allHrSamples.let { if (it.isEmpty()) null else it.average() }
                val zone = classifyHeartRateZone(meanHr, restingHr)
                contextProvider.updateHeartRateZone(zone)
                contextProvider.updateSleepMinutes(sleepData.totalMinutes)
                contextProvider.updateStepsToday(totalSteps)

                HealthDailySummary(
                    dateKey = dateKey,
                    patientId = patientId,
                    restingHeartRateBpm = restingHr,
                    heartRateMeanBpm = allHrSamples.let { if (it.isEmpty()) null else it.average() },
                    heartRateMinBpm = allHrSamples.minOrNull(),
                    heartRateMaxBpm = allHrSamples.maxOrNull(),
                    hrvRmssdMs = meanHrv,
                    sleepTotalMinutes = if (sleepData.totalMinutes > 0) sleepData.totalMinutes else null,
                    sleepDeepMinutes = sleepData.deepMinutes.takeIf { it > 0 },
                    sleepRemMinutes = sleepData.remMinutes.takeIf { it > 0 },
                    sleepLightMinutes = sleepData.lightMinutes.takeIf { it > 0 },
                    sleepAwakeMinutes = sleepData.awakeMinutes.takeIf { it > 0 },
                    sleepOnsetMs = sleepData.onsetMs,
                    wakeTimeMs = sleepData.wakeMs,
                    stepCount = totalSteps.takeIf { it > 0 },
                    activeCaloriesBurned = activeCal.takeIf { it > 0 },
                    totalCaloriesBurned = totalCal.takeIf { it > 0 },
                    activeMinutes = sleepData.activeMinutes.takeIf { it > 0 },
                    exerciseSessionMinutes = exerciseMinutes.takeIf { it > 0 },
                    spO2MeanPercent = spo2Values.let { if (it.isEmpty()) null else it.average() },
                    spO2MinPercent = spo2Values.minOrNull(),
                    syncedToCloud = false
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private data class SleepData(
        val totalMinutes: Int = 0,
        val deepMinutes: Int = 0,
        val remMinutes: Int = 0,
        val lightMinutes: Int = 0,
        val awakeMinutes: Int = 0,
        val onsetMs: Long? = null,
        val wakeMs: Long? = null,
        val activeMinutes: Int = 0
    )

    private fun processSleepRecords(records: List<SleepSessionRecord>): SleepData {
        if (records.isEmpty()) return SleepData()

        // Take the longest sleep session (primary sleep, not naps)
        val primary = records.maxByOrNull { it.endTime.toEpochMilli() - it.startTime.toEpochMilli() }
            ?: return SleepData()

        var deepMins = 0; var remMins = 0; var lightMins = 0; var awakeMins = 0

        primary.stages.forEach { stage ->
            val durationMins = ChronoUnit.MINUTES.between(stage.startTime, stage.endTime).toInt()
            when (stage.stage) {
                SleepSessionRecord.STAGE_TYPE_DEEP  -> deepMins += durationMins
                SleepSessionRecord.STAGE_TYPE_REM   -> remMins += durationMins
                SleepSessionRecord.STAGE_TYPE_LIGHT -> lightMins += durationMins
                SleepSessionRecord.STAGE_TYPE_AWAKE -> awakeMins += durationMins
                else -> lightMins += durationMins  // unspecified → count as light
            }
        }

        val totalMins = ChronoUnit.MINUTES.between(primary.startTime, primary.endTime).toInt()

        return SleepData(
            totalMinutes = totalMins,
            deepMinutes = deepMins,
            remMinutes = remMins,
            lightMinutes = lightMins,
            awakeMinutes = awakeMins,
            onsetMs = primary.startTime.toEpochMilli(),
            wakeMs = primary.endTime.toEpochMilli()
        )
    }

    /**
     * Classify heart rate zone from mean HR and resting HR.
     * Uses Karvonen method approximation.
     * Without max HR, we use age-estimated 220-age, defaulting to 180 if unknown.
     */
    private fun classifyHeartRateZone(meanHr: Double?, restingHr: Double?): HeartRateZone {
        val hr = meanHr ?: return HeartRateZone.UNKNOWN
        val rhr = restingHr ?: 60.0
        val maxHr = 180.0  // conservative default

        val reserve = maxHr - rhr
        val percentReserve = if (reserve > 0) (hr - rhr) / reserve else 0.0

        return when {
            percentReserve < 0.1  -> HeartRateZone.RESTING
            percentReserve < 0.5  -> HeartRateZone.LIGHT
            percentReserve < 0.7  -> HeartRateZone.MODERATE
            else                   -> HeartRateZone.VIGOROUS
        }
    }
}
