package health.cogniware.data.sync

import android.content.Context
import android.util.Log
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.data.models.HealthDailySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BloodPressureSync {
    private const val TAG = "BloodPressureSync"

    suspend fun syncToday(context: Context) = withContext(Dispatchers.IO) {
        val db = CogniwareDatabase.getInstance(context)
        val profile = db.profileDao().getAny() ?: return@withContext
        val patientId = profile.patientId
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val existing = db.healthDao().getLatest(patientId)
        if (existing == null) {
            db.healthDao().insert(
                HealthDailySummary(
                    dateKey = dateKey,
                    patientId = patientId,
                    restingHeartRateBpm = null,
                    heartRateMeanBpm = null,
                    heartRateMinBpm = null,
                    heartRateMaxBpm = null,
                    hrvRmssdMs = null,
                    sleepTotalMinutes = null,
                    sleepDeepMinutes = null,
                    sleepRemMinutes = null,
                    sleepLightMinutes = null,
                    sleepAwakeMinutes = null,
                    sleepOnsetMs = null,
                    wakeTimeMs = null,
                    stepCount = null,
                    activeCaloriesBurned = null,
                    totalCaloriesBurned = null,
                    activeMinutes = null,
                    exerciseSessionMinutes = null,
                    spO2MeanPercent = null,
                    spO2MinPercent = null,
                    syncedToCloud = false
                )
            )
            Log.d(TAG, "Created placeholder HealthDailySummary for $dateKey")
        }
    }
}
