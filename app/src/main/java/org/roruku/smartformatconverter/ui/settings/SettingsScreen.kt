package org.roruku.smartformatconverter.ui.settings

import android.net.Uri
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
import org.roruku.smartformatconverter.data.preferences.UserSettings
import org.roruku.smartformatconverter.data.storage.StorageManager
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme

@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    viewModel: SettingsViewModel? = if (LocalInspectionMode.current) null else viewModel(),
) {
    if (viewModel == null) {
        SettingsScreenContent(
            settings = UserSettings(),
            onUpdateDefaultOutputUri = {},
            onUpdateCustomPrefix = {},
            onUpdatePreserveMetadata = {},
            onUpdateHardwareAcceleration = {},
            onClearCache = {},
            onShowSnackbar = {},
        )
    } else {
        val settings by viewModel.userSettings.collectAsState()

        SettingsScreenContent(
            settings = settings,
            onUpdateDefaultOutputUri = { viewModel.updateDefaultOutputUri(it) },
            onUpdateCustomPrefix = { viewModel.updateCustomPrefix(it) },
            onUpdatePreserveMetadata = { viewModel.updatePreserveMetadata(it) },
            onUpdateHardwareAcceleration = { viewModel.updateHardwareAcceleration(it) },
            onClearCache = { viewModel.clearCache() },
            onShowSnackbar = { msg ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(msg)
                }
            },
        )
    }
}

@Composable
fun SettingsScreenContent(
    settings: UserSettings,
    onUpdateDefaultOutputUri: (String) -> Unit,
    onUpdateCustomPrefix: (String) -> Unit,
    onUpdatePreserveMetadata: (Boolean) -> Unit,
    onUpdateHardwareAcceleration: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            StorageManager.persistUriPermission(context, uri)
            onUpdateDefaultOutputUri(uri.toString())
            onShowSnackbar("Output location updated!")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
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
                    text = "Storage & Location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Default Output Directory:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = settings.defaultOutputUri ?: "Default App Internal Storage",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { folderPickerLauncher.launch(null) }) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Select Output Folder")
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
                    text = "File Naming Template",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.customPrefix,
                    onValueChange = onUpdateCustomPrefix,
                    label = { Text("Filename Prefix / Template") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Performance & Hardware
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Engine & Performance",
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
                            text = "Preserve EXIF & Metadata",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Keep creation date, camera model & rotation tags",
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
                            text = "Hardware Acceleration",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Use MediaCodec hardware encoding when available",
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
                    text = "Cache & Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onClearCache()
                        onShowSnackbar("Temporary cache cleared!")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Temporary Cache")
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
                defaultOutputUri = "primary:Download/ConvertedMedia",
                customPrefix = "Converted_",
                preserveMetadata = true,
                hardwareAcceleration = true
            ),
            onUpdateDefaultOutputUri = {},
            onUpdateCustomPrefix = {},
            onUpdatePreserveMetadata = {},
            onUpdateHardwareAcceleration = {},
            onClearCache = {},
            onShowSnackbar = {}
        )
    }
}
