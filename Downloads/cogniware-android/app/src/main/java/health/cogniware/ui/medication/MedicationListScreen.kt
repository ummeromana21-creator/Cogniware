package health.cogniware.ui.medication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import health.cogniware.data.models.MedicationSchedule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationListScreen(
    viewModel: MedicationViewModel,
    onNavigateToAdd: () -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val schedules by viewModel.activeSchedules.collectAsState()
    val recentDoses by viewModel.recentDoses.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medications") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add medication"
                )
            }
        }
    ) { innerPadding ->
        if (schedules.isEmpty()) {
            MedicationEmptyState(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(schedules, key = { it.scheduleId }) { schedule ->
                    val lastDose = recentDoses
                        .filter { it.scheduleId == schedule.scheduleId }
                        .maxByOrNull { it.takenAtMs }
                    MedicationCard(
                        schedule = schedule,
                        lastDoseTakenAtMs = lastDose?.takenAtMs,
                        onLogDose = { viewModel.logDose(schedule.scheduleId) }
                    )
                }
            }
        }
    }
}

@Composable
fun MedicationCard(
    schedule: MedicationSchedule,
    lastDoseTakenAtMs: Long?,
    onLogDose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Medication,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = schedule.drugName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${schedule.doseMg.toInt()} mg",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${schedule.frequencyPerDay}x per day",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val parsedTimes = parseDoseTimes(schedule.doseTimesJson)
            if (parsedTimes.isNotEmpty()) {
                Text(
                    text = "Doses: ${parsedTimes.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            lastDoseTakenAtMs?.let { ms ->
                Text(
                    text = "Last taken: ${TIME_FORMAT.format(Date(ms))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } ?: Text(
                text = "Not taken yet today",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onLogDose,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Took it")
            }
        }
    }
}

@Composable
private fun MedicationEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Medication,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No medications added yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to add your first medication",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/** Parses ["08:00","12:00"] JSON without a full JSON library for display purposes. */
private fun parseDoseTimes(json: String): List<String> {
    return try {
        json.trim().removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }
}

@Preview(showBackground = true)
@Composable
private fun MedicationCardPreview() {
    MaterialTheme {
        MedicationCard(
            schedule = MedicationSchedule(
                scheduleId = "preview-1",
                patientId = "patient-1",
                drugName = "Levodopa/Carbidopa",
                doseMg = 100.0,
                frequencyPerDay = 3,
                doseTimesJson = "[\"08:00\",\"12:00\",\"17:00\"]",
                conditionTarget = "parkinson",
                medicationModel = "LEVODOPA_CYCLE",
                expectedOnsetMinutes = 30,
                expectedDurationMinutes = 240
            ),
            lastDoseTakenAtMs = System.currentTimeMillis() - 3_600_000L,
            onLogDose = {}
        )
    }
}
