package org.roruku.smartformatconverter.ui.home

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.repository.ConversionRepository

data class HomeUiState(
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val totalBytesConverted: Long = 0L,
    val recentJobs: List<ConversionJobEntity> = emptyList(),
    val freeStorageBytes: Long = 0L,
    val totalStorageBytes: Long = 0L,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConversionRepository(application)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.allJobsFlow,
        repository.completedCountFlow,
        repository.failedCountFlow,
        repository.totalBytesConvertedFlow
    ) { jobs, completed, failed, totalBytes ->
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeBytes = stat.availableBytes
        val totalBytesStorage = stat.totalBytes

        HomeUiState(
            completedCount = completed,
            failedCount = failed,
            totalBytesConverted = totalBytes,
            recentJobs = jobs.take(10),
            freeStorageBytes = freeBytes,
            totalStorageBytes = totalBytesStorage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )
}
