package org.roruku.smartformatconverter.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.roruku.smartformatconverter.data.preferences.FilenameFormat
import org.roruku.smartformatconverter.data.preferences.ThemeMode
import org.roruku.smartformatconverter.data.preferences.UserPreferencesRepository
import org.roruku.smartformatconverter.data.preferences.UserSettings
import org.roruku.smartformatconverter.data.storage.StorageManager

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserPreferencesRepository(application)

    val userSettings: StateFlow<UserSettings> = repository.userSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings(),
    )

    fun updateDefaultOutputUri(uriStr: String?) {
        viewModelScope.launch { repository.updateDefaultOutputUri(uriStr) }
    }

    fun updateFilenameFormat(format: FilenameFormat) {
        viewModelScope.launch { repository.updateFilenameFormat(format) }
    }

    fun updateCustomPrefix(prefix: String) {
        viewModelScope.launch { repository.updateCustomPrefix(prefix) }
    }

    fun updatePreserveMetadata(preserve: Boolean) {
        viewModelScope.launch { repository.updatePreserveMetadata(preserve) }
    }

    fun updateHardwareAcceleration(enable: Boolean) {
        viewModelScope.launch { repository.updateHardwareAcceleration(enable) }
    }

    fun updateMaxConcurrentJobs(maxJobs: Int) {
        viewModelScope.launch { repository.updateMaxConcurrentJobs(maxJobs) }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch { repository.updateThemeMode(themeMode) }
    }

    fun clearCache() {
        StorageManager.clearCache(getApplication())
    }
}
