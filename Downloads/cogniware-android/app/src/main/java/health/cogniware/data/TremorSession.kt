package health.cogniware.data

import androidx.room.*

/**
 * TremorSession
 *
 * One 2-second analysis window from the watch accelerometer.
 * Stored in Room; PassiveSensingService queries the latest row
 * to feed tremorPowerRatio into RiskScoreEngine.
 */
@Entity(tableName = "tremor_sessions")
data class TremorSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    // Fraction of FFT power in 4–12 Hz band (0.0–1.0)
    val tremorPowerRatio: Double,
    // Peak frequency within tremor band (Hz); 0 if no tremor detected
    val dominantFreqHz: Double,
    // 4–6 Hz component (rest tremor, Parkinson's signature)
    val restTremorRatio: Double,
    // 6–12 Hz component (action tremor, ET/MS signature)
    val actionTremorRatio: Double,
    // "wear_os" | "phone_accel" (phone fallback when no watch paired)
    val source: String
)

@Dao
interface TremorSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: TremorSession)

    @Query("SELECT * FROM tremor_sessions ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getLatest(): TremorSession?

    // Last 30 minutes of windows for trending
    @Query("SELECT * FROM tremor_sessions WHERE timestampMs > :sinceMs ORDER BY timestampMs ASC")
    suspend fun getSince(sinceMs: Long): List<TremorSession>

    @Query("DELETE FROM tremor_sessions WHERE timestampMs < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)
}
