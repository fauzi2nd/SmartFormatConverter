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

    @Query("UPDATE conversion_jobs SET status = 'CANCELLED', executionToken = executionToken + 1, progress = 0, speed = NULL, eta = NULL WHERE id = :jobId AND status IN ('PENDING', 'PREPARING', 'RUNNING', 'PAUSED')")
    suspend fun cancelJob(jobId: String): Int

    /** CAS transitions prevent races with a worker reporting late progress. */
    @Query("UPDATE conversion_jobs SET status = 'PAUSED', executionToken = executionToken + 1, speed = NULL, eta = NULL WHERE id = :jobId AND status IN ('PENDING', 'PREPARING', 'RUNNING')")
    suspend fun pauseJob(jobId: String): Int

    @Query("UPDATE conversion_jobs SET status = 'PENDING', executionToken = executionToken + 1, progress = 0, speed = NULL, eta = NULL, errorMessage = NULL WHERE id = :jobId AND status = 'PAUSED'")
    suspend fun resumeJob(jobId: String): Int

    @Query("UPDATE conversion_jobs SET executionToken = :executionToken WHERE id = :jobId AND status IN ('PENDING', 'PREPARING', 'RUNNING')")
    suspend fun bindExecutionToken(jobId: String, executionToken: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: ConversionJobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobs(jobs: List<ConversionJobEntity>)

    @Update
    suspend fun updateJob(job: ConversionJobEntity)

    @Query("UPDATE conversion_jobs SET status = :status, progress = :progress, speed = :speed, eta = :eta WHERE id = :jobId AND (executionToken = :executionToken OR :executionToken = 0) AND status IN ('PENDING', 'PREPARING', 'RUNNING')")
    suspend fun updateProgress(jobId: String, status: JobStatus, progress: Float, speed: String?, eta: String?, executionToken: Long = 0L)

    @Query("UPDATE conversion_jobs SET status = :status, progress = 1.0, speed = NULL, eta = '00:00', completedAt = :completedAt, outputUri = :outputUri, outputFileSize = :outputFileSize WHERE id = :jobId AND (executionToken = :executionToken OR :executionToken = 0) AND status IN ('PENDING', 'PREPARING', 'RUNNING')")
    suspend fun markCompleted(jobId: String, status: JobStatus = JobStatus.COMPLETED, completedAt: Long = System.currentTimeMillis(), outputUri: String?, outputFileSize: Long, executionToken: Long = 0L): Int

    @Query("UPDATE conversion_jobs SET status = :status, errorMessage = :errorMessage, speed = NULL, eta = NULL WHERE id = :jobId AND (executionToken = :executionToken OR :executionToken = 0) AND status IN ('PENDING', 'PREPARING', 'RUNNING')")
    suspend fun markFailed(jobId: String, errorMessage: String, status: JobStatus = JobStatus.FAILED, executionToken: Long = 0L): Int

    /** Update a failed job's immutable video snapshot only with user consent. */
    @Query("UPDATE conversion_jobs SET customParamsJson = :snapshot, status = 'PENDING', " +
        "executionToken = executionToken + 1, progress = 0, speed = NULL, eta = NULL, " +
        "errorMessage = NULL, outputUri = NULL, outputFileSize = 0, completedAt = NULL, " +
        "retryCount = retryCount + 1 " +
        "WHERE id = :jobId AND status = 'FAILED' AND mediaType = 'VIDEO' " +
        "AND lower(outputFormat) = 'webm' AND executionToken = :oldToken")
    suspend fun retryFailedWebmWithDownscale(
        jobId: String, oldToken: Long, snapshot: String
    ): Int

    @Query("UPDATE conversion_jobs SET status = 'PENDING', executionToken = executionToken + 1 WHERE status IN ('RUNNING', 'PREPARING')")
    suspend fun resetStuckJobsToPending()

    @Query("UPDATE conversion_jobs SET status = 'INTERRUPTED', executionToken = executionToken + 1, speed = NULL, eta = NULL, errorMessage = :reason WHERE status IN ('RUNNING', 'PREPARING')")
    suspend fun interruptRunningJobs(reason: String): Int

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
