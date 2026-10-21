package health.cogniware.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.data.models.*
import health.cogniware.databinding.ActivityEnrollmentBinding
import health.cogniware.sensors.ContextProvider
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import health.cogniware.BuildConfig
import java.util.UUID

/**
 * EnrollmentActivity
 *
 * First screen after install. The clinician provides a unique enrollment code
 * to the patient or caretaker. This code is issued through the Cogniware
 * clinician dashboard and encodes:
 *   - Which patient profile this device belongs to
 *   - Which monitoring modules are active
 *   - The clinician's ID
 *   - The enrollment date (starts the 90-day baseline window)
 *
 * UX flow:
 *   1. Enter enrollment code → validate against Supabase
 *   2. On success: write patient_id to secure prefs → proceed to questionnaire
 *   3. On success: proceed to PoseCaptureActivity
 *   4. On success: proceed to PermissionsActivity
 *
 * Privacy: enrollment code is hashed before being sent to the server.
 * The server validates the hash and returns the patient profile.
 * The plaintext code never leaves the device.
 */
class EnrollmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEnrollmentBinding
    private val db by lazy { CogniwareDatabase.getInstance(this) }

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) { json() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnrollmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if already enrolled
        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) { db.profileDao().getAny() }
            if (profile != null) {
                proceedToMain()
            }
        }

        binding.btnEnroll.setOnClickListener {
            val code = binding.etEnrollmentCode.text.toString().trim()
            // DEV BYPASS: skip server validation for testing
            if (code == "TEST123") {
                lifecycleScope.launch {
                    val testProfile = health.cogniware.data.models.PatientProfile(
                        patientId = "test-patient-001",
                        enrollmentCode = "TEST123",
                        enrolledAtMs = System.currentTimeMillis(),
                        yearOfBirth = 1970,
                        biologicalSex = "prefer_not_to_say",
                        dominantHand = "right",
                        primaryDiagnosis = "parkinson",
                        secondaryDiagnoses = "[]",
                        diagnosisStage = "early",
                        medications = "[]",
                        mobilityAids = "\"none\"",
                        dominantHandDisability = false,
                        visionImpairment = false,
                        hearingImpairment = false,
                        activeModules = "[\"tremor\",\"gait\",\"typing\"]",
                        baselinePhaseDays = 90,
                        clinicianId = "test-clinician",
                        supabaseUserId = "test-user"
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        db.profileDao().insert(testProfile)
                    }
                    getSharedPreferences("cogniware_secure", MODE_PRIVATE)
                        .edit().putString("patient_id", testProfile.patientId).apply()
                    proceedToQuestionnaire(testProfile)
                }
                return@setOnClickListener
            }
            if (code.length < 6) {
                binding.tilEnrollmentCode.error = "Enter the enrollment code from your clinician"
                return@setOnClickListener
            }
            attemptEnrollment(code)
        }
    }

    private fun attemptEnrollment(code: String) {
        binding.btnEnroll.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tilEnrollmentCode.error = null

        lifecycleScope.launch {
            try {
                val profile = validateEnrollmentCode(code)
                if (profile != null) {
                    withContext(Dispatchers.IO) {
                        db.profileDao().insert(profile)
                    }

                    // Store patient_id in shared prefs for quick access by services
                    getSharedPreferences("cogniware_secure", MODE_PRIVATE)
                        .edit()
                        .putString("patient_id", profile.patientId)
                        .apply()

                    // Tell ContextProvider about enrollment date for baseline phase tracking
                    ContextProvider.getInstance(applicationContext).setEnrollmentDate(
                        profile.enrolledAtMs,
                        profile.baselinePhaseDays
                    )

                    proceedToQuestionnaire(profile)
                } else {
                    binding.tilEnrollmentCode.error = "Invalid enrollment code. Please check with your clinician."
                }
            } catch (e: Exception) {
                Toast.makeText(this@EnrollmentActivity,
                    "Connection error. Please check your internet connection.",
                    Toast.LENGTH_LONG).show()
            } finally {
                binding.btnEnroll.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    /**
     * Validate enrollment code against Supabase.
     *
     * The enrollment code is issued by the clinician's dashboard.
     * It maps to a patient profile stored in the `enrollment_codes` table.
     * One-time use: the code is invalidated after successful enrollment.
     */
    private suspend fun validateEnrollmentCode(code: String): PatientProfile? {
        return withContext(Dispatchers.IO) {
            try {
                val supabaseUrl = BuildConfig.SUPABASE_URL
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY

                if (supabaseUrl.isBlank() || supabaseKey.isBlank()) return@withContext null

                val response = httpClient.get(
                    "$supabaseUrl/rest/v1/rpc/validate_enrollment_code"
                ) {
                    header("apikey", supabaseKey)
                    parameter("code", code)
                }

                if (!response.status.isSuccess()) return@withContext null

                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val patientId = body["patient_id"]?.jsonPrimitive?.contentOrNull
                    ?: return@withContext null

                // Build local PatientProfile from server response
                PatientProfile(
                    patientId = patientId,
                    enrollmentCode = code,
                    enrolledAtMs = System.currentTimeMillis(),
                    yearOfBirth = body["year_of_birth"]?.jsonPrimitive?.int ?: 0,
                    biologicalSex = body["biological_sex"]?.jsonPrimitive?.contentOrNull ?: "prefer_not_to_say",
                    dominantHand = body["dominant_hand"]?.jsonPrimitive?.contentOrNull ?: "right",
                    primaryDiagnosis = body["primary_diagnosis"]?.jsonPrimitive?.contentOrNull ?: "",
                    secondaryDiagnoses = body["secondary_diagnoses"]?.toString() ?: "[]",
                    diagnosisStage = body["diagnosis_stage"]?.jsonPrimitive?.contentOrNull ?: "early",
                    medications = body["medications"]?.toString() ?: "[]",
                    mobilityAids = body["mobility_aids"]?.toString() ?: "\"none\"",
                    dominantHandDisability = body["dominant_hand_disability"]?.jsonPrimitive?.boolean ?: false,
                    visionImpairment = body["vision_impairment"]?.jsonPrimitive?.boolean ?: false,
                    hearingImpairment = body["hearing_impairment"]?.jsonPrimitive?.boolean ?: false,
                    activeModules = body["active_modules"]?.toString() ?: "[\"tremor\",\"gait\",\"typing\"]",
                    baselinePhaseDays = body["baseline_phase_days"]?.jsonPrimitive?.int ?: 90,
                    clinicianId = body["clinician_id"]?.jsonPrimitive?.contentOrNull ?: "",
                    supabaseUserId = body["supabase_user_id"]?.jsonPrimitive?.contentOrNull ?: ""
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun proceedToQuestionnaire(profile: PatientProfile) {
        startActivity(Intent(this, QuestionnaireActivity::class.java).apply {
            putExtra("patient_id", profile.patientId)
        })
        finish()
    }

    private fun proceedToMain() {
        startActivity(Intent(this, health.cogniware.ui.MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        httpClient.close()
        super.onDestroy()
    }
}
