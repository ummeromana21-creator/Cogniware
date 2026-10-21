package health.cogniware.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_schedules")
data class MedicationSchedule(
    @PrimaryKey val scheduleId: String,
    val patientId: String,
    val drugName: String,               // e.g. "Levodopa/Carbidopa"
    val doseMg: Double,
    val frequencyPerDay: Int,
    val doseTimesJson: String,          // JSON array of "HH:mm" strings e.g. ["08:00","12:00","17:00"]
    val conditionTarget: String,        // "parkinson" | "ms" | "als" | "adhd" etc.
    val medicationModel: String,        // "LEVODOPA_CYCLE" | "STIMULANT_CYCLE" | "NONE"
    val expectedOnsetMinutes: Int,      // typical minutes to peak effect
    val expectedDurationMinutes: Int,   // typical duration of effect in minutes
    val notes: String = "",
    val active: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis(),
    val syncedToCloud: Boolean = false
)
