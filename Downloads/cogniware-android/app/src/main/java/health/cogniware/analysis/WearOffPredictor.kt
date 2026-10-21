package health.cogniware.analysis

import health.cogniware.data.local.MedicationDao
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * WearOffPredictor
 *
 * Predicts when the next medication wear-off window will occur for a patient.
 *
 * Two-tier prediction:
 *   1. Schedule-based: takenAtMs + expectedDurationMinutes * 60_000
 *   2. Personal pharmacokinetic (PK) profile: if ≥1 actual wear-off observations
 *      are available for this patient, uses the mean of the last 5 actual durations
 *      in place of expectedDurationMinutes.
 *
 * PersonalPkProfile is NOT a Room entity — it is serialised as JSON and intended
 * for storage in PatientProfile.notesJson or a similarly flexible text column.
 * Use [getPersonalProfile] / [updatePersonalProfile] to manage it.
 *
 * @param medicationDao  Room DAO for medication data.
 * @param patientId      The patient whose wear-off is being predicted.
 */
class WearOffPredictor(
    private val medicationDao: MedicationDao,
    private val patientId: String
) {

    // Internal JSON store for the personal PK profile.
    // In production this should be persisted via PatientProfile.notesJson.
    // Here we hold it in memory so the predictor is self-contained; callers
    // should save/restore via [profileJson] property.
    private var cachedProfile: PersonalPkProfile? = null

    private val json = Json { ignoreUnknownKeys = true }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    enum class WearOffState {
        STABLE,      // > 90 min until predicted wear-off
        APPROACHING, // 30–90 min until wear-off
        IMMINENT,    // 0–30 min until wear-off
        OVERDUE,     // past predicted wear-off time
        UNKNOWN      // no dose or schedule data available
    }

    @Serializable
    data class PersonalPkProfile(
        val meanActualDurationMs: Long,
        val sampleCount: Int,
        val lastUpdatedMs: Long
    )

    /**
     * Returns the current wear-off state for the patient.
     * Must be called from a coroutine (suspend).
     */
    suspend fun getWearOffState(): WearOffState {
        val lastDose = medicationDao.getLastDoseEvent(patientId) ?: return WearOffState.UNKNOWN

        // Determine effective duration in ms: prefer personal profile if available
        val effectiveDurationMs = resolveEffectiveDurationMs(lastDose.scheduleId) ?: return WearOffState.UNKNOWN

        val predictedWearOffMs = lastDose.takenAtMs + effectiveDurationMs
        val minutesUntilWearOff = (predictedWearOffMs - System.currentTimeMillis()) / 60_000.0

        return when {
            minutesUntilWearOff < 0     -> WearOffState.OVERDUE
            minutesUntilWearOff <= 30   -> WearOffState.IMMINENT
            minutesUntilWearOff <= 90   -> WearOffState.APPROACHING
            else                        -> WearOffState.STABLE
        }
    }

    /**
     * Records the actual wear-off time for a specific dose event and updates
     * the personal PK profile.
     *
     * @param doseEventId    The eventId of the DoseEvent that wore off.
     * @param actualWearOffMs Epoch-ms timestamp when the wear-off was observed.
     */
    suspend fun updatePersonalProfile(doseEventId: String, actualWearOffMs: Long) {
        // Retrieve the last 5 dose events to build/update the profile
        val sinceMs = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000) // 90 days window
        val recentDoses = medicationDao.getRecentDoseEvents(patientId, sinceMs)
            .take(5)

        // Build list of known actual durations including the one we're recording
        val knownDurations = recentDoses
            .mapNotNull { dose ->
                val wearOff = if (dose.eventId == doseEventId) actualWearOffMs else dose.actualWearOffMs
                wearOff?.let { it - dose.takenAtMs }
            }
            .filter { it > 0 }

        if (knownDurations.isEmpty()) return

        val meanDurationMs = knownDurations.average().toLong()
        cachedProfile = PersonalPkProfile(
            meanActualDurationMs = meanDurationMs,
            sampleCount = knownDurations.size,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    /**
     * Returns the patient's personal PK profile if enough data has been accumulated,
     * or null if the profile has never been computed.
     */
    suspend fun getPersonalProfile(): PersonalPkProfile? = cachedProfile

    // Serialise/deserialise helpers for callers that persist the profile externally.
    fun encodeProfileToJson(profile: PersonalPkProfile): String =
        json.encodeToString(profile)

    fun decodeProfileFromJson(raw: String): PersonalPkProfile? =
        runCatching { json.decodeFromString<PersonalPkProfile>(raw) }.getOrNull()

    fun restoreProfileFromJson(raw: String) {
        cachedProfile = decodeProfileFromJson(raw)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private suspend fun resolveEffectiveDurationMs(scheduleId: String): Long? {
        // Personal profile takes precedence when available
        cachedProfile?.let { return it.meanActualDurationMs }

        // Fall back to the schedule's expected duration
        val patientSchedules = medicationDao.getActiveSchedules(patientId)
        val schedule = patientSchedules.find { it.scheduleId == scheduleId } ?: return null
        return schedule.expectedDurationMinutes * 60_000L
    }
}
