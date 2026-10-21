package health.cogniware.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.databinding.ActivityProfileBinding
import health.cogniware.onboarding.EnrollmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var db: CogniwareDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = CogniwareDatabase.getInstance(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSignOut.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try { db.profileDao().deleteAll() } catch (e: Exception) {}
                withContext(Dispatchers.Main) {
                    startActivity(
                        Intent(this@ProfileActivity, EnrollmentActivity::class.java)
                            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
                    )
                }
            }
        }

        loadProfile()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) { db.profileDao().getAny() }
            profile?.let { p ->
                // Header
                binding.tvDisplayName.text = p.patientId.take(8)
                val enrollDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    .format(Date(p.enrolledAtMs))
                binding.tvEnrolledSince.text = "Enrolled $enrollDate"

                // Demographics
                val ageBracket = when {
                    p.yearOfBirth <= 0 -> "—"
                    else -> {
                        val age = 2026 - p.yearOfBirth
                        when {
                            age < 50  -> "Under 50"
                            age < 60  -> "50–59"
                            age < 70  -> "60–69"
                            age < 80  -> "70–79"
                            else      -> "80+"
                        }
                    }
                }
                binding.tvAgeBracket.text = ageBracket
                binding.tvSex.text = when (p.biologicalSex.lowercase()) {
                    "male"   -> "Male"
                    "female" -> "Female"
                    "other"  -> "Other"
                    else     -> p.biologicalSex.ifBlank { "—" }
                }
                binding.tvDominantHand.text = when (p.dominantHand.lowercase()) {
                    "right"         -> "Right"
                    "left"          -> "Left"
                    "ambidextrous"  -> "Ambidextrous"
                    else            -> p.dominantHand.ifBlank { "—" }
                }

                // Clinical
                binding.tvDiagnosisYear.text = if (p.diagnosisYear > 0) p.diagnosisYear.toString() else "—"
                binding.tvHyStage.text = if (p.hyStage.isNotBlank()) "Stage ${p.hyStage}" else "—"
                binding.tvPrimarySymptom.text = when (p.primarySymptomType.lowercase()) {
                    "tremor_dominant"  -> "Tremor-dominant"
                    "akinetic_rigid"   -> "Akinetic-rigid"
                    "mixed"            -> "Mixed"
                    else               -> p.primarySymptomType.ifBlank { "—" }
                }

                // Caregiver
                binding.tvCaregiverName.text  = "—"  // field not yet in model
                binding.tvCaregiverPhone.text = "—"  // field not yet in model

                // Modules
                binding.tvModules.text = p.activeModules
                    .replace(",", " · ")
                    .ifBlank { "Tremor · Gait · Typing · HRV · Sleep · Steps" }
            }
        }
    }
}
