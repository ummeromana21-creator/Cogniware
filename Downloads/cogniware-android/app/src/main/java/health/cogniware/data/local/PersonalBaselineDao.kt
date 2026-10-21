package health.cogniware.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import health.cogniware.data.models.PersonalBaseline

@Dao
interface PersonalBaselineDao {

    @Query("SELECT * FROM personal_baselines WHERE featureKey = :key LIMIT 1")
    suspend fun getBaseline(key: String): PersonalBaseline?

    @Query("SELECT * FROM personal_baselines")
    suspend fun getAllBaselines(): List<PersonalBaseline>

    /** Upsert — replaces on conflict (featureKey is PK) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(baseline: PersonalBaseline)
}
