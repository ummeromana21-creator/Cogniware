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
                    val intent = Intent(this@ProfileActivity, EnrollmentActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
            }
        }

        loadProfile()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) {
                db.profileDao().getAny()
            }
            profile?.let { p ->
                binding.tvPatientId.text = p.patientId
                val enrollDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    .format(Date(p.enrolledAtMs))
                binding.tvEnrolledSince.text = "Enrolled $enrollDate"
                binding.tvDiagnosisDetail.text = p.primaryDiagnosis ?: "—"
                binding.tvStage.text = p.diagnosisStage ?: "—"
                binding.tvClinician.text = p.clinicianId ?: "—"
                binding.tvModules.text = p.activeModules?.replace(",", " · ")?.ifBlank { "Tremor · Gait · Typing · HRV · Sleep · Steps" } ?: "Tremor · Gait · Typing · HRV · Sleep · Steps"
            }
        }
    }
}
