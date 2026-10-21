package health.cogniware.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import health.cogniware.data.models.RiskScoreRecord

@Dao
interface RiskScoreDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RiskScoreRecord)

    /** Latest single score for patient — used by dashboard card */
    @Query("SELECT * FROM risk_scores WHERE patientId = :patientId ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getLatest(patientId: String): RiskScoreRecord?

    /** Last 7 days of scores for history chart (max 2016 rows at 5-min intervals) */
    @Query("""
        SELECT * FROM risk_scores
        WHERE patientId = :patientId AND timestampMs > :sinceMs
        ORDER BY timestampMs DESC
        LIMIT 500
    """)
    suspend fun getRecent(patientId: String, sinceMs: Long): List<RiskScoreRecord>

    /** Count for sync / debug */
    @Query("SELECT COUNT(*) FROM risk_scores WHERE patientId = :patientId")
    suspend fun count(patientId: String): Int
}
