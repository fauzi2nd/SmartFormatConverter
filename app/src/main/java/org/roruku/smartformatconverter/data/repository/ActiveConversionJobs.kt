package org.roruku.smartformatconverter.data.repository

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job

/**
 * Process-wide registry tracking active jobs with monotonic execution-generation
 * tokens to protect against concurrency, races from fast Pause -> Resume,
 * and callbacks from stale execution attempts.
 */
internal object ActiveConversionJobs {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val generations = ConcurrentHashMap<String, Long>()
    private val lock = Any()

    fun beginExecution(jobId: String, job: Job): Long = synchronized(lock) {
        val old = jobs[jobId]
        if (old != null && old.isActive) {
            old.cancel()
        }
        val gen = (generations[jobId] ?: 0L) + 1L
        generations[jobId] = gen
        jobs[jobId] = job
        gen
    }

    fun isCurrent(jobId: String, generation: Long): Boolean = synchronized(lock) {
        generations[jobId] == generation && jobs[jobId]?.isActive == true
    }

    fun cancel(jobId: String): Job? = synchronized(lock) {
        generations[jobId] = (generations[jobId] ?: 0L) + 1L
        val old = jobs.remove(jobId)
        old?.cancel()
        old
    }

    fun register(jobId: String, job: Job): Long = beginExecution(jobId, job)

    fun unregister(jobId: String, job: Job?) = synchronized(lock) {
        if (job != null) jobs.remove(jobId, job)
    }

    fun unregister(jobId: String, generation: Long, job: Job?) = synchronized(lock) {
        if (generations[jobId] == generation) {
            if (job != null) jobs.remove(jobId, job)
        }
    }

    fun cancelAll() = synchronized(lock) {
        generations.clear()
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    fun isJobActive(jobId: String): Boolean = synchronized(lock) {
        jobs[jobId]?.isActive == true
    }
}
