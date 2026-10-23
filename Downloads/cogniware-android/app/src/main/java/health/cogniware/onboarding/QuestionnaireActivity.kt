package health.cogniware.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.databinding.ActivityQuestionnaireBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuestionnaireActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuestionnaireBinding
    private val db by lazy { CogniwareDatabase.getInstance(this) }
    private var patientId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuestionnaireBinding.inflate(layoutInflater)
        setContentView(binding.root)
        patientId = intent.getStringExtra("patient_id") ?: ""
        binding.btnNext.setOnClickListener { saveAndProceed() }
    }

    private fun saveAndProceed() {
        val dominantHand = when (binding.rgDominantHand.checkedRadioButtonId) {
            binding.rbLeft.id         -> "left"
            binding.rbAmbidextrous.id -> "ambidextrous"
            else                      -> "right"
        }
        val mobilityAids = buildList {
            if (binding.cbCane.isChecked)       add("cane")
            if (binding.cbWalker.isChecked)     add("walker")
            if (binding.cbWheelchair.isChecked) add("wheelchair")
        }
        val mobilityAidsJson = if (mobilityAids.isEmpty()) "\"none\""
        else "[${mobilityAids.joinToString(",") { "\"$it\"" }}]"

        val diagnosisYear = binding.etDiagnosisYear.text?.toString()?.trim()?.toIntOrNull() ?: 0

        val hyStage = when (binding.rgHyStage.checkedRadioButtonId) {
            binding.rbHy1.id   -> "1"
            binding.rbHy15.id  -> "1.5"
            binding.rbHy2.id   -> "2"
            binding.rbHy25.id  -> "2.5"
            binding.rbHy3.id   -> "3"
            binding.rbHy4.id   -> "4"
            binding.rbHy5.id   -> "5"
            else               -> ""
        }

        val primarySymptomType = when (binding.rgSymptomType.checkedRadioButtonId) {
            binding.rbTremorDominant.id -> "tremor_dominant"
            binding.rbAkineticRigid.id  -> "akinetic_rigid"
            binding.rbMixed.id          -> "mixed"
            else                        -> ""
        }

        getSharedPreferences("cogniware_secure", MODE_PRIVATE).edit().apply {
            putString("dominant_hand", dominantHand)
            putBoolean("dominant_hand_disability", binding.cbDominantHandDisability.isChecked)
            putBoolean("vision_impairment", binding.cbVisionImpairment.isChecked)
            putBoolean("hearing_impairment", binding.cbHearingImpairment.isChecked)
            apply()
        }

        binding.btnNext.isEnabled = false
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val profile = db.profileDao().get(patientId) ?: db.profileDao().getAny()
                if (profile != null) {
                    db.profileDao().insert(profile.copy(
                        dominantHand           = dominantHand,
                        mobilityAids           = mobilityAidsJson,
                        dominantHandDisability = binding.cbDominantHandDisability.isChecked,
                        visionImpairment       = binding.cbVisionImpairment.isChecked,
                        hearingImpairment      = binding.cbHearingImpairment.isChecked,
                        diagnosisYear          = diagnosisYear,
                        hyStage                = hyStage,
                        primarySymptomType     = primarySymptomType
                    ))
                }
            }
            startActivity(Intent(this@QuestionnaireActivity, PoseCaptureActivity::class.java).apply {
                putExtra("patient_id", patientId)
            })
            finish()
        }
    }
}
