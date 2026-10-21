package health.cogniware.data.local

import androidx.room.*
import health.cogniware.data.models.DiagnosisProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosisProfileDao {

    /** Returns the diagnosis profile for a given patient, or null if none exists. */
    @Query("SELECT * FROM diagnosis_profiles WHERE patientId = :patientId LIMIT 1")
    suspend fun getProfileForPatient(patientId: String): DiagnosisProfile?

    /** Observes all diagnosis profiles as a reactive Flow. */
    @Query("SELECT * FROM diagnosis_profiles ORDER BY createdAtMs DESC")
    fun getAllProfiles(): Flow<List<DiagnosisProfile>>

    /** Inserts or replaces a diagnosis profile. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: DiagnosisProfile)

    /** Updates an existing diagnosis profile. */
    @Update
    suspend fun update(profile: DiagnosisProfile)

    /**
     * Hard-deletes a diagnosis profile by its primary key.
     * No referential constraints depend on this row, so deletion is safe.
     */
    @Query("DELETE FROM diagnosis_profiles WHERE profileId = :profileId")
    suspend fun delete(profileId: String)

    @Query("DELETE FROM patient_profile")
    suspend fun deleteAll()
}
