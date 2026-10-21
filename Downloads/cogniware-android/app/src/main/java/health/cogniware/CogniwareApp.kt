package health.cogniware

import android.app.Application
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.sensors.ContextProvider
import health.cogniware.sync.FeatureBundleSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * CogniwareApp
 *
 * Application class — initialises shared singletons at startup.
 * Lightweight: no heavy I/O here, just wiring.
 */
class CogniwareApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Warm the database singleton (avoids first-access latency in services)
        val db = CogniwareDatabase.getInstance(this)

        // Warm the context provider singleton
        val contextProvider = ContextProvider.getInstance(this)

        // Restore enrollment state if already enrolled
        CoroutineScope(Dispatchers.IO).launch {
            val profile = db.profileDao().getAny()
            if (profile != null) {
                contextProvider.setEnrollmentDate(profile.enrolledAtMs, profile.baselinePhaseDays)
            }
        }
    }
}
