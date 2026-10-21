package health.cogniware.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted risk score computation result.
 * One row per computeAndStoreRiskScore() call (~every 5 min while app is active).
 */
@Entity(
    tableName = "risk_scores",
    indices = [Index("patientId"), Index("timestampMs")]
)
data class RiskScoreRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val patientId: String,
    val timestampMs: Long,

    val score: Int,            // 0–100
    val level: String,         // "LOW" | "ELEVATED" | "HIGH" | "CRITICAL"
    val dominantFactor: String,

    // Component scores serialised as "factor:value,factor:value,..."
    val componentScoresJson: String = ""
)
