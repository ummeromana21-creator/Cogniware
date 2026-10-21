package health.cogniware.ui.medication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * MedicationActivity
 *
 * Compose-based Activity that hosts the medication management nav graph.
 * Routes:
 *   "medications"     -> MedicationListScreen
 *   "add_medication"  -> AddMedicationScreen
 *
 * Launched from MainActivity via the "Medications" button.
 */
class MedicationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val patientId = applicationContext
            .getSharedPreferences("cogniware_secure", MODE_PRIVATE)
            .getString("patient_id", "") ?: ""

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val medicationViewModel: MedicationViewModel = viewModel(
                        factory = MedicationViewModel.Factory(application)
                    )

                    NavHost(
                        navController = navController,
                        startDestination = "medications"
                    ) {
                        composable("medications") {
                            MedicationListScreen(
                                viewModel = medicationViewModel,
                                onNavigateToAdd = {
                                    navController.navigate("add_medication")
                                },
                                onNavigateBack = {
                                    finish()
                                }
                            )
                        }
                        composable("add_medication") {
                            AddMedicationScreen(
                                viewModel = medicationViewModel,
                                patientId = patientId,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
