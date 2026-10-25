package org.roruku.smartformatconverter

import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.data.repository.ActiveConversionJobs

class ActiveConversionJobsTest {
    @Test
    fun queueCancellationReachesServiceRegisteredJob() {
        val job = Job()
        ActiveConversionJobs.register("download-test", job)
        assertFalse(job.isCancelled)
        ActiveConversionJobs.cancel("download-test")
        assertTrue(job.isCancelled)
        ActiveConversionJobs.unregister("download-test", job)
    }

    @Test
    fun completedJobDoesNotCancelReplacementWithSameId() {
        val finished = Job()
        val retry = Job()
        ActiveConversionJobs.register("retry-test", finished)
        finished.complete()
        ActiveConversionJobs.register("retry-test", retry)
        ActiveConversionJobs.unregister("retry-test", finished)
        ActiveConversionJobs.cancel("retry-test")
        assertFalse(finished.isCancelled)
        assertTrue(retry.isCancelled)
        ActiveConversionJobs.unregister("retry-test", retry)
    }

    @Test
    fun cancelAllNeverReusesGenerationTokens() {
        val job1 = Job()
        val gen1 = ActiveConversionJobs.beginExecution("reuse-test", job1)
        ActiveConversionJobs.cancelAll()
        assertTrue(job1.isCancelled)
        assertFalse(ActiveConversionJobs.isCurrent("reuse-test", gen1))

        val job2 = Job()
        val gen2 = ActiveConversionJobs.beginExecution("reuse-test", job2)
        assertTrue(gen2 > gen1)
        assertTrue(ActiveConversionJobs.isCurrent("reuse-test", gen2))
        assertFalse(ActiveConversionJobs.isCurrent("reuse-test", gen1))
    }

    @Test
    fun pruneRemovesJobAndGenerationFromRegistry() {
        val job = Job()
        val gen = ActiveConversionJobs.beginExecution("prune-test", job)
        assertTrue(ActiveConversionJobs.isCurrent("prune-test", gen))
        ActiveConversionJobs.prune("prune-test")
        assertTrue(job.isCancelled)
        assertFalse(ActiveConversionJobs.isCurrent("prune-test", gen))
        assertFalse(ActiveConversionJobs.isJobActive("prune-test"))
    }

    @Test
    fun clearRemovesAllJobsAndGenerations() {
        val job1 = Job()
        val job2 = Job()
        val gen1 = ActiveConversionJobs.beginExecution("clear-1", job1)
        val gen2 = ActiveConversionJobs.beginExecution("clear-2", job2)
        ActiveConversionJobs.clear()
        assertTrue(job1.isCancelled)
        assertTrue(job2.isCancelled)
        assertFalse(ActiveConversionJobs.isCurrent("clear-1", gen1))
        assertFalse(ActiveConversionJobs.isCurrent("clear-2", gen2))
    }

    @Test
    fun fastPauseAndResumeRaceRejectsStaleExecutionToken() {
        val job1 = Job()
        val tokenWorker1 = ActiveConversionJobs.beginExecution("race-test", job1)
        assertTrue(ActiveConversionJobs.isCurrent("race-test", tokenWorker1))

        // User pauses
        ActiveConversionJobs.cancel("race-test")
        assertFalse(ActiveConversionJobs.isCurrent("race-test", tokenWorker1))

        // User immediately resumes
        val job2 = Job()
        val tokenWorker2 = ActiveConversionJobs.beginExecution("race-test", job2)

        assertTrue(ActiveConversionJobs.isCurrent("race-test", tokenWorker2))
        assertFalse(ActiveConversionJobs.isCurrent("race-test", tokenWorker1))
        ActiveConversionJobs.prune("race-test")
    }
}
