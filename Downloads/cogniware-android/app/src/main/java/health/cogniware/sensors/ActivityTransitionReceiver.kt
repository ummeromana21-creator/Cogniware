package health.cogniware.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import health.cogniware.data.models.ActivityState

/**
 * Receives activity transition events from Google Activity Recognition API.
 * Maps detected activities to Cogniware's ActivityState enum and injects
 * into ContextProvider.
 */
class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val mostRecent = result.transitionEvents.lastOrNull() ?: return

        val state = when (mostRecent.activityType) {
            DetectedActivity.STILL       -> ActivityState.STILL
            DetectedActivity.WALKING     -> ActivityState.WALKING
            DetectedActivity.RUNNING     -> ActivityState.RUNNING
            DetectedActivity.IN_VEHICLE  -> ActivityState.IN_VEHICLE
            DetectedActivity.ON_BICYCLE  -> ActivityState.WALKING  // treat cycling as walking for gait purposes
            DetectedActivity.TILTING     -> ActivityState.TILTING
            else                         -> ActivityState.UNKNOWN
        }

        ContextProvider.getInstance(context).updateActivityState(state)
    }
}
