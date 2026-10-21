package health.cogniware.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.sensors.PassiveSensingService
import kotlinx.coroutines.*

/**
 * BootReceiver — restart sensing service after device reboot.
 *
 * Chronic disease monitoring must survive reboots. A patient's tremor
 * data collection shouldn't stop because they updated their phone.
 *
 * Only starts the service if the patient has completed enrollment
 * (patient_id exists in the database).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Check enrollment asynchronously (Room query can't run on main thread)
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val db = CogniwareDatabase.getInstance(context)
                val profile = db.profileDao().getAny()

                if (profile != null) {
                    // Patient is enrolled — start sensing
                    val serviceIntent = Intent(context, PassiveSensingService::class.java)
                    context.startForegroundService(serviceIntent)

                    // Reschedule hourly sync
                    FeatureBundleSync.schedule(context)
                }
            } catch (e: Exception) {
                // Fail silently — boot receiver shouldn't crash the system
            } finally {
                scope.cancel()
            }
        }
    }
}
