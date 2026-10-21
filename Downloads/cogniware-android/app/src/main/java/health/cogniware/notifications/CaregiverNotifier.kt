package health.cogniware.notifications

import android.content.Context
import android.util.Log

class CaregiverNotifier(
    private val context: Context,
    private val patientId: String,
    private val supabaseUrl: String,
    private val supabaseKey: String
) {
    companion object {
        private const val TAG = "CaregiverNotifier"
    }

    fun notifyFall(timestampMs: Long) {
        Log.i(TAG, "Notifying caregivers of fall for patient $patientId at $timestampMs")
    }

    fun notifyElevatedTremor(rmsAmplitude: Double) {
        Log.i(TAG, "Notifying caregivers of elevated tremor (${"%.3f".format(rmsAmplitude)}g) for patient $patientId")
    }
}
