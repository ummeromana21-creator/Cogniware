package health.cogniware.onboarding

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import health.cogniware.databinding.ActivityPermissionsBinding
import health.cogniware.sensors.HealthConnectManager
import health.cogniware.sensors.PassiveSensingService
import health.cogniware.sync.FeatureBundleSync
import health.cogniware.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * PermissionsActivity
 *
 * Guides the user through granting all required permissions.
 * Permissions are requested in priority order:
 *   1. Activity Recognition — needed for context tagging
 *   2. Body Sensors — accelerometer for tremor/gait
 *   3. Camera — for pose estimation (one-time, then unused)
 *   4. Health Connect — wearable data integration
 *   5. Notifications — for ongoing monitoring notification
 *   6. POST_NOTIFICATIONS — Android 13+
 *
 * UX principle: explain WHY before asking.
 * Each permission step shows a plain-language explanation.
 *
 * Enforcement: tremor and gait work without camera.
 * Health Connect works without camera and sensors.
 * Core typing analysis works without any of the above.
 * We degrade gracefully — missing permissions reduce fidelity, not crash the app.
 */
class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding
    private var patientId: String = ""

    private val standardPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Some may be denied — we continue anyway with reduced capability
        requestHealthConnectPermissions()
    }

    private val healthConnectPermissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        // Granted set may be partial — acceptable
        onAllPermissionsHandled()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        patientId = intent.getStringExtra("patient_id") ?: ""

        binding.btnGrantPermissions.setOnClickListener {
            requestStandardPermissions()
        }

        binding.btnContinueWithout.setOnClickListener {
            onAllPermissionsHandled()
        }

        setupPermissionExplanations()
    }

    private fun setupPermissionExplanations() {
        binding.tvPermissionSummary.text = """
            Cogniware needs these permissions to monitor your health:

            📱 Activity Recognition
            Detects whether you're walking, still, or in a vehicle — so biomarkers are interpreted in the right context.

            🔄 Sensors
            Reads the accelerometer to detect tremor and analyse walking rhythm.

            📷 Camera (one-time)
            Captures a single posture photo at setup. The image is immediately deleted — only measurements are kept.

            ❤️ Health Connect
            Reads your heart rate, sleep, and step data from your wearable device (Oura, Fitbit, Garmin, etc.)

            All data stays on your device until your clinician's sync window.
        """.trimIndent()
    }

    private fun requestStandardPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.CAMERA
        )

        // Android 13+ notification permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        standardPermissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun requestHealthConnectPermissions() {
        lifecycleScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(this@PermissionsActivity)
                val already = client.permissionController.getGrantedPermissions()
                val needed = HealthConnectManager.REQUIRED_PERMISSIONS - already
                if (needed.isNotEmpty()) {
                    healthConnectPermissionsLauncher.launch(needed)
                } else {
                    onAllPermissionsHandled()
                }
            } catch (e: Exception) {
                // Health Connect not installed or not available
                onAllPermissionsHandled()
            }
        }
    }

    private fun onAllPermissionsHandled() {
        // Start the passive sensing service
        startForegroundService(Intent(this, PassiveSensingService::class.java))

        // Schedule hourly sync
        FeatureBundleSync.schedule(this)

        // Proceed to main app
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
