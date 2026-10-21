package health.cogniware.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import health.cogniware.data.models.*
import health.cogniware.data.TremorSession
import health.cogniware.data.TremorSessionDao
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Database(
    entities = [
        KeystrokeSession::class,
        TremorReading::class,
        GaitSession::class,
        StructuralBaseline::class,
        HealthDailySummary::class,
        PatientProfile::class,
        MedicationSchedule::class,
        DoseEvent::class,
        DiagnosisProfile::class,
        PersonalBaseline::class,
        PostureBaseline::class,
        AssessmentSession::class,
        TremorSession::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CogniwareDatabase : RoomDatabase() {

    abstract fun keystrokeDao(): KeystrokeDao
    abstract fun tremorDao(): TremorDao
    abstract fun gaitDao(): GaitDao
    abstract fun healthDao(): HealthDao
    abstract fun profileDao(): ProfileDao
    abstract fun medicationDao(): MedicationDao
    abstract fun diagnosisProfileDao(): DiagnosisProfileDao
    abstract fun personalBaselineDao(): PersonalBaselineDao
    abstract fun postureBaselineDao(): PostureBaselineDao
    abstract fun assessmentSessionDao(): AssessmentSessionDao
    abstract fun tremorSessionDao(): TremorSessionDao

    companion object {
        @Volatile private var instance: CogniwareDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Extend patient_profile
                database.execSQL("ALTER TABLE patient_profile ADD COLUMN heightCm REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE patient_profile ADD COLUMN weightKg REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE patient_profile ADD COLUMN caregiverName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE patient_profile ADD COLUMN caregiverPhone TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE patient_profile ADD COLUMN caregiverRelationship TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE patient_profile ADD COLUMN alertPreference TEXT NOT NULL DEFAULT 'BOTH'")
                database.execSQL("ALTER TABLE patient_profile ADD COLUMN phoneCarryPosition TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE patient_profile ADD COLUMN pilotConsentGiven INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE patient_profile ADD COLUMN enrollmentStepsComplete INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE patient_profile ADD COLUMN nextAssessmentMs INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE patient_profile ADD COLUMN missedAssessmentCount INTEGER NOT NULL DEFAULT 0")
                // New tables
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS posture_baselines (
                        baselineId TEXT NOT NULL PRIMARY KEY,
                        patientId TEXT NOT NULL,
                        capturedAtMs INTEGER NOT NULL,
                        sessionType TEXT NOT NULL,
                        armSwingAsymmetryIndex REAL NOT NULL,
                        gaitInitiationDelayMs REAL NOT NULL,
                        trunkForwardLeanDeg REAL NOT NULL,
                        stepSymmetryIndex REAL NOT NULL,
                        cadenceStepsPerMin REAL NOT NULL,
                        landmarkSeriesJson TEXT NOT NULL DEFAULT '',
                        qualityScore REAL NOT NULL DEFAULT 0,
                        flaggedIssues TEXT NOT NULL DEFAULT '[]',
                        syncedToCloud INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS assessment_sessions (
                        sessionId TEXT NOT NULL PRIMARY KEY,
                        patientId TEXT NOT NULL,
                        scheduledMs INTEGER NOT NULL,
                        completedMs INTEGER NOT NULL DEFAULT 0,
                        sessionType TEXT NOT NULL,
                        postureBaselineId TEXT,
                        rawVideoStoredRemotely INTEGER NOT NULL DEFAULT 0,
                        armSwingDelta REAL,
                        trunkLeanDelta REAL,
                        gaitInitiationDelta REAL,
                        flaggedIssues TEXT NOT NULL DEFAULT '[]',
                        syncedToCloud INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

    val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medication_schedules (
                        scheduleId TEXT NOT NULL PRIMARY KEY,
                        patientId TEXT NOT NULL,
                        drugName TEXT NOT NULL,
                        doseMg REAL NOT NULL,
                        frequencyPerDay INTEGER NOT NULL,
                        doseTimesJson TEXT NOT NULL,
                        conditionTarget TEXT NOT NULL,
                        medicationModel TEXT NOT NULL,
                        expectedOnsetMinutes INTEGER NOT NULL,
                        expectedDurationMinutes INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        active INTEGER NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        syncedToCloud INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dose_events (
                        eventId TEXT NOT NULL PRIMARY KEY,
                        patientId TEXT NOT NULL,
                        scheduleId TEXT NOT NULL,
                        drugName TEXT NOT NULL,
                        doseMg REAL NOT NULL,
                        takenAtMs INTEGER NOT NULL,
                        loggedAtMs INTEGER NOT NULL,
                        logMethod TEXT NOT NULL,
                        hrvAtDoseMs REAL,
                        hrAtDoseBpm REAL,
                        predictedWearOffMs INTEGER,
                        actualWearOffMs INTEGER,
                        syncedToCloud INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS diagnosis_profiles (
                        profileId TEXT NOT NULL PRIMARY KEY,
                        patientId TEXT NOT NULL,
                        conditionType TEXT NOT NULL,
                        ageGroup TEXT NOT NULL,
                        subtype TEXT NOT NULL,
                        diagnosisDateMs INTEGER,
                        caregiverId TEXT,
                        notifyTarget TEXT NOT NULL,
                        anomalyModel TEXT NOT NULL,
                        medicationModel TEXT NOT NULL,
                        riskWeightsJson TEXT NOT NULL DEFAULT '{}',
                        createdAtMs INTEGER NOT NULL,
                        syncedToCloud INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Recreate diagnosis_profiles with subtype nullable
                database.execSQL("ALTER TABLE diagnosis_profiles RENAME TO diagnosis_profiles_old")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS diagnosis_profiles (
                        profileId TEXT NOT NULL PRIMARY KEY,
                        patientId TEXT NOT NULL,
                        conditionType TEXT NOT NULL,
                        ageGroup TEXT NOT NULL,
                        subtype TEXT,
                        diagnosisDateMs INTEGER,
                        caregiverId TEXT,
                        notifyTarget TEXT NOT NULL,
                        anomalyModel TEXT NOT NULL,
                        medicationModel TEXT NOT NULL,
                        riskWeightsJson TEXT NOT NULL DEFAULT '{}',
                        createdAtMs INTEGER NOT NULL,
                        syncedToCloud INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "INSERT INTO diagnosis_profiles SELECT * FROM diagnosis_profiles_old"
                )
                database.execSQL("DROP TABLE diagnosis_profiles_old")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE gait_sessions ADD COLUMN strideCvPercent REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE keystroke_sessions ADD COLUMN pausePerLetterMs REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE keystroke_sessions ADD COLUMN bilateralAsymmetryIndex REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS personal_baselines (
                        featureKey TEXT NOT NULL PRIMARY KEY,
                        n INTEGER NOT NULL DEFAULT 0,
                        mean REAL NOT NULL DEFAULT 0.0,
                        m2 REAL NOT NULL DEFAULT 0.0,
                        lastUpdatedMs INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

                val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS tremor_sessions (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        timestampMs INTEGER NOT NULL,
                        tremorPowerRatio REAL NOT NULL,
                        dominantFreqHz REAL NOT NULL,
                        restTremorRatio REAL NOT NULL,
                        actionTremorRatio REAL NOT NULL,
                        source TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): CogniwareDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CogniwareDatabase::class.java,
                    "cogniware.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromContextVector(cv: ContextVector): String = json.encodeToString(cv)

    @TypeConverter
    fun toContextVector(s: String): ContextVector = json.decodeFromString(s)
}

@Dao
interface KeystrokeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: KeystrokeSession)

    @Query("SELECT * FROM keystroke_sessions WHERE syncedToCloud = 0 ORDER BY startMs ASC LIMIT 200")
    suspend fun getUnsynced(): List<KeystrokeSession>

    @Query("UPDATE keystroke_sessions SET syncedToCloud = 1 WHERE sessionId IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM keystroke_sessions WHERE syncedToCloud = 1 AND startMs < :cutoffMs")
    suspend fun pruneOld(cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM keystroke_sessions WHERE patientId = :patientId")
    suspend fun count(patientId: String): Int

    @Query("SELECT * FROM keystroke_sessions WHERE patientId = :patientId ORDER BY startMs DESC LIMIT 8")
    suspend fun getRecent(patientId: String): List<KeystrokeSession>
}

@Dao
interface TremorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: TremorReading)

    @Query("SELECT * FROM tremor_readings WHERE syncedToCloud = 0 ORDER BY timestampMs ASC LIMIT 500")
    suspend fun getUnsynced(): List<TremorReading>

    @Query("UPDATE tremor_readings SET syncedToCloud = 1 WHERE readingId IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM tremor_readings WHERE syncedToCloud = 1 AND timestampMs < :cutoffMs")
    suspend fun pruneOld(cutoffMs: Long)

    @Query("SELECT * FROM tremor_readings WHERE patientId = :patientId ORDER BY timestampMs DESC LIMIT 50")
    suspend fun getRecent(patientId: String): List<TremorReading>
}

@Dao
interface GaitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: GaitSession)

    @Query("SELECT * FROM gait_sessions WHERE syncedToCloud = 0 ORDER BY startMs ASC LIMIT 200")
    suspend fun getUnsynced(): List<GaitSession>

    @Query("UPDATE gait_sessions SET syncedToCloud = 1 WHERE sessionId IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM gait_sessions WHERE syncedToCloud = 1 AND startMs < :cutoffMs")
    suspend fun pruneOld(cutoffMs: Long)

    @Query("SELECT * FROM gait_sessions WHERE patientId = :patientId ORDER BY startMs DESC LIMIT 10")
    suspend fun getRecent(patientId: String): List<GaitSession>
}

@Dao
interface HealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: HealthDailySummary)

    @Query("SELECT * FROM health_daily WHERE syncedToCloud = 0 ORDER BY dateKey ASC")
    suspend fun getUnsynced(): List<HealthDailySummary>

    @Query("UPDATE health_daily SET syncedToCloud = 1 WHERE dateKey IN (:dateKeys)")
    suspend fun markSynced(dateKeys: List<String>)

    @Query("SELECT * FROM health_daily WHERE patientId = :patientId ORDER BY dateKey DESC LIMIT 1")
    suspend fun getLatest(patientId: String): HealthDailySummary?
}

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: PatientProfile)

    @Query("SELECT * FROM patient_profile WHERE patientId = :patientId LIMIT 1")
    suspend fun get(patientId: String): PatientProfile?

    @Query("SELECT * FROM patient_profile LIMIT 1")
    suspend fun getAny(): PatientProfile?

    @Query("DELETE FROM patient_profile")
    suspend fun deleteAll()
}

