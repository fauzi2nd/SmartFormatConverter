package org.roruku.smartformatconverter.data.repository

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job

/**
 * ConversionRepository is instantiated by both QueueViewModel and the
 * foreground service. A process-wide registry lets Queue cancel the *actual*
 * active task instead of a different repository instance's empty local map.
 *
 * Every item gets a child Job of the service SupervisorJob, so cancelling
 * one download cannot cancel the entire queue processing loop.
 */
internal object ActiveConversionJobs {
    private val jobs = ConcurrentHashMap<String, Job>()

    fun register(jobId: String, job: Job) {
        jobs[jobId] = job
    }

    fun cancel(jobId: String) {
        jobs[jobId]?.cancel()
    }

    fun unregister(jobId: String, job: Job?) {
        if (job != null) jobs.remove(jobId, job)
    }

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}
