package health.cogniware.ui.medication

import android.app.Application
import androidx.lifecycle.*
import health.cogniware.data.local.CogniwareDatabase
import health.cogniware.data.models.DoseEvent
import health.cogniware.data.models.MedicationSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MedicationViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CogniwareDatabase.getInstance(application)
    private val dao = db.medicationDao()

    private val patientId: String = application
        .getSharedPreferences("cogniware_secure", android.content.Context.MODE_PRIVATE)
        .getString("patient_id", "") ?: ""

    private val _activeSchedules = MutableStateFlow<List<MedicationSchedule>>(emptyList())
    val activeSchedules: StateFlow<List<MedicationSchedule>> = _activeSchedules.asStateFlow()

    private val _recentDoses = MutableStateFlow<List<DoseEvent>>(emptyList())
    val recentDoses: StateFlow<List<DoseEvent>> = _recentDoses.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _activeSchedules.value = dao.getActiveSchedules(patientId)
            val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            _recentDoses.value = dao.getRecentDoseEvents(patientId, sevenDaysAgo)
        }
    }

    fun addSchedule(schedule: MedicationSchedule) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertSchedule(schedule)
            refresh()
        }
    }

    fun logDose(scheduleId: String, method: String = "MANUAL") {
        viewModelScope.launch(Dispatchers.IO) {
            val schedule = _activeSchedules.value.find { it.scheduleId == scheduleId } ?: return@launch
            val now = System.currentTimeMillis()
            val event = DoseEvent(
                eventId = UUID.randomUUID().toString(),
                patientId = patientId,
                scheduleId = scheduleId,
                drugName = schedule.drugName,
                doseMg = schedule.doseMg,
                takenAtMs = now,
                loggedAtMs = now,
                logMethod = method,
                hrvAtDoseMs = null,
                hrAtDoseBpm = null,
                predictedWearOffMs = now + schedule.expectedDurationMinutes * 60_000L,
                actualWearOffMs = null,
                syncedToCloud = false
            )
            dao.insertDoseEvent(event)
            refresh()
        }
    }

    fun updateSchedule(schedule: MedicationSchedule) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertSchedule(schedule)
            refresh()
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteSchedule(scheduleId)
            refresh()
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MedicationViewModel::class.java)) {
                return MedicationViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
