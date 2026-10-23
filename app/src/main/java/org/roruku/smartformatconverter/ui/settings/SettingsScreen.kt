package org.roruku.smartformatconverter.ui.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import org.roruku.smartformatconverter.R
import org.roruku.smartformatconverter.data.preferences.FilenameFormat
import org.roruku.smartformatconverter.data.preferences.UserSettings
import org.roruku.smartformatconverter.data.storage.OutputPathPolicy
import org.roruku.smartformatconverter.data.storage.StorageManager
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme

@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    viewModel: SettingsViewModel? = if (LocalInspectionMode.current) null else viewModel(),
) {
    val parentFolderSelectedMsg = stringResource(R.string.msg_parent_folder_selected)
    val cacheClearedMsg = stringResource(R.string.msg_cache_cleared)

    if (viewModel == null) {
        SettingsScreenContent(
            settings = UserSettings(),
            onUpdateDefaultOutputUri = {},
            onSelectOutputFolder = {},
            onUpdateCustomPrefix = {},
            onUpdateFilenameFormat = {},
            onUpdatePreserveMetadata = {},
            onUpdateHardwareAcceleration = {},
            onClearCache = {},
            onShowSnackbar = {},
            cacheClearedMsg = cacheClearedMsg,
        )
    } else {
        val settings by viewModel.userSettings.collectAsState()
        val context = LocalContext.current
        // Keep SAF activity launchers out of the stateless UI: Compose Preview
        // can now render Settings without an ActivityResultRegistryOwner.
        val folderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            if (uri != null) {
                StorageManager.persistUriPermission(context, uri)
                viewModel.updateDefaultOutputUri(uri.toString())
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(parentFolderSelectedMsg)
                }
            }
        }

        SettingsScreenContent(
            settings = settings,
            onUpdateDefaultOutputUri = { viewModel.updateDefaultOutputUri(it) },
            onSelectOutputFolder = { folderPickerLauncher.launch(null) },
            onUpdateCustomPrefix = { viewModel.updateCustomPrefix(it) },
            onUpdateFilenameFormat = { viewModel.updateFilenameFormat(it) },
            onUpdatePreserveMetadata = { viewModel.updatePreserveMetadata(it) },
            onUpdateHardwareAcceleration = { viewModel.updateHardwareAcceleration(it) },
            onClearCache = { viewModel.clearCache() },
            onShowSnackbar = { msg ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(msg)
                }
            },
            cacheClearedMsg = cacheClearedMsg,
        )
    }
}

@Composable
fun SettingsScreenContent(
    settings: UserSettings,
    onUpdateDefaultOutputUri: (String) -> Unit,
    onSelectOutputFolder: () -> Unit,
    onUpdateCustomPrefix: (String) -> Unit,
    onUpdateFilenameFormat: (FilenameFormat) -> Unit,
    onUpdatePreserveMetadata: (Boolean) -> Unit,
    onUpdateHardwareAcceleration: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    cacheClearedMsg: String = "",
    modifier: Modifier = Modifier,
) {
    val selectedFolderText = stringResource(R.string.settings_selected_folder)
    val notSelectedText = stringResource(R.string.settings_not_selected)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Storage & Folder Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.storage_output),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.parent_folder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val parentDisplayName = settings.defaultOutputUri?.let { uriString ->
                    runCatching {
                        DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
                            .substringAfter(':')
                    }.getOrNull()?.ifBlank { selectedFolderText }
                } ?: notSelectedText
                Text(
                    text = parentDisplayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Output: $parentDisplayName/${OutputPathPolicy.OUTPUT_FOLDER}/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Batch folders are mirrored inside Converter. Files added with +Files go directly into Converter. Existing Converter/Converted folders are excluded from batch scans.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = onSelectOutputFolder) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.select_parent_folder), maxLines = 1)
                }
            }
        }

        // Filename & Naming Template
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.file_naming),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.keep_original_filename), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.keep_original_filename_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.filenameFormat != FilenameFormat.CUSTOM_PREFIX,
                        onCheckedChange = { original ->
                            onUpdateFilenameFormat(
                                if (original) FilenameFormat.ORIGINAL else FilenameFormat.CUSTOM_PREFIX
                            )
                        }
                    )
                }
                if (settings.filenameFormat == FilenameFormat.CUSTOM_PREFIX) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.customPrefix,
                        onValueChange = onUpdateCustomPrefix,
                        label = { Text(stringResource(R.string.optional_filename_prefix)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Performance & Hardware
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.engine_performance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.preserve_exif_metadata),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.preserve_exif_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.preserveMetadata,
                        onCheckedChange = onUpdatePreserveMetadata
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.hardware_acceleration),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.hardware_acceleration_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.hardwareAcceleration,
                        onCheckedChange = onUpdateHardwareAcceleration
                    )
                }
            }
        }

        // Maintenance & Storage
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.cache_diagnostics),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onClearCache()
                        onShowSnackbar(cacheClearedMsg)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.clear_temporary_cache))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SmartFormatConverterTheme {
        SettingsScreenContent(
            settings = UserSettings(
                defaultOutputUri = "content://com.android.externalstorage.documents/tree/primary%3ATorrents",
                customPrefix = "",
                preserveMetadata = true,
                hardwareAcceleration = true
            ),
            onUpdateDefaultOutputUri = {},
            onSelectOutputFolder = {},
            onUpdateCustomPrefix = {},
            onUpdateFilenameFormat = {},
            onUpdatePreserveMetadata = {},
            onUpdateHardwareAcceleration = {},
            onClearCache = {},
            onShowSnackbar = {}
        )
    }
}
