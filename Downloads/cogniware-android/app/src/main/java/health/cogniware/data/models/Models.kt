package health.cogniware.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────
// CONTEXT VECTOR
// Every sensor reading is tagged with this.
// This is the core of the context-aware architecture —
// we never interpret a biomarker without knowing the
// behavioral state it was measured in.
// ─────────────────────────────────────────────

@Serializable
data class ContextVector(
    val timestampMs: Long,
    val activityState: ActivityState,       // STILL / WALKING / RUNNING / IN_VEHICLE / TILTING / UNKNOWN
    val deviceOrientation: DeviceOrientation, // UPRIGHT / FLAT / TILTED / UNKNOWN
    val hourOfDay: Int,                     // 0–23
    val dayOfWeek: Int,                     // 1=Mon … 7=Sun
    val minutesSinceLastInteraction: Long,  // proxy for fatigue / session continuity
    val heartRateZone: HeartRateZone,       // resting / light / moderate / vigorous (from Health Connect)
    val lastNightSleepMinutes: Int,         // from Health Connect
    val stepsTodaySoFar: Int,               // from Health Connect
    val baselinePhase: BaselinePhase        // BUILDING (0–90 days) / ESTABLISHED / MONITORING
)

enum class ActivityState { STILL, WALKING, RUNNING, IN_VEHICLE, TILTING, UNKNOWN }
enum class DeviceOrientation { UPRIGHT, FLAT_FACE_UP, FLAT_FACE_DOWN, TILTED, UNKNOWN }
enum class HeartRateZone { RESTING, LIGHT, MODERATE, VIGOROUS, UNKNOWN }
enum class BaselinePhase { BUILDING, ESTABLISHED, MONITORING }

// ─────────────────────────────────────────────
// KEYSTROKE FEATURES
// Raw timing metadata from the keyboard service.
// No key content ever stored.
// ─────────────────────────────────────────────

@Entity(tableName = "keystroke_sessions")
@Serializable
data class KeystrokeSession(
    @PrimaryKey val sessionId: String,
    val patientId: String,
    val startMs: Long,
    val endMs: Long,

    // IKI features
    val ikiMeanMs: Double,          // mean inter-keystroke interval
    val ikiCvPercent: Double,       // coefficient of variation — variability index
    val ikiP10Ms: Double,           // 10th percentile (fast bursts)
    val ikiP90Ms: Double,           // 90th percentile (pauses)

    // Flight time (key-down to key-up) — bradykinesia signal
    val flightTimeMeanMs: Double,
    val flightTimeCvPercent: Double,

    // Error / correction signal — anterior cingulate executive function proxy
    val backspaceRatePercent: Double,   // backspaces / total keystrokes
    val correctionBurstCount: Int,      // runs of 3+ consecutive backspaces

    // Pause features — lexical retrieval and motor fluency
    val wordBoundaryPauseMeanMs: Double,    // pause before first letter of new word
    val withinWordPauseMeanMs: Double,      // pause between letters of same word
    val longPauseCount: Int,                // IKI > 800ms — executive function lapse
    val pausePerLetterMs: Double = 0.0,      // mean within-word pause / letter — motor fluency
    val bilateralAsymmetryIndex: Double = 0.0, // L/R hand IKI asymmetry (alternating key pairs)

    // Speed
    val estimatedWpm: Double,

    // Typing volume
    val totalKeystrokes: Int,

    // Context at time of session
    val context: ContextVector,

    val syncedToCloud: Boolean = false
)

// ─────────────────────────────────────────────
// TREMOR FEATURES
// Derived from accelerometer bursts via FFT.
// ─────────────────────────────────────────────

@Entity(tableName = "tremor_readings")
@Serializable
data class TremorReading(
    @PrimaryKey val readingId: String,
    val patientId: String,
    val timestampMs: Long,
    val durationMs: Long,           // length of accelerometer burst analysed

    // Frequency domain
    val peakFrequencyHz: Double,    // dominant tremor frequency
    val powerAtPeakDb: Double,      // amplitude at peak frequency
    val power4to7Hz: Double,        // Parkinsonian band power
    val power4to12Hz: Double,       // Essential tremor band power
    val power2to4Hz: Double,        // Cerebellar / MS band power
    val totalPowerDb: Double,       // baseline noise level

    // Time domain
    val rmsAmplitude: Double,       // root mean square — overall tremor severity
    val laterality: Laterality,     // LEFT_DOMINANT / RIGHT_DOMINANT / BILATERAL / UNKNOWN

    // Classification hint (not diagnosis — for routing to clinician)
    val tremorBandHint: TremorBand,

    val context: ContextVector,
    val syncedToCloud: Boolean = false
)

