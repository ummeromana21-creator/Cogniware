package health.cogniware.sync

import android.content.Context
import androidx.work.*
import health.cogniware.BuildConfig
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.data.models.*
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * FeatureBundleSync
 *
 * WorkManager worker that runs hourly to sync accumulated biomarker features
 * to Supabase. Handles:
 *   - Bundles unsynced keystroke sessions, tremor readings, and gait sessions
 *   - Fetches latest health daily summary
 *   - Posts as a FeatureBundle to Supabase REST API
 *   - Marks individual records as synced after confirmation
 *   - Prunes old synced records to keep local storage bounded
 *
 * Security: credentials ONLY from BuildConfig (injected from local.properties).
 * Never hardcoded. Never logged.
 */
class FeatureBundleSync(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = CogniwareDatabase.getInstance(applicationContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val profile = db.profileDao().getAny() ?: return Result.success()  // not enrolled yet
            val patientId = profile.patientId

            // ── Collect unsynced data ──
            val keystrokeSessions = db.keystrokeDao().getUnsynced()
            val tremorReadings = db.tremorDao().getUnsynced()
            val gaitSessions = db.gaitDao().getUnsynced()
            val healthSummary = db.healthDao().getLatest(patientId)

            val hasData = keystrokeSessions.isNotEmpty() ||
                    tremorReadings.isNotEmpty() ||
                    gaitSessions.isNotEmpty()

            if (!hasData) return Result.success()  // nothing to sync

            // ── Compute dominant activity state ──
            val dominantActivity = computeDominantActivity(keystrokeSessions, gaitSessions)

            // ── Bundle quality score: fraction of expected data present ──
            val qualityScore = computeQualityScore(keystrokeSessions, tremorReadings, gaitSessions)

            val now = System.currentTimeMillis()
            val bundle = FeatureBundle(
                patientId = patientId,
                bundleId = UUID.randomUUID().toString(),
                periodStartMs = now - 3_600_000L,  // last hour
                periodEndMs = now,
                keystrokeSessions = keystrokeSessions,
                tremorReadings = tremorReadings,
                gaitSessions = gaitSessions,
                healthSummary = healthSummary,
                dominantActivityState = dominantActivity,
                bundleQualityScore = qualityScore,
                baselinePhase = profile.computeBaselinePhase()
            )

            // ── POST to Supabase ──
            val supabaseUrl = BuildConfig.SUPABASE_URL
            val supabaseKey = BuildConfig.SUPABASE_ANON_KEY

            if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
                // Credentials not configured — skip sync silently
                return Result.success()
            }

            val response = httpClient.post("$supabaseUrl/rest/v1/feature_bundles") {
                header("apikey", supabaseKey)
                header("Authorization", "Bearer ${getAuthToken()}")
                header("Content-Type", "application/json")
                header("Prefer", "return=minimal")
                setBody(json.encodeToString(bundle))
            }

            if (response.status.isSuccess()) {
                // ── Mark individual records as synced ──
                db.keystrokeDao().markSynced(keystrokeSessions.map { it.sessionId })
                db.tremorDao().markSynced(tremorReadings.map { it.readingId })
                db.gaitDao().markSynced(gaitSessions.map { it.sessionId })

                // ── Prune old synced records (keep local storage bounded) ──
                val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)
                db.keystrokeDao().pruneOld(thirtyDaysAgo)
                db.tremorDao().pruneOld(thirtyDaysAgo)
                db.gaitDao().pruneOld(thirtyDaysAgo)

                Result.success()
            } else {
                Result.retry()  // Server error — retry with backoff
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun computeDominantActivity(
        keystrokes: List<KeystrokeSession>,
        gait: List<GaitSession>
    ): ActivityState {
        val states = (keystrokes.map { it.context.activityState } +
                gait.map { it.context.activityState })
        return states.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: ActivityState.UNKNOWN
    }

    private fun computeQualityScore(
        keystrokes: List<KeystrokeSession>,
        tremor: List<TremorReading>,
        gait: List<GaitSession>
    ): Double {
        // Scoring: presence of at least some data in each modality
        var score = 0.0
        if (keystrokes.isNotEmpty()) score += 0.5         // keyboard is primary signal
        if (tremor.isNotEmpty()) score += 0.3             // tremor readings
        if (gait.isNotEmpty()) score += 0.2               // gait sessions
        return score.coerceIn(0.0, 1.0)
    }

    private fun getAuthToken(): String {
        return applicationContext
            .getSharedPreferences("cogniware_secure", Context.MODE_PRIVATE)
            .getString("supabase_token", "") ?: ""
    }

    private fun PatientProfile.computeBaselinePhase(): BaselinePhase {
        val days = (System.currentTimeMillis() - enrolledAtMs) / (1000L * 60 * 60 * 24)
        return when {
            days < baselinePhaseDays -> BaselinePhase.BUILDING
            days < baselinePhaseDays * 2L -> BaselinePhase.ESTABLISHED
            else -> BaselinePhase.MONITORING
        }
    }

    companion object {
        private const val WORK_NAME = "cogniware_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<FeatureBundleSync>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<FeatureBundleSync>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
