package health.cogniware.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * BloodPressureManager
 *
 * Reads blood pressure from Health Connect.
 *
 * Compatible sources (via Health Connect):
 *   Omron Connect, Withings Health Mate, Samsung Health, Qardio, iHealth
 *
 * Clinical relevance for Cogniware:
 *   - Levodopa + antihypertensives → orthostatic hypotension → fall risk
 *   - Autonomic dysfunction (PD, MSA) → BP lability throughout the day
 *   - Hypertensive urgency (>180/120) can mimic neurological events
 *   - Pulse pressure (systolic - diastolic) > 60 mmHg → arterial stiffness marker
 *
 * Permission: android.permission.health.READ_BLOOD_PRESSURE
 * (Add to manifest + HealthConnectManager.REQUIRED_PERMISSIONS)
 */
class BloodPressureManager(private val context: Context) {

    private val client: HealthConnectClient? by lazy {
        try { HealthConnectClient.getOrCreate(context) } catch (e: Exception) { null }
    }

    companion object {
        val PERMISSION = HealthPermission.getReadPermission(BloodPressureRecord::class)
    }

    data class BpReading(
        val systolicMmHg: Double,
        val diastolicMmHg: Double,
        val timestampMs: Long
    ) {
        /** WHO/AHA classification */
        val classification: String get() = when {
            systolicMmHg < 90  || diastolicMmHg < 60   -> "Hypotension"
            systolicMmHg < 120 && diastolicMmHg < 80   -> "Normal"
            systolicMmHg < 130 && diastolicMmHg < 80   -> "Elevated"
            systolicMmHg < 140 || diastolicMmHg < 90   -> "Stage 1 Hypertension"
            systolicMmHg >= 180 || diastolicMmHg >= 120 -> "Hypertensive Crisis"
            else                                         -> "Stage 2 Hypertension"
        }

        /** Pulse pressure — arterial stiffness marker (>60 mmHg is clinically significant) */
        val pulsePressure: Double get() = systolicMmHg - diastolicMmHg

        /** True when BP is in hypertensive crisis range — triggers EmergencySOS */
        val isCrisis: Boolean get() = systolicMmHg >= 180 || diastolicMmHg >= 120

        /** True when orthostatic hypotension is likely — elevated fall risk */
        val isHypotensive: Boolean get() = systolicMmHg < 90 || diastolicMmHg < 60
    }

    data class DailySummary(
        val readings: List<BpReading>,
        val meanSystolic: Double?,
        val meanDiastolic: Double?,
        val minSystolic: Double?,
        val maxSystolic: Double?,
        val classification: String?         // based on mean values
    )

    /**
     * Pull blood pressure readings for the given time range.
     * Returns an empty list if Health Connect is unavailable or permission not granted.
     */
    suspend fun readRange(startMs: Long, endMs: Long): List<BpReading> {
        val c = client ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val range = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startMs),
                    Instant.ofEpochMilli(endMs)
                )
                c.readRecords(ReadRecordsRequest(BloodPressureRecord::class, range))
                    .records
                    .map { record ->
                        BpReading(
                            systolicMmHg  = record.systolic.inMillimetersOfMercury,
                            diastolicMmHg = record.diastolic.inMillimetersOfMercury,
                            timestampMs   = record.time.toEpochMilli()
                        )
                    }
                    .sortedBy { it.timestampMs }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Summarise a day's BP readings.
     * Call with startMs = midnight, endMs = next midnight.
     */
    suspend fun dailySummary(startMs: Long, endMs: Long): DailySummary {
        val readings = readRange(startMs, endMs)
        if (readings.isEmpty()) return DailySummary(emptyList(), null, null, null, null, null)

        val meanSys  = readings.map { it.systolicMmHg }.average()
        val meanDia  = readings.map { it.diastolicMmHg }.average()

        val summaryReading = BpReading(meanSys, meanDia, readings.last().timestampMs)

        return DailySummary(
            readings       = readings,
            meanSystolic   = meanSys,
            meanDiastolic  = meanDia,
            minSystolic    = readings.minOf { it.systolicMmHg },
            maxSystolic    = readings.maxOf { it.systolicMmHg },
            classification = summaryReading.classification
        )
    }

    /**
     * Check if any reading in the last [windowMs] milliseconds is in crisis range.
     * Used by PassiveSensingService to decide whether to fire EmergencySOS.
     */
    suspend fun hasCrisisReading(windowMs: Long = 30 * 60 * 1000L): Boolean {
        val now = System.currentTimeMillis()
        val readings = readRange(now - windowMs, now)
        return readings.any { it.isCrisis }
    }

    suspend fun hasPermission(): Boolean {
        val c = client ?: return false
        return try {
            PERMISSION in c.permissionController.getGrantedPermissions()
        } catch (e: Exception) { false }
    }
}
