package health.cogniware.wear

import android.content.Intent
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import health.cogniware.data.TremorSession
import health.cogniware.data.TremorSessionDao
import kotlinx.coroutines.*

/**
 * WearDataListenerService (runs on phone)
 *
 * Receives tremor data packets from the watch via the Wearable Data Layer
 * and persists them as TremorSession rows in Room for RiskScoreEngine.
 */
class WearDataListenerService : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == "/tremor") {

                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val session = TremorSession(
                    timestampMs        = map.getLong("timestampMs"),
                    tremorPowerRatio   = map.getDouble("tremorPowerRatio"),
                    dominantFreqHz     = map.getDouble("dominantFreqHz"),
                    restTremorRatio    = map.getDouble("restTremorRatio"),
                    actionTremorRatio  = map.getDouble("actionTremorRatio"),
                    source             = "wear_os"
                )

                scope.launch {
                    // Insert into Room — PassiveSensingService reads latest row
                    val db = health.cogniware.data.local.CogniwareDatabase.getInstance(applicationContext)
                    db.tremorSessionDao().insert(session)
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
