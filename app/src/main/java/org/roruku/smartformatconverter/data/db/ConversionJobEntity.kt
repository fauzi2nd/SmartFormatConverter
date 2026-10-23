package org.roruku.smartformatconverter.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class MediaType {
    VIDEO,
    IMAGE,
    AUDIO,
    DOWNLOAD
}

enum class JobStatus {
    PENDING,
    PREPARING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED
}

@Entity(tableName = "conversion_jobs")
data class ConversionJobEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val inputUri: String,
    val outputUri: String? = null,
    val mediaType: MediaType,
    val inputFormat: String,
    val outputFormat: String,
    val presetName: String = "Balanced",
    val status: JobStatus = JobStatus.PENDING,
    val progress: Float = 0f,
    val speed: String? = null,
    val eta: String? = null,
    val errorMessage: String? = null,
    val fileSize: Long = 0L,
    val outputFileSize: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val retryCount: Int = 0,
    val relativeFolderPath: String? = null,
    val customParamsJson: String? = null,
)
