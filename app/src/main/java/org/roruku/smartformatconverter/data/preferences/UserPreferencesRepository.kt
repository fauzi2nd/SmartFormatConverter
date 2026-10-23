package org.roruku.smartformatconverter.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

enum class FilenameFormat {
    ORIGINAL,
    ORIGINAL_SUFFIX,
    FOLDER_NUMBER,
    CUSTOM_PREFIX
}

data class UserSettings(
    val defaultOutputUri: String? = null,
    val filenameFormat: FilenameFormat = FilenameFormat.ORIGINAL,
    val customPrefix: String = "",
    val preserveMetadata: Boolean = true,
    val hardwareAcceleration: Boolean = true,
    val maxConcurrentJobs: Int = 2,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val autoClearCompleted: Boolean = false,
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val DEFAULT_OUTPUT_URI = stringPreferencesKey("default_output_uri")
        val FILENAME_FORMAT = stringPreferencesKey("filename_format")
        val CUSTOM_PREFIX = stringPreferencesKey("custom_prefix")
        val PRESERVE_METADATA = booleanPreferencesKey("preserve_metadata")
        val HARDWARE_ACCELERATION = booleanPreferencesKey("hardware_acceleration")
        val MAX_CONCURRENT_JOBS = intPreferencesKey("max_concurrent_jobs")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_CLEAR_COMPLETED = booleanPreferencesKey("auto_clear_completed")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val outputUri = preferences[PreferencesKeys.DEFAULT_OUTPUT_URI]
        val filenameFormatStr = preferences[PreferencesKeys.FILENAME_FORMAT] ?: FilenameFormat.ORIGINAL.name
        val customPrefix = preferences[PreferencesKeys.CUSTOM_PREFIX] ?: ""
        val preserveMetadata = preferences[PreferencesKeys.PRESERVE_METADATA] ?: true
        val hardwareAccel = preferences[PreferencesKeys.HARDWARE_ACCELERATION] ?: true
        val maxJobs = preferences[PreferencesKeys.MAX_CONCURRENT_JOBS] ?: 2
        val themeModeStr = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name
        val autoClear = preferences[PreferencesKeys.AUTO_CLEAR_COMPLETED] ?: false

        UserSettings(
            defaultOutputUri = outputUri,
            filenameFormat = when (filenameFormatStr) {
                // Older builds stored ORIGINAL_SUFFIX as the default, even though
                // that naming preference was not applied to output filenames.
                FilenameFormat.ORIGINAL_SUFFIX.name -> FilenameFormat.ORIGINAL
                else -> try {
                    FilenameFormat.valueOf(filenameFormatStr)
                } catch (_: Exception) {
                    FilenameFormat.ORIGINAL
                }
            },
            customPrefix = customPrefix,
            preserveMetadata = preserveMetadata,
            hardwareAcceleration = hardwareAccel,
            maxConcurrentJobs = maxJobs,
            themeMode = try { ThemeMode.valueOf(themeModeStr) } catch (_: Exception) { ThemeMode.DARK },
            autoClearCompleted = autoClear,
        )
    }

    suspend fun updateDefaultOutputUri(uriString: String?) {
        context.dataStore.edit { preferences ->
            if (uriString != null) {
                preferences[PreferencesKeys.DEFAULT_OUTPUT_URI] = uriString
            } else {
                preferences.remove(PreferencesKeys.DEFAULT_OUTPUT_URI)
            }
        }
    }

    suspend fun updateFilenameFormat(format: FilenameFormat) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FILENAME_FORMAT] = format.name
        }
    }

    suspend fun updateCustomPrefix(prefix: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_PREFIX] = prefix
        }
    }

    suspend fun updatePreserveMetadata(preserve: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRESERVE_METADATA] = preserve
        }
    }

    suspend fun updateHardwareAcceleration(enable: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HARDWARE_ACCELERATION] = enable
        }
    }

    suspend fun updateMaxConcurrentJobs(maxJobs: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_CONCURRENT_JOBS] = maxJobs
        }
    }

    suspend fun updateThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun updateAutoClearCompleted(autoClear: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CLEAR_COMPLETED] = autoClear
        }
    }
}
