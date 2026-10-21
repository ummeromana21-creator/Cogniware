package health.cogniware.data.local

import androidx.room.*
import health.cogniware.data.models.DoseEvent
import health.cogniware.data.models.MedicationSchedule

@Dao
interface MedicationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: MedicationSchedule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseEvent(event: DoseEvent)

    @Query("SELECT * FROM medication_schedules WHERE patientId = :patientId AND active = 1")
    suspend fun getActiveSchedules(patientId: String): List<MedicationSchedule>

    @Query(
        "SELECT * FROM dose_events WHERE patientId = :patientId AND takenAtMs >= :sinceMs " +
        "ORDER BY takenAtMs DESC"
    )
    suspend fun getRecentDoseEvents(patientId: String, sinceMs: Long): List<DoseEvent>

    @Query(
        "SELECT * FROM dose_events WHERE patientId = :patientId " +
        "ORDER BY takenAtMs DESC LIMIT 1"
    )
    suspend fun getLastDoseEvent(patientId: String): DoseEvent?

    @Query(
        "SELECT * FROM dose_events WHERE patientId = :patientId AND drugName = :drugName " +
        "ORDER BY takenAtMs DESC LIMIT 1"
    )
    suspend fun getLastDoseForDrug(patientId: String, drugName: String): DoseEvent?

    @Query("SELECT * FROM dose_events WHERE syncedToCloud = 0")
    suspend fun getPendingSync(): List<DoseEvent>

    @Query("UPDATE dose_events SET syncedToCloud = 1 WHERE eventId = :eventId")
    suspend fun markSynced(eventId: String)

    @Query("UPDATE medication_schedules SET active = 0 WHERE scheduleId = :scheduleId")
    suspend fun deleteSchedule(scheduleId: String)
}
