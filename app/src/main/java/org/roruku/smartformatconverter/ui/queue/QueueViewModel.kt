package org.roruku.smartformatconverter.ui.queue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.JobStatus
import org.roruku.smartformatconverter.data.repository.ConversionRepository

enum class QueueFilterTab {
    ALL,
    ACTIVE,
    COMPLETED,
    FAILED
}

data class QueueUiState(
    val selectedFilter: QueueFilterTab = QueueFilterTab.ALL,
    val jobs: List<ConversionJobEntity> = emptyList(),
)

class QueueViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConversionRepository(application)
    private val selectedFilterFlow = MutableStateFlow(QueueFilterTab.ALL)

    val uiState: StateFlow<QueueUiState> = combine(
        repository.allJobsFlow,
        selectedFilterFlow,
    ) { allJobs, filter ->
        val filtered = when (filter) {
            QueueFilterTab.ALL -> allJobs
            QueueFilterTab.ACTIVE -> allJobs.filter {
                (it.status == JobStatus.PENDING) || (it.status == JobStatus.PREPARING) || (it.status == JobStatus.RUNNING)
            }
            QueueFilterTab.COMPLETED -> allJobs.filter { it.status == JobStatus.COMPLETED }
            QueueFilterTab.FAILED -> allJobs.filter {
                (it.status == JobStatus.FAILED) || (it.status == JobStatus.CANCELLED) || (it.status == JobStatus.INTERRUPTED)
            }
        }
        QueueUiState(selectedFilter = filter, jobs = filtered)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = QueueUiState()
    )

    fun setFilter(filter: QueueFilterTab) {
        selectedFilterFlow.value = filter
    }

    fun cancelJob(jobId: String) {
        viewModelScope.launch { repository.cancelJob(jobId) }
    }

    fun retryJob(jobId: String) {
        viewModelScope.launch { repository.retryJob(jobId) }
    }

    fun deleteJob(job: ConversionJobEntity) {
        viewModelScope.launch { repository.deleteJob(job) }
    }

    fun clearCompleted() {
        viewModelScope.launch { repository.clearCompletedJobs() }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAllJobs() }
    }
}
