package health.cogniware.ui.medication

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import health.cogniware.data.models.MedicationSchedule
import java.util.UUID

private val MEDICATION_MODELS = listOf("NONE", "LEVODOPA_CYCLE", "STIMULANT_CYCLE")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationScreen(
    viewModel: MedicationViewModel,
    patientId: String,
    onNavigateBack: () -> Unit
) {
    var drugName by remember { mutableStateOf("") }
    var doseMgText by remember { mutableStateOf("") }
    var frequencyPerDay by remember { mutableIntStateOf(1) }
    var doseTimes by remember { mutableStateOf(List(1) { "08:00" }) }
    var selectedModel by remember { mutableStateOf("NONE") }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var expectedOnsetText by remember { mutableStateOf("30") }
    var expectedDurationText by remember { mutableStateOf("240") }
    var notes by remember { mutableStateOf("") }

    // Keep doseTimes list in sync with frequency slider
    LaunchedEffect(frequencyPerDay) {
        doseTimes = List(frequencyPerDay) { index ->
            doseTimes.getOrElse(index) { "%02d:00".format(8 + index * 4) }
        }
    }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isSaveEnabled = drugName.isNotBlank() && doseMgText.toDoubleOrNull() != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Medication") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Drug name
            OutlinedTextField(
                value = drugName,
                onValueChange = { drugName = it },
                label = { Text("Drug name") },
                placeholder = { Text("e.g. Levodopa/Carbidopa") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Dose mg
            OutlinedTextField(
                value = doseMgText,
                onValueChange = { doseMgText = it },
                label = { Text("Dose (mg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Frequency slider
            Column {
                Text(
                    text = "Frequency per day: $frequencyPerDay",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = frequencyPerDay.toFloat(),
                    onValueChange = { frequencyPerDay = it.toInt() },
                    valueRange = 1f..6f,
                    steps = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text("6", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            // Dynamic dose time pickers
            Text(
                text = "Dose times",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            doseTimes.forEachIndexed { index, timeStr ->
                DoseTimePicker(
                    label = "Dose ${index + 1}",
                    time = timeStr,
                    onTimeSelected = { newTime ->
                        doseTimes = doseTimes.toMutableList().also { it[index] = newTime }
                    },
                    context = context
                )
            }

            // Medication model dropdown
            Column {
                Text(
                    text = "Medication model",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false }
                    ) {
                        MEDICATION_MODELS.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    selectedModel = model
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Expected onset
            OutlinedTextField(
                value = expectedOnsetText,
                onValueChange = { expectedOnsetText = it },
                label = { Text("Expected onset (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Expected duration
            OutlinedTextField(
                value = expectedDurationText,
                onValueChange = { expectedDurationText = it },
                label = { Text("Expected duration (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                placeholder = { Text("Optional notes about this medication") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                maxLines = 4
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val schedule = MedicationSchedule(
                            scheduleId = UUID.randomUUID().toString(),
                            patientId = patientId,
                            drugName = drugName.trim(),
                            doseMg = doseMgText.toDoubleOrNull() ?: 0.0,
                            frequencyPerDay = frequencyPerDay,
                            doseTimesJson = doseTimes.joinToString(
                                separator = "\",\"",
                                prefix = "[\"",
                                postfix = "\"]"
                            ),
                            conditionTarget = "",
                            medicationModel = selectedModel,
                            expectedOnsetMinutes = expectedOnsetText.toIntOrNull() ?: 30,
                            expectedDurationMinutes = expectedDurationText.toIntOrNull() ?: 240,
                            notes = notes.trim()
                        )
                        viewModel.addSchedule(schedule)
                        onNavigateBack()
                    },
                    enabled = isSaveEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DoseTimePicker(
    label: String,
    time: String,
    onTimeSelected: (String) -> Unit,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

    OutlinedTextField(
        value = time,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = "Pick time",
                modifier = Modifier.clickable {
                    TimePickerDialog(
                        context,
                        { _, h, m -> onTimeSelected("%02d:%02d".format(h, m)) },
                        hour,
                        minute,
                        false
                    ).show()
                }
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                TimePickerDialog(
                    context,
                    { _, h, m -> onTimeSelected("%02d:%02d".format(h, m)) },
                    hour,
                    minute,
                    false
                ).show()
            }
    )
}

@Preview(showBackground = true)
@Composable
private fun AddMedicationScreenPreview() {
    MaterialTheme {
        Text("AddMedicationScreen — preview requires ViewModel")
    }
}
