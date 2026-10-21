package health.cogniware.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dose_events")
data class DoseEvent(
    @PrimaryKey val eventId: String,
    val patientId: String,
    val scheduleId: String,             // FK to MedicationSchedule
    val drugName: String,
    val doseMg: Double,
    val takenAtMs: Long,                // when patient logged they took it
    val loggedAtMs: Long,               // when the log entry was created (may differ)
    val logMethod: String,              // "MANUAL" | "INFERRED" | "REMINDER_CONFIRMED"
    val hrvAtDoseMs: Double?,           // HRV RMSSD at time of dose (for correlation)
    val hrAtDoseBpm: Double?,           // HR at time of dose
    val predictedWearOffMs: Long?,      // takenAtMs + expectedDurationMinutes (computed on insert)
    val actualWearOffMs: Long?,         // filled in retrospectively when wear-off detected
    val syncedToCloud: Boolean = false
)
