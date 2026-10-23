package org.roruku.smartformatconverter.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionJobDao {
    @Query("SELECT * FROM conversion_jobs ORDER BY createdAt DESC")
    fun getAllJobsFlow(): Flow<List<ConversionJobEntity>>

    @Query("SELECT * FROM conversion_jobs WHERE status = :status ORDER BY createdAt DESC")
    fun getJobsByStatusFlow(status: JobStatus): Flow<List<ConversionJobEntity>>

    @Query("SELECT * FROM conversion_jobs WHERE mediaType = :mediaType ORDER BY createdAt DESC")
    fun getJobsByMediaTypeFlow(mediaType: MediaType): Flow<List<ConversionJobEntity>>

    @Query("SELECT * FROM conversion_jobs WHERE id = :jobId LIMIT 1")
    suspend fun getJobById(jobId: String): ConversionJobEntity?

    @Query("SELECT * FROM conversion_jobs WHERE status IN ('PENDING', 'PREPARING', 'RUNNING') ORDER BY createdAt ASC")
    suspend fun getActiveAndPendingJobs(): List<ConversionJobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: ConversionJobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobs(jobs: List<ConversionJobEntity>)

    @Update
    suspend fun updateJob(job: ConversionJobEntity)

    @Query("UPDATE conversion_jobs SET status = :status, progress = :progress, speed = :speed, eta = :eta WHERE id = :jobId")
    suspend fun updateProgress(jobId: String, status: JobStatus, progress: Float, speed: String?, eta: String?)

    @Query("UPDATE conversion_jobs SET status = :status, completedAt = :completedAt, outputUri = :outputUri, outputFileSize = :outputFileSize WHERE id = :jobId")
    suspend fun markCompleted(jobId: String, status: JobStatus = JobStatus.COMPLETED, completedAt: Long = System.currentTimeMillis(), outputUri: String?, outputFileSize: Long)

    @Query("UPDATE conversion_jobs SET status = :status, errorMessage = :errorMessage WHERE id = :jobId")
    suspend fun markFailed(jobId: String, errorMessage: String, status: JobStatus = JobStatus.FAILED)

    @Query("UPDATE conversion_jobs SET status = 'PENDING' WHERE status IN ('RUNNING', 'PREPARING')")
    suspend fun resetStuckJobsToPending()

    @Delete
    suspend fun deleteJob(job: ConversionJobEntity)

    @Query("DELETE FROM conversion_jobs WHERE status = 'COMPLETED'")
    suspend fun clearCompletedJobs()

    @Query("DELETE FROM conversion_jobs")
    suspend fun clearAllJobs()

    @Query("SELECT COUNT(*) FROM conversion_jobs WHERE status = 'COMPLETED'")
    fun getCompletedJobCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM conversion_jobs WHERE status = 'FAILED'")
    fun getFailedJobCountFlow(): Flow<Int>

    @Query("SELECT COALESCE(SUM(outputFileSize), 0) FROM conversion_jobs WHERE status = 'COMPLETED'")
    fun getTotalBytesConvertedFlow(): Flow<Long>
}
