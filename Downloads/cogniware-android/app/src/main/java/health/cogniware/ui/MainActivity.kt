package health.cogniware.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import health.cogniware.BuildConfig
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.databinding.ActivityMainBinding
import health.cogniware.debug.DebugDataSeeder
import health.cogniware.onboarding.EnrollmentActivity
import health.cogniware.sensors.PassiveSensingService
import health.cogniware.sync.FeatureBundleSync
import health.cogniware.analysis.RiskLevel
import health.cogniware.ui.RiskHistoryActivity
import health.cogniware.ui.medication.MedicationActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val db by lazy { CogniwareDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkEnrollmentAndRoute()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) { db.profileDao().getAny() }
            if (profile != null) refreshMetrics(profile.patientId)
        }
    }

    private fun checkEnrollmentAndRoute() {
        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) { db.profileDao().getAny() }
            if (profile == null) {
                startActivity(Intent(this@MainActivity, EnrollmentActivity::class.java))
                finish()
                return@launch
            }
            showDashboard(profile)
            startForegroundService(Intent(this@MainActivity, PassiveSensingService::class.java))
            FeatureBundleSync.schedule(this@MainActivity)
        }
    }

    private fun showDashboard(profile: health.cogniware.data.models.PatientProfile) {
        val enrolledDays = ((System.currentTimeMillis() - profile.enrolledAtMs) /
                (1000L * 60 * 60 * 24)).toInt()
        val baselineDays = profile.baselinePhaseDays

        binding.tvStatus.text = "Active"
        binding.tvDiagnosis.text = formatDiagnosis(profile.primaryDiagnosis)

        val progressPct = if (enrolledDays < baselineDays) enrolledDays * 100 / baselineDays else 100
        binding.progressBaseline.progress = progressPct
        binding.tvBaselineProgress.text = when {
            enrolledDays < baselineDays -> "Building baseline — Day $enrolledDays of $baselineDays"
            enrolledDays < baselineDays * 2 -> "Baseline established • Monitoring changes"
            else -> "Monitoring — Day ${enrolledDays - baselineDays} since baseline"
        }

        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        binding.btnMedications.setOnClickListener {
            startActivity(Intent(this, MedicationActivity::class.java))
        }

        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.btnSyncNow.setOnClickListener {
            FeatureBundleSync.runNow(this)
            binding.tvSyncStatus.text = "Sync in progress…"
        }

        binding.btnRiskHistory.setOnClickListener {
            startActivity(Intent(this, RiskHistoryActivity::class.java))
        }

        if (BuildConfig.DEBUG) {
            binding.cardDebug.visibility = View.VISIBLE
            binding.btnSeedData.setOnClickListener {
                lifecycleScope.launch {
                    binding.btnSeedData.isEnabled = false
                    binding.btnSeedData.text = "Seeding…"
                    DebugDataSeeder.seed14Days(this@MainActivity)
                    refreshMetrics(profile.patientId)
                    binding.btnSeedData.text = "Seed 14 Days of Simulated Data"
                    binding.btnSeedData.isEnabled = true
                }
            }
            binding.btnClearData.setOnClickListener {
                lifecycleScope.launch {
                    DebugDataSeeder.clearAll(this@MainActivity)
                    refreshMetrics(profile.patientId)
                    binding.tvSyncStatus.text = "Data cleared"
                }
            }
        }

        lifecycleScope.launch { refreshMetrics(profile.patientId) }
    }

    private suspend fun refreshMetrics(patientId: String) {
        val recentTremors = withContext(Dispatchers.IO) { db.tremorDao().getRecent(patientId) }
        val healthToday = withContext(Dispatchers.IO) { db.healthDao().getLatest(patientId) }
        val unsyncedCount = withContext(Dispatchers.IO) { db.keystrokeDao().count(patientId) }

        val latestRisk = withContext(Dispatchers.IO) { db.riskScoreDao().getLatest(patientId) }

        withContext(Dispatchers.Main) {
            updateTremorCard(recentTremors)
            updateGaitCard(patientId)
            updateTypingCard(patientId)
            updateHealthCard(healthToday)
            updateRiskCard(latestRisk)
            binding.tvReadingsCount.text = "$unsyncedCount keystroke sessions stored"
            binding.tvSyncStatus.text = "Auto-sync enabled · Hourly when online"
        }
    }

    private suspend fun updateTremorCard(tremors: List<health.cogniware.data.models.TremorReading>) {
        if (tremors.isEmpty()) {
            binding.tvTremorScore.text = "—"
            binding.tvTremorFreq.text = "No readings yet"
            return
        }
        val recent8 = tremors.take(8)
        val avgRms = recent8.map { it.rmsAmplitude }.average()
        val avgFreq = recent8.map { it.peakFrequencyHz }.average()
        val bandLabel = when {
            avgFreq in 4.0..7.0 -> "Parkinsonian band"
            avgFreq > 7.0 -> "Essential tremor band"
            else -> "Low freq"
        }
        binding.tvTremorScore.text = "%.3f".format(avgRms)
        binding.tvTremorLabel.text = "g RMS"
        binding.tvTremorFreq.text = "%.1f Hz · %s".format(avgFreq, bandLabel)
        if (avgRms > 0.06) {
            binding.cardAlert.visibility = View.VISIBLE
            binding.tvAlertText.text =
                "Tremor amplitude elevated today (%.3fg avg). Your clinician has been notified.".format(avgRms)
        }
    }

    private suspend fun updateGaitCard(patientId: String) {
        val recentGait = withContext(Dispatchers.IO) { db.gaitDao().getRecent(patientId) }
        if (recentGait.isEmpty()) {
            binding.tvGaitCadence.text = "—"
            binding.tvGaitAsym.text = "No walk detected today"
            return
        }
        val avgCadence = recentGait.map { it.cadenceStepsPerMin }.average()
        val avgAsym = recentGait.map { it.stepAsymmetryIndex }.average()
        binding.tvGaitCadence.text = avgCadence.roundToInt().toString()
        binding.tvGaitAsym.text = "${(avgAsym * 100).roundToInt()}% asymmetry · ${recentGait.size} walks"
    }

    private suspend fun updateTypingCard(patientId: String) {
        val recentKeys = withContext(Dispatchers.IO) { db.keystrokeDao().getRecent(patientId) }
        if (recentKeys.isEmpty()) {
            binding.tvTypingWpm.text = "—"
            binding.tvTypingIki.text = "No typing data yet"
            return
        }
        val avgWpm = recentKeys.map { it.estimatedWpm }.average()
        val avgIki = recentKeys.map { it.ikiMeanMs }.average()
        binding.tvTypingWpm.text = avgWpm.roundToInt().toString()
        binding.tvTypingIki.text = "${avgIki.roundToInt()}ms avg IKI"
    }

    private fun updateHealthCard(health: health.cogniware.data.models.HealthDailySummary?) {
        if (health == null) {
            binding.tvSleepHours.text = "—"
            binding.tvSleepDeep.text = "No Health Connect data"
            binding.tvHrv.text = "—"
            binding.tvHr.text = "Connect wearable"
            binding.tvSteps.text = "—"
            binding.tvPosture.text = ""
            return
        }
        health.sleepTotalMinutes?.let { mins ->
            val hrs = mins / 60
            val tenths = (mins % 60) / 6
            binding.tvSleepHours.text = if (tenths > 0) "$hrs.$tenths" else "$hrs"
            binding.tvSleepDeep.text = "${health.sleepDeepMinutes ?: 0}m deep · ${health.sleepRemMinutes ?: 0}m REM"
        } ?: run {
            binding.tvSleepHours.text = "—"
            binding.tvSleepDeep.text = "No sleep data"
        }
        health.hrvRmssdMs?.let { hrv ->
            binding.tvHrv.text = hrv.roundToInt().toString()
            binding.tvHr.text = "${(health.restingHeartRateBpm ?: 0.0).roundToInt()} bpm resting"
        } ?: run {
            binding.tvHrv.text = "—"
            binding.tvHr.text = "No HR data"
        }
        health.stepCount?.let { steps ->
            binding.tvSteps.text = when {
                steps >= 10000 -> "${steps / 1000}k"
                steps >= 1000 -> "${steps / 1000}.${(steps % 1000) / 100}k"
                else -> steps.toString()
            }
            binding.tvPosture.text = "Posture score: —"
        } ?: run { binding.tvSteps.text = "—" }
    }

    private fun updateRiskCard(risk: health.cogniware.data.models.RiskScoreRecord?) {
        if (risk == null) {
            binding.tvRiskScore.text = "—"
            binding.tvRiskLevel.text = "No data"
            binding.tvRiskDominant.text = "Passive sensing active"
            binding.tvRiskUpdated.text = ""
            return
        }
        binding.tvRiskScore.text = risk.score.toString()
        binding.tvRiskLevel.text = risk.level.replaceFirstChar { it.uppercase() }
            .replace("_", " ")
        binding.tvRiskDominant.text = "↑ " + risk.dominantFactor
            .replace("_", " ").replaceFirstChar { it.uppercase() }
        val agoMin = ((System.currentTimeMillis() - risk.timestampMs) / 60_000).toInt()
        binding.tvRiskUpdated.text = when {
            agoMin < 1 -> "Updated just now"
            agoMin < 60 -> "Updated ${agoMin}m ago"
            else -> "Updated ${agoMin / 60}h ago"
        }
        // Color the circle by level
        val bgColor = when (risk.level) {
            "LOW"      -> getColor(R.color.colorRiskLow)
            "ELEVATED" -> getColor(R.color.colorRiskElevated)
            "HIGH"     -> getColor(R.color.colorRiskHigh)
            "CRITICAL" -> getColor(R.color.colorRiskCritical)
            else       -> getColor(R.color.colorRiskLow)
        }
        binding.cardRisk.setCardBackgroundColor(getColor(
            when (risk.level) {
                "CRITICAL" -> android.R.color.white
                else -> android.R.color.white
            }
        ))
        (binding.tvRiskScore.parent as? android.view.View)?.background?.setTint(bgColor)
        binding.tvRiskLevel.setTextColor(bgColor)
    }

    private fun formatDiagnosis(code: String): String = when (code.lowercase()) {
        "g20", "parkinson", "parkinson's", "parkinson's disease" -> "Parkinson's Disease"
        "g25.0", "essential tremor" -> "Essential Tremor"
        "g35", "ms", "multiple sclerosis" -> "Multiple Sclerosis"
        "g30", "alzheimer", "alzheimer's" -> "Alzheimer's Disease"
        "f31", "bipolar" -> "Bipolar Disorder"
        "f20", "schizophrenia" -> "Schizophrenia"
        "f90", "adhd" -> "ADHD"
        "f84.0", "asd", "autism" -> "Autism Spectrum"
        "als" -> "ALS"
        else -> code.replaceFirstChar { it.uppercase() }
    }
}
