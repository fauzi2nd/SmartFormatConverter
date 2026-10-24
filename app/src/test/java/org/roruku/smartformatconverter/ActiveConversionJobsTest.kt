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
        ActiveConversionJobs.register("retry-test", retry)
        ActiveConversionJobs.unregister("retry-test", finished)
        ActiveConversionJobs.cancel("retry-test")
        assertFalse(finished.isCancelled)
        assertTrue(retry.isCancelled)
        ActiveConversionJobs.unregister("retry-test", retry)
    }
}