@Dao
interface PostureBaselineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(baseline: PostureBaseline)

    @Query("SELECT * FROM posture_baselines WHERE patientId = :patientId ORDER BY capturedAtMs DESC")
    suspend fun getAllForPatient(patientId: String): List<PostureBaseline>

    @Query("SELECT * FROM posture_baselines WHERE patientId = :patientId ORDER BY capturedAtMs ASC LIMIT 1")
    suspend fun getEnrollmentBaseline(patientId: String): PostureBaseline?

    @Query("SELECT * FROM posture_baselines WHERE patientId = :patientId ORDER BY capturedAtMs DESC LIMIT 1")
    suspend fun getLatest(patientId: String): PostureBaseline?
}

@Dao
interface AssessmentSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: AssessmentSession)

    @Query("SELECT * FROM assessment_sessions WHERE patientId = :patientId ORDER BY scheduledMs DESC")
    suspend fun getAllForPatient(patientId: String): List<AssessmentSession>

    @Query("SELECT * FROM assessment_sessions WHERE patientId = :patientId AND completedMs = 0 ORDER BY scheduledMs ASC LIMIT 1")
    suspend fun getNextPending(patientId: String): AssessmentSession?

    @Query("UPDATE assessment_sessions SET completedMs = :completedMs, postureBaselineId = :baselineId WHERE sessionId = :sessionId")
    suspend fun markComplete(sessionId: String, completedMs: Long, baselineId: String)
}
