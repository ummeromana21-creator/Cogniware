package health.cogniware.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import health.cogniware.databinding.ActivityQuestionnaireBinding

/**
 * QuestionnaireActivity
 *
 * Collects disability and physical context information that affects
 * biomarker interpretation. This is NOT a diagnostic questionnaire —
 * the diagnosis comes from the clinician at enrollment.
 *
 * What we collect here:
 *   - Dominant hand (affects which hand's tremor/typing is the primary signal)
 *   - Mobility aids in use (walker → gait analysis must account for different rhythm)
 *   - Dominant hand disability (affects typing baseline)
 *   - Vision impairment (affects interaction patterns)
 *   - Hearing impairment (context for some cognitive tasks)
 *
 * These are stable confounders — they don't change day to day and must
 * be absorbed into the baseline, not treated as signals.
 *
 * Clinical note: A patient with Dupuytren's contracture has a naturally
 * slower flight time on the ring finger. Their typing "bradykinesia" is
 * structural, not neurological. The questionnaire captures this.
 *
 * Layout: single scrollable screen with radio groups and checkboxes.
 * All responses pre-populated with safe defaults.
 */
class QuestionnaireActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuestionnaireBinding
    private var patientId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuestionnaireBinding.inflate(layoutInflater)
        setContentView(binding.root)

        patientId = intent.getStringExtra("patient_id") ?: ""

        binding.btnNext.setOnClickListener {
            saveAndProceed()
        }
    }

    private fun saveAndProceed() {
        // Read UI state (radio groups, checkboxes)
        val dominantHand = when (binding.rgDominantHand.checkedRadioButtonId) {
            binding.rbLeft.id -> "left"
            binding.rbAmbidextrous.id -> "ambidextrous"
            else -> "right"
        }

        val mobilityAids = buildList {
            if (binding.cbCane.isChecked) add("cane")
            if (binding.cbWalker.isChecked) add("walker")
            if (binding.cbWheelchair.isChecked) add("wheelchair")
        }

        val mobilityAidsJson = if (mobilityAids.isEmpty()) "\"none\""
        else "[${mobilityAids.joinToString(",") { "\"$it\"" }}]"

        // Store to shared prefs for ContextProvider (quick access before DB is queried)
        getSharedPreferences("cogniware_secure", MODE_PRIVATE).edit().apply {
            putString("dominant_hand", dominantHand)
            putBoolean("dominant_hand_disability", binding.cbDominantHandDisability.isChecked)
            putBoolean("vision_impairment", binding.cbVisionImpairment.isChecked)
            putBoolean("hearing_impairment", binding.cbHearingImpairment.isChecked)
            apply()
        }

        // Proceed to pose capture
        startActivity(Intent(this, PoseCaptureActivity::class.java).apply {
            putExtra("patient_id", patientId)
        })
        finish()
    }
}
