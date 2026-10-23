package org.roruku.smartformatconverter.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromMediaType(mediaType: MediaType): String {
        return mediaType.name
    }

    @TypeConverter
    fun toMediaType(value: String): MediaType {
        return try {
            MediaType.valueOf(value)
        } catch (_: Exception) {
            MediaType.VIDEO
        }
    }

    @TypeConverter
    fun fromJobStatus(jobStatus: JobStatus): String {
        return jobStatus.name
    }

    @TypeConverter
    fun toJobStatus(value: String): JobStatus {
        return try {
            JobStatus.valueOf(value)
        } catch (_: Exception) {
            JobStatus.PENDING
        }
    }
}
