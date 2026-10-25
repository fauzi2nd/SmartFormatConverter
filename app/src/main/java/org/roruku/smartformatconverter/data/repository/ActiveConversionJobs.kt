package org.roruku.smartformatconverter.data.repository

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Job

/**
 * Process-wide registry tracking active jobs with monotonic execution-generation
 * tokens to protect against concurrency, races from fast Pause -> Resume,
 * and callbacks from stale execution attempts.
 */
internal object ActiveConversionJobs {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val generations = ConcurrentHashMap<String, Long>()
    private val globalSequence = AtomicLong(0L)
    private val lock = Any()

    fun beginExecution(jobId: String, job: Job): Long = synchronized(lock) {
        val old = jobs[jobId]
        if (old != null && old.isActive) {
            old.cancel()
        }
        val gen = globalSequence.incrementAndGet()
        generations[jobId] = gen
        jobs[jobId] = job
        gen
    }

    fun isCurrent(jobId: String, generation: Long): Boolean = synchronized(lock) {
        generations[jobId] == generation && jobs[jobId]?.isActive == true
    }

    fun cancel(jobId: String): Job? = synchronized(lock) {
        generations[jobId] = globalSequence.incrementAndGet()
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

    fun prune(jobId: String) = synchronized(lock) {
        jobs.remove(jobId)?.cancel()
        generations.remove(jobId)
    }

    fun cancelAll() = synchronized(lock) {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        // Never reset sequence backwards; invalidate all active tokens by advancing.
        for (key in generations.keys) {
            generations[key] = globalSequence.incrementAndGet()
        }
    }

    fun clear() = synchronized(lock) {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        generations.clear()
    }

    fun isJobActive(jobId: String): Boolean = synchronized(lock) {
        jobs[jobId]?.isActive == true
    }
}
