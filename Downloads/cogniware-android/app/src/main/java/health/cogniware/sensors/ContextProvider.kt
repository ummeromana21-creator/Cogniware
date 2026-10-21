package health.cogniware.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.google.android.gms.location.*
import health.cogniware.data.models.ActivityState
import health.cogniware.data.models.BaselinePhase
import health.cogniware.data.models.ContextVector
import health.cogniware.data.models.HeartRateZone
import health.cogniware.data.models.DeviceOrientation as ProjectDeviceOrientation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import kotlin.math.*

class ContextProvider private constructor(private val context: Context) {

    companion object {
        @Volatile private var instance: ContextProvider? = null
        fun getInstance(context: Context): ContextProvider {
            return instance ?: synchronized(this) {
                instance ?: ContextProvider(context.applicationContext).also { instance = it }
            }
        }
    }

    private var currentActivityState: ActivityState = ActivityState.UNKNOWN
    private var activityClient: ActivityRecognitionClient? = null
    private val activityTransitions = buildTransitionList()

    private var currentOrientation: ProjectDeviceOrientation = ProjectDeviceOrientation.UNKNOWN
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private var gravX = 0f; private var gravY = 0f; private var gravZ = 0f
    private val GRAVITY_ALPHA = 0.1f

    private var heartRateZone: HeartRateZone = HeartRateZone.UNKNOWN
    private var lastNightSleepMinutes: Int = 0
    private var stepsTodaySoFar: Int = 0
    private var lastInteractionMs: Long = System.currentTimeMillis()
    private var enrollmentDateMs: Long = 0L
    private var baselinePhaseDays: Int = 90

    private val _contextFlow = MutableStateFlow(buildContext())
    val contextFlow = _contextFlow.asStateFlow()

    private val gravityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            gravX = GRAVITY_ALPHA * event.values[0] + (1 - GRAVITY_ALPHA) * gravX
            gravY = GRAVITY_ALPHA * event.values[1] + (1 - GRAVITY_ALPHA) * gravY
            gravZ = GRAVITY_ALPHA * event.values[2] + (1 - GRAVITY_ALPHA) * gravZ
            currentOrientation = classifyOrientation(gravX, gravY, gravZ)
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private fun classifyOrientation(x: Float, y: Float, z: Float): ProjectDeviceOrientation {
        val total = sqrt(x * x + y * y + z * z)
        if (total < 4.0f) return ProjectDeviceOrientation.UNKNOWN

        val absY = abs(y) / total
        val absZ = abs(z) / total

        return when {
            absY > 0.85 -> ProjectDeviceOrientation.UPRIGHT
            absZ > 0.85 && z > 0 -> ProjectDeviceOrientation.FLAT_FACE_UP
            absZ > 0.85 && z < 0 -> ProjectDeviceOrientation.FLAT_FACE_DOWN
            else -> ProjectDeviceOrientation.TILTED
        }
    }

    fun start() {
        gravitySensor?.let {
            sensorManager.registerListener(gravityListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        setupActivityRecognition()
    }

    fun stop() {
        sensorManager.unregisterListener(gravityListener)
        activityClient?.removeActivityTransitionUpdates(getPendingIntent())
    }

    fun snapshot(): ContextVector = buildContext()

    fun recordInteraction() {
        lastInteractionMs = System.currentTimeMillis()
        _contextFlow.value = buildContext()
    }

    fun updateHealthContext(zone: HeartRateZone, sleepMinutes: Int, steps: Int) {
        heartRateZone = zone
        lastNightSleepMinutes = sleepMinutes
        stepsTodaySoFar = steps
        _contextFlow.value = buildContext()
    }

    fun setEnrollmentDate(enrolledAtMs: Long, baselineDays: Int) {
        enrollmentDateMs = enrolledAtMs
        baselinePhaseDays = baselineDays
    }

    private fun buildContext(): ContextVector {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val daysSinceEnroll = if (enrollmentDateMs > 0) {
            ((now - enrollmentDateMs) / (1000L * 60 * 60 * 24)).toInt()
        } else 0
        val phase = when {
            daysSinceEnroll < baselinePhaseDays -> BaselinePhase.BUILDING
            daysSinceEnroll < baselinePhaseDays * 2 -> BaselinePhase.ESTABLISHED
            else -> BaselinePhase.MONITORING
        }
        return ContextVector(
            timestampMs = now,
            activityState = currentActivityState,
            deviceOrientation = currentOrientation,
            hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
            dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
            minutesSinceLastInteraction = (now - lastInteractionMs) / 60_000,
            heartRateZone = heartRateZone,
            lastNightSleepMinutes = lastNightSleepMinutes,
            stepsTodaySoFar = stepsTodaySoFar,
            baselinePhase = phase
        )
    }

    private fun buildTransitionList(): List<ActivityTransition> {
        return emptyList()
    }

    
    private fun getPendingIntent(): android.app.PendingIntent {
        val intent = android.content.Intent(context, ActivityTransitionReceiver::class.java)
        return android.app.PendingIntent.getBroadcast(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setupActivityRecognition() {
        if (activityTransitions.isEmpty()) return
        activityClient = ActivityRecognition.getClient(context)
        val request = ActivityTransitionRequest(activityTransitions)
        activityClient?.requestActivityTransitionUpdates(request, getPendingIntent())
    }

    fun onActivityTransition(event: ActivityTransitionEvent) {
        currentActivityState = when (event.activityType) {
            DetectedActivity.STILL -> ActivityState.STILL
            DetectedActivity.WALKING -> ActivityState.WALKING
            DetectedActivity.RUNNING -> ActivityState.RUNNING
            DetectedActivity.IN_VEHICLE -> ActivityState.IN_VEHICLE
            DetectedActivity.TILTING -> ActivityState.TILTING
            else -> ActivityState.UNKNOWN
        }
        _contextFlow.value = buildContext()
    }

    fun currentContext(): ContextVector = buildContext()

    fun updateHeartRateZone(zone: HeartRateZone) { heartRateZone = zone }
    fun updateSleepMinutes(minutes: Int) { lastNightSleepMinutes = minutes }
    fun updateStepsToday(steps: Int) { stepsTodaySoFar = steps }

    fun updateActivityState(state: ActivityState) {
        currentActivityState = state
    }

    fun setActivityTransitionType(transitionType: Int) {
        currentActivityState = when (transitionType) {
            DetectedActivity.WALKING -> ActivityState.WALKING
            DetectedActivity.RUNNING -> ActivityState.WALKING
            DetectedActivity.IN_VEHICLE -> ActivityState.IN_VEHICLE
            else -> ActivityState.STILL
        }
    }

}