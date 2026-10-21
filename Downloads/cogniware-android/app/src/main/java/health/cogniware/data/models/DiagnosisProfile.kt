package health.cogniware.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * DiagnosisProfile — Room entity (table: diagnosis_profiles)
 *
 * Captures the clinical profile of a patient's neurological condition.
 * Drives which anomaly models and medication cycle models are active,
 * who receives alerts (patient vs caregiver), and per-condition overrides
 * to RiskScoreEngine weights.
 *
 * conditionType values: "PARKINSONS" | "MS" | "ALS" | "ASD" | "ADHD" | "TOURETTE" | "CP"
 * ageGroup values:      "PAEDIATRIC" | "ADULT" | "ELDERLY"
 * notifyTarget values:  "PATIENT" | "CAREGIVER" | "BOTH"
 * anomalyModel values:  "TREMOR_FFT" | "GAIT_ANALYSIS" | "SPEECH" | "STIMMING" | "SPASTICITY"
 * medicationModel values:"LEVODOPA_CYCLE" | "STIMULANT_CYCLE" | "IMMUNOTHERAPY" | "NONE"
 */
@Entity(tableName = "diagnosis_profiles")
data class DiagnosisProfile(
    @PrimaryKey val profileId: String,          // UUID

    val patientId: String,

    /** e.g. "PARKINSONS", "MS", "ALS", "ASD", "ADHD", "TOURETTE", "CP" */
    val conditionType: String,

    /** "PAEDIATRIC" | "ADULT" | "ELDERLY" */
    val ageGroup: String,

    /** Condition sub-type, e.g. "RELAPSING_REMITTING" for MS, "TREMOR_DOMINANT" for PD */
    val subtype: String? = null,

    /** Optional epoch-ms of formal diagnosis date */
    val diagnosisDateMs: Long?,

    /** Non-null for paediatric patients — alerts are routed to this caregiver */
    val caregiverId: String?,

    /** "PATIENT" | "CAREGIVER" | "BOTH" */
    val notifyTarget: String,

    /** Primary anomaly detection model for this condition */
    val anomalyModel: String,

    /** Medication cycling model for this condition */
    val medicationModel: String,

    /** JSON object overriding RiskScoreEngine weights per condition; "{}" = use defaults */
    val riskWeightsJson: String = "{}",

    val createdAtMs: Long = System.currentTimeMillis(),

    val syncedToCloud: Boolean = false
)
