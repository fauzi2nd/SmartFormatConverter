package org.roruku.smartformatconverter.ui.settings

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class CodecRow(
    val name: String,
    val kind: String,
    val formats: String,
    val videoLimits: String
)

private fun scanDeviceEncoders(): List<CodecRow> =
    MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        .filter { it.isEncoder }
        .map { codec ->
            val kind = if (Build.VERSION.SDK_INT >= 29) {
                when {
                    codec.isHardwareAccelerated -> "Hardware"
                    codec.isSoftwareOnly -> "Software"
                    else -> "Unknown / vendor"
                }
            } else "Unknown (Android < 10)"
            val limits = codec.supportedTypes.mapNotNull { mime ->
                if (!mime.startsWith("video/", ignoreCase = true)) return@mapNotNull null
                runCatching {
                    val v = codec.getCapabilitiesForType(mime).videoCapabilities
                    if (v != null) "$mime: ${v.supportedWidths} × ${v.supportedHeights}, ${v.supportedFrameRates} fps" else null
                }.getOrNull()
            }.joinToString("\n")
            CodecRow(codec.name, kind, codec.supportedTypes.joinToString(", "), limits)
        }.sortedWith(compareBy<CodecRow> { it.kind }.thenBy { it.name })

@Composable
internal fun DeviceCodecCapabilitiesDialog(onDismiss: () -> Unit) {
    val result = remember { runCatching { scanDeviceEncoders() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Device Codec Capabilities") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Android MediaCodecList • Encoder capabilities", style = MaterialTheme.typography.bodySmall)
                result.fold(
                    onSuccess = { codecs ->
                        Text("${codecs.size} encoders detected", style = MaterialTheme.typography.titleSmall)
                        codecs.forEach { codec ->
                            HorizontalDivider()
                            Text(codec.name, style = MaterialTheme.typography.titleSmall)
                            Text(codec.kind, color = MaterialTheme.colorScheme.primary)
                            Text(codec.formats, style = MaterialTheme.typography.bodySmall)
                            if (codec.videoLimits.isNotEmpty()) Text(codec.videoLimits, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Reported ranges are not a guarantee that every resolution, frame rate or output container works.", style = MaterialTheme.typography.bodySmall)
                    },
                    onFailure = { Text("Unable to read codecs: ${it.message ?: "Unknown error"}") }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