enum class Laterality { LEFT_DOMINANT, RIGHT_DOMINANT, BILATERAL, UNKNOWN }
enum class TremorBand {
    PARKINSONIAN,       // 4–7 Hz, resting, improves with movement
    ESSENTIAL,          // 4–12 Hz, worsens with action
    CEREBELLAR,         // 2–4 Hz, intention tremor
    PHYSIOLOGICAL,      // <0.5g amplitude, no clinical significance
    UNKNOWN
}

// ─────────────────────────────────────────────
// GAIT FEATURES
// From accelerometer during WALKING activity state.
// ─────────────────────────────────────────────

@Entity(tableName = "gait_sessions")
@Serializable
data class GaitSession(
    @PrimaryKey val sessionId: String,
    val patientId: String,
    val startMs: Long,
    val endMs: Long,
    val stepCount: Int,

    val cadenceStepsPerMin: Double,
    val strideRegularityIndex: Double,  // 0–1, higher = more regular
    val stepAsymmetryIndex: Double,     // 0–1, 0 = perfectly symmetric
    val walkingSpeedProxy: Double,      // step frequency × estimated stride length
    val verticalImpactG: Double,        // peak vertical acceleration per step
    val strideCvPercent: Double = 0.0,  // stride time variability — CV of stride intervals (STV)

    val context: ContextVector,
    val syncedToCloud: Boolean = false
)

// ─────────────────────────────────────────────
// STRUCTURAL BASELINE (one-time, from pose capture)
// Skeletal landmark map — no image stored.
// ─────────────────────────────────────────────

@Entity(tableName = "structural_baseline")
@Serializable
data class StructuralBaseline(
    @PrimaryKey val patientId: String,
    val capturedAtMs: Long,

    // Spinal alignment (degrees from vertical)
    val forwardHeadPostureDeg: Double,
    val shoulderAsymmetryDeg: Double,   // height difference L vs R shoulder
    val spinalLateralCurvatureDeg: Double,

    // Limb symmetry (proportion of body height)
    val leftArmLengthRatio: Double,
    val rightArmLengthRatio: Double,
    val leftLegLengthRatio: Double,
    val rightLegLengthRatio: Double,

    // Hip and knee alignment
    val hipLevelAsymmetryDeg: Double,
    val kneeValgusAngleLeftDeg: Double,
    val kneeValgusAngleRightDeg: Double,

    // Overall posture score (0–100, higher = more symmetric)
    val postureSymmetryScore: Double,

    // Raw landmark confidences — flag low-quality captures
    val captureQualityScore: Double,    // 0–1
    val captureNotes: String = ""       // e.g. "low confidence left shoulder"
)

// ─────────────────────────────────────────────
// HEALTH CONNECT DAILY SUMMARY
// Pulled once per day from wearables via Health Connect.
// ─────────────────────────────────────────────

@Entity(tableName = "health_daily")
@Serializable
data class HealthDailySummary(
    @PrimaryKey val dateKey: String,    // "2026-10-01"
    val patientId: String,

    // Heart
    val restingHeartRateBpm: Double?,
    val heartRateMeanBpm: Double?,
    val heartRateMinBpm: Double?,
    val heartRateMaxBpm: Double?,
    val hrvRmssdMs: Double?,            // HRV — autonomic nervous system proxy

    // Sleep
    val sleepTotalMinutes: Int?,
    val sleepDeepMinutes: Int?,
    val sleepRemMinutes: Int?,
    val sleepLightMinutes: Int?,
    val sleepAwakeMinutes: Int?,
    val sleepOnsetMs: Long?,            // when they fell asleep (circadian marker)
    val wakeTimeMs: Long?,

    // Activity
    val stepCount: Int?,
    val activeCaloriesBurned: Double?,
    val totalCaloriesBurned: Double?,
    val activeMinutes: Int?,
    val exerciseSessionMinutes: Int?,   // structured workout

    // Other
    val spO2MeanPercent: Double?,
    val spO2MinPercent: Double?,

    val syncedToCloud: Boolean = false
)

// ─────────────────────────────────────────────
// PATIENT PROFILE
// Set at enrollment — drives disorder module routing
// and interpretation context.
// ─────────────────────────────────────────────

@Entity(tableName = "patient_profile")
@Serializable
data class PatientProfile(
    @PrimaryKey val patientId: String,
    val enrollmentCode: String,         // clinician-issued
    val enrolledAtMs: Long,

    // Demographics (for norm stratification)
    val yearOfBirth: Int,
    val biologicalSex: String,          // "male" / "female" / "other" / "prefer_not_to_say"
    val dominantHand: String,           // "right" / "left" / "ambidextrous"

    // Diagnosis (clinician-confirmed at enrollment)
    val primaryDiagnosis: String,       // ICD-10 code, e.g. "G20" for Parkinson's
    val secondaryDiagnoses: String,     // JSON array of ICD-10 codes
    val diagnosisStage: String,         // "prodromal" / "early" / "moderate" / "advanced"

    // Medications (affects biomarker interpretation)
    val medications: String,            // JSON array: [{name, dose, frequency, startDate}]

    // Disability / physical context (from questionnaire)
    val mobilityAids: String,           // JSON: "none" / ["cane", "walker", "wheelchair"]
    val dominantHandDisability: Boolean,
    val visionImpairment: Boolean,
    val hearingImpairment: Boolean,

    // Monitoring configuration
    val activeModules: String,          // JSON array: ["tremor", "gait", "typing", "sleep"]
    val baselinePhaseDays: Int = 90,    // days before prediction mode activates
    val clinicianId: String,

    // Clinical classification (added v10)
    val diagnosisYear: Int = 0,             // year of PD diagnosis, e.g. 2019
    val hyStage: String = "",               // Hoehn & Yahr: "1","1.5","2","2.5","3","4","5"
    val primarySymptomType: String = "",    // "tremor_dominant" / "akinetic_rigid" / "mixed"

    // Supabase auth
    val supabaseUserId: String = ""
)

// ─────────────────────────────────────────────
// FEATURE BUNDLE — what gets synced to Supabase
// Hourly aggregation of all features + context
// ─────────────────────────────────────────────

@Serializable
data class FeatureBundle(
    val patientId: String,
    val bundleId: String,
    val periodStartMs: Long,
    val periodEndMs: Long,
    val keystrokeSessions: List<KeystrokeSession> = emptyList(),
    val tremorReadings: List<TremorReading> = emptyList(),
    val gaitSessions: List<GaitSession> = emptyList(),
    val healthSummary: HealthDailySummary? = null,
    val dominantActivityState: ActivityState,
    val bundleQualityScore: Double,     // 0–1, how much data was collected this hour
    val baselinePhase: BaselinePhase
)

// ─────────────────────────────────────────────
// POSTURE BASELINE
// ─────────────────────────────────────────────

@androidx.room.Entity(tableName = "posture_baselines")
data class PostureBaseline(
    @androidx.room.PrimaryKey val baselineId: String,
    val patientId: String,
    val capturedAtMs: Long,
    val sessionType: String,             // "ENROLLMENT" | "REASSESSMENT"
    val armSwingAsymmetryIndex: Float,
    val gaitInitiationDelayMs: Float,
    val trunkForwardLeanDeg: Float,
    val stepSymmetryIndex: Float,
    val cadenceStepsPerMin: Float,
    val landmarkSeriesJson: String = "[]",
    val qualityScore: Float = 0f,
    val flaggedIssues: String = "",
    val syncedToCloud: Boolean = false
)

// ─────────────────────────────────────────────
// ASSESSMENT SESSION
// ─────────────────────────────────────────────

@androidx.room.Entity(tableName = "assessment_sessions")
data class AssessmentSession(
    @androidx.room.PrimaryKey val sessionId: String,
    val patientId: String,
    val scheduledMs: Long,
    val completedMs: Long = 0L,
    val sessionType: String,             // "ENROLLMENT" | "REASSESSMENT"
    val postureBaselineId: String = "",
    val rawVideoStoredRemotely: Boolean = false,
    val armSwingDelta: Float = 0f,
    val trunkLeanDelta: Float = 0f,
    val gaitInitiationDelta: Float = 0f,
    val flaggedIssues: String = "",
    val syncedToCloud: Boolean = false
)
