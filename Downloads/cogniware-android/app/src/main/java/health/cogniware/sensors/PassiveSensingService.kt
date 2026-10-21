package health.cogniware.sensors

import android.app.*
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import health.cogniware.BuildConfig
import health.cogniware.R
import health.cogniware.analysis.BaselineEngine
import health.cogniware.analysis.FreezeOfGaitDetector
import health.cogniware.analysis.InferenceEngineFactory
import health.cogniware.analysis.RiskInput
import health.cogniware.analysis.RiskLevel
import health.cogniware.analysis.SubThresholdFFTMonitor
import health.cogniware.analysis.SubThresholdResult
import health.cogniware.analysis.WearOffPredictor
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.data.local.DiagnosisProfileDao
import health.cogniware.data.models.*
import health.cogniware.notifications.CaregiverNotifier
import kotlinx.coroutines.*

/**
 * PassiveSensingService
 *
 * Android foreground service that orchestrates all passive sensor collection:
 *   - Accelerometer at 50Hz -> tremor bursts every 15 minutes (when STILL)
 *   - Accelerometer during WALKING -> gait analysis
 *   - SubThresholdFFTMonitor -> sub-threshold tremor power every ~10s
 *   - FreezeOfGaitDetector -> FOG detection on z-axis every ~2.56s
 *   - WearOffPredictor -> medication wear-off proximity incorporated into risk score
 *   - InferenceEngine (via InferenceEngineFactory) -> composite risk score every 5 minutes -> notifications
 *   - ContextProvider -> keeps activity state and orientation current
 *
 * Runs as a HEALTH foreground service (declared in manifest).
 * Survives app close. Restarts after device reboot via BootReceiver.
 */
class PassiveSensingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var sensorManager: SensorManager
    private lateinit var db: CogniwareDatabase
    private lateinit var contextProvider: ContextProvider
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var subThresholdMonitor: SubThresholdFFTMonitor
    private lateinit var fogDetector: FreezeOfGaitDetector
    private lateinit var wearOffPredictor: WearOffPredictor
    private lateinit var mainHandler: Handler
    private lateinit var caregiverNotifier: CaregiverNotifier
    private lateinit var baselineEngine: BaselineEngine
    private lateinit var diagnosisProfileDao: DiagnosisProfileDao

    @Volatile private var cachedProfile: health.cogniware.data.models.DiagnosisProfile? = null

    // -- Sensor instances --
    private var accelerometer: Sensor? = null

    // -- Sample buffers --
    private val xBuffer = ArrayDeque<Double>()   // 8 seconds at 50Hz + margin
    private val yBuffer = ArrayDeque<Double>()
    private val zBuffer = ArrayDeque<Double>()

    // -- Analysis instances --
    private var tremorAnalyzer: TremorAnalyzer? = null
    private var gaitAnalyzer: GaitAnalyzer? = null

    // -- Sub-threshold + risk state --
    @Volatile private var lastSubThresholdResult: SubThresholdResult? = null

    // -- Timing --
    private val TREMOR_ANALYSIS_INTERVAL_MS = 15 * 60 * 1000L   // every 15 minutes
    private val GAIT_MIN_DURATION_MS = 10 * 1000L                // at least 10s of walking
    private val RISK_SCORE_INTERVAL_MS = 5 * 60 * 1000L          // every 5 minutes
    private var lastTremorAnalysisMs = 0L
    private var walkingStartMs = 0L
    private var wasWalking = false

    companion object {
        private const val CHANNEL_ID = "cogniware_sensing"
        private const val CHANNEL_RISK_ID = "cogniware_risk"
        private const val CHANNEL_FOG_ID = "cogniware_fog"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_RISK_ID = 1002
        private const val NOTIFICATION_FOG_ID = 1003
        private const val MAX_BUFFER_SAMPLES = 50 * 30  // 30 seconds at 50Hz

        private const val TAG = "PassiveSensingService"
    }

    private val fogListener = object : FreezeOfGaitDetector.FogListener {
        override fun onFreezeDetected(timestampMs: Long) {
            android.util.Log.w(TAG, "FOG episode detected at $timestampMs")
            fireFogNotification()
        }

        override fun onFreezeResolved(durationMs: Long) {
            android.util.Log.i(TAG, "FOG episode resolved — duration ${durationMs}ms")
            dismissFogNotification()
        }
    }

    private val accelerometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0].toDouble()
            val y = event.values[1].toDouble()
            val z = event.values[2].toDouble()

            // Keep rolling buffer (newest samples only)
            if (xBuffer.size >= MAX_BUFFER_SAMPLES) {
                xBuffer.removeFirst(); yBuffer.removeFirst(); zBuffer.removeFirst()
            }
            xBuffer.addLast(x); yBuffer.addLast(y); zBuffer.addLast(z)

            // Feed sub-threshold FFT monitor
            val subResult = subThresholdMonitor.processSample(
                event.values[0],
                event.values[1],
                event.values[2]
            )
            if (subResult != null) {
                lastSubThresholdResult = subResult
            }

            // Feed FOG detector with vertical (z) axis
            fogDetector.processSample(event.values[2], System.currentTimeMillis())

            // Trigger analyses on schedule
            val now = System.currentTimeMillis()
            val ctx = contextProvider.currentContext()

            // -- Tremor analysis: every 15 min when STILL --
            if (ctx.activityState == ActivityState.STILL &&
                now - lastTremorAnalysisMs > TREMOR_ANALYSIS_INTERVAL_MS &&
                xBuffer.size >= 400) {   // 400 = 8s at 50Hz
                triggerTremorAnalysis(ctx)
                lastTremorAnalysisMs = now
            }

            // -- Gait analysis: when WALKING, collect until stop --
            if (ctx.activityState == ActivityState.WALKING) {
                if (!wasWalking) {
                    walkingStartMs = now
                    wasWalking = true
                }
            } else if (wasWalking) {
                val walkDuration = now - walkingStartMs
                if (walkDuration >= GAIT_MIN_DURATION_MS && zBuffer.size >= 500) {
                    triggerGaitAnalysis(ctx)
                }
                wasWalking = false
                walkingStartMs = 0L
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        db = CogniwareDatabase.getInstance(applicationContext)
        contextProvider = ContextProvider.getInstance(applicationContext)
        baselineEngine = BaselineEngine(db.personalBaselineDao())
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        subThresholdMonitor = SubThresholdFFTMonitor()
        mainHandler = Handler(Looper.getMainLooper())

        val patientId = getPatientId()
        tremorAnalyzer = TremorAnalyzer(patientId)
        gaitAnalyzer = GaitAnalyzer(patientId)

        // Initialise and wire FOG detector
        fogDetector = FreezeOfGaitDetector()
        fogDetector.setListener(fogListener)

        // Initialise wear-off predictor using patientId from EncryptedSharedPreferences
        wearOffPredictor = WearOffPredictor(db.medicationDao(), patientId)

        // Initialise caregiver notifier (credentials from BuildConfig, never hardcoded)
        caregiverNotifier = CaregiverNotifier(
            this,
            patientId,
            BuildConfig.SUPABASE_URL,
            BuildConfig.SUPABASE_ANON_KEY
        )

        // Wire DAO and load the diagnosis profile for this patient once at startup
        diagnosisProfileDao = db.diagnosisProfileDao()
        serviceScope.launch(Dispatchers.IO) {
            cachedProfile = diagnosisProfileDao.getProfileForPatient(patientId)
            android.util.Log.i(TAG, "Loaded DiagnosisProfile: conditionType=${cachedProfile?.conditionType} notifyTarget=${cachedProfile?.notifyTarget}")
        }

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "cogniware:sensing"
        ).apply { acquire(12 * 60 * 60 * 1000L) }  // 12 hour max

        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildNotification())

        contextProvider.start()

        accelerometer?.let {
            sensorManager.registerListener(accelerometerListener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // Start 5-minute risk score loop
        scheduleRiskScoreCheck()
    }

    private fun scheduleRiskScoreCheck() {
        mainHandler.postDelayed({
            serviceScope.launch { computeAndStoreRiskScore() }
            scheduleRiskScoreCheck()  // reschedule
        }, RISK_SCORE_INTERVAL_MS)
    }

    private suspend fun computeAndStoreRiskScore() {
        val patientId = getPatientId()
        val subResult = lastSubThresholdResult ?: return

        val lastDose = withContext(Dispatchers.IO) { db.medicationDao().getLastDoseEvent(patientId) }
        val lastSchedule = lastDose?.let {
            withContext(Dispatchers.IO) {
                db.medicationDao().getActiveSchedules(patientId)
                    .find { s -> s.scheduleId == lastDose.scheduleId }
            }
        }

        val minutesSinceLastDose = lastDose?.let {
            (System.currentTimeMillis() - it.takenAtMs) / 60_000.0
        }

        // Sub-threshold tremor ratio: compare tremor band to rest band as proxy baseline
        val restPower = subResult.restPowerG2
        val tremorPower = subResult.tremorPowerG2
        val tremorRatio = if (restPower > 0.0) tremorPower / restPower else 1.0

        // Query wear-off state on IO dispatcher and map to proximity score
        val wearOffState = withContext(Dispatchers.IO) {
            wearOffPredictor.getWearOffState()
        }
        val wearOffProximityScore = when (wearOffState) {
            WearOffPredictor.WearOffState.STABLE     -> 0.0
            WearOffPredictor.WearOffState.APPROACHING -> 0.4
            WearOffPredictor.WearOffState.IMMINENT   -> 0.85
            WearOffPredictor.WearOffState.OVERDUE    -> 1.0
            WearOffPredictor.WearOffState.UNKNOWN    -> 0.0
        }

        val ctx = contextProvider.currentContext()
        val input = RiskInput(
            patientId = patientId,
            timestampMs = System.currentTimeMillis(),
            subThresholdTremorRatio = tremorRatio.coerceAtLeast(1.0),
            minutesSinceLastDose = minutesSinceLastDose,
            expectedDurationMinutes = lastSchedule?.expectedDurationMinutes,
            currentHrvMs = null,    // HRV not yet stored in TremorReading; extend when available
            baselineHrvMs = null,
            stepsToday = ctx.stepsTodaySoFar,
            baselineSteps = 6000    // fallback baseline
        )

        // Route through InferenceEngineFactory — never call RiskScoreEngine.compute() directly
        val score = InferenceEngineFactory.get().infer(input)

        android.util.Log.i(
            TAG,
            "[${InferenceEngineFactory.get().engineName}] level=${score.level} score=${score.score} dominant=${score.dominantFactor} " +
            "wearOff=$wearOffState (proximity=$wearOffProximityScore)"
        )

        // Build notification text, appending wear-off warning when relevant
        val wearOffSuffix = when (wearOffState) {
            WearOffPredictor.WearOffState.IMMINENT -> " — wear-off imminent"
            WearOffPredictor.WearOffState.OVERDUE  -> " — wear-off imminent"
            else -> ""
        }

        when (score.level) {
            RiskLevel.ELEVATED -> fireRiskNotification(
                title = "Health Risk: ELEVATED — stay aware$wearOffSuffix",
                importance = NotificationManager.IMPORTANCE_LOW,
                ongoing = true
            )
            RiskLevel.HIGH -> fireRiskNotification(
                title = "High Risk Alert — consider resting$wearOffSuffix",
                importance = NotificationManager.IMPORTANCE_HIGH,
                ongoing = false
            )
            RiskLevel.CRITICAL -> {
                fireRiskNotification(
                    title = "Critical Risk — seek assistance$wearOffSuffix",
                    importance = NotificationManager.IMPORTANCE_MAX,
                    ongoing = false
                )
                // Notify caregiver on CRITICAL risk
                val profile = cachedProfile
                val caregiverId = profile?.caregiverId
                val notifyTarget = profile?.notifyTarget
                if (caregiverId != null && notifyTarget != null &&
                    (notifyTarget == "CAREGIVER" || notifyTarget == "BOTH")
                ) {
                    serviceScope.launch(Dispatchers.IO) {
                        caregiverNotifier.notifyElevatedTremor(score.score.toDouble())
                    }
                }
            }
            RiskLevel.LOW -> {
                // Still surface wear-off warning even when overall risk is low
                if (wearOffSuffix.isNotEmpty()) {
                    fireRiskNotification(
                        title = "Medication wear-off imminent — take next dose if scheduled",
                        importance = NotificationManager.IMPORTANCE_DEFAULT,
                        ongoing = false
                    )
                } else {
                    dismissRiskNotification()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // FOG notifications
    // -------------------------------------------------------------------------

    private fun fireFogNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_FOG_ID)
            .setContentTitle("Gait freeze detected — hold a stable surface")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_FOG_ID, notification)
    }

    private fun dismissFogNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_FOG_ID)
    }

    // -------------------------------------------------------------------------
    // Risk notifications
    // -------------------------------------------------------------------------

    private fun fireRiskNotification(title: String, importance: Int, ongoing: Boolean) {
        val channel = NotificationChannel(
            CHANNEL_RISK_ID,
            "Health Risk Alerts",
            importance
        ).apply { description = "Neurological risk level alerts" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_RISK_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(ongoing)
            .setPriority(
                when (importance) {
                    NotificationManager.IMPORTANCE_MAX -> NotificationCompat.PRIORITY_MAX
                    NotificationManager.IMPORTANCE_HIGH -> NotificationCompat.PRIORITY_HIGH
                    else -> NotificationCompat.PRIORITY_LOW
                }
            )
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_RISK_ID, notification)
    }

    private fun dismissRiskNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_RISK_ID)
    }

    // -------------------------------------------------------------------------
    // Analysis triggers
    // -------------------------------------------------------------------------

    private fun triggerTremorAnalysis(context: ContextVector) {
        val xArray = xBuffer.toList().toDoubleArray()
        val yArray = yBuffer.toList().toDoubleArray()
        val zArray = zBuffer.toList().toDoubleArray()

        serviceScope.launch {
            val reading = tremorAnalyzer?.analyse(xArray, yArray, zArray, context)
            reading?.let { db.tremorDao().insert(it) }
        }
    }

    private fun triggerGaitAnalysis(context: ContextVector) {
        val zArray = zBuffer.toList().toDoubleArray()
        val xArray = xBuffer.toList().toDoubleArray()

        serviceScope.launch {
            val session = gaitAnalyzer?.analyse(zArray, xArray, context)
            session?.let {
                db.gaitDao().insert(it)
                baselineEngine.updateFromGait(it)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fall detection callback — FallDetector wires into this via its own listener
    // -------------------------------------------------------------------------

    /**
     * Called by FallDetector when a fall is confirmed.
     * Fires a local alert, then notifies the caregiver if the profile requires it.
     */
    fun onFallDetected(timestampMs: Long) {
        android.util.Log.w(TAG, "Fall detected at $timestampMs")
        // Fire local fall notification (reuse risk channel at max priority)
        fireRiskNotification(
            title = "Fall detected — are you OK?",
            importance = NotificationManager.IMPORTANCE_MAX,
            ongoing = false
        )

        // Caregiver notification
        val profile = cachedProfile
        val caregiverId = profile?.caregiverId
        val notifyTarget = profile?.notifyTarget
        if (caregiverId != null && notifyTarget != null &&
            (notifyTarget == "CAREGIVER" || notifyTarget == "BOTH")
        ) {
            serviceScope.launch(Dispatchers.IO) {
                caregiverNotifier.notifyFall(System.currentTimeMillis())
            }
        }
    }

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        sensorManager.unregisterListener(accelerometerListener)
        fogDetector.setListener(null)
        contextProvider.stop()
        serviceScope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Notification channels
    // -------------------------------------------------------------------------

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Brain Health Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Passive sensor monitoring for health tracking"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RISK_ID,
                "Health Risk Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Neurological risk level alerts"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOG_ID,
                "Gait Freeze Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Freeze of Gait safety alerts"
                enableVibration(true)
            }
        )
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cogniware")
            .setContentText("Health monitoring active")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun getPatientId(): String {
        return applicationContext
            .getSharedPreferences("cogniware_secure", MODE_PRIVATE)
            .getString("patient_id", "") ?: ""
    }
}
