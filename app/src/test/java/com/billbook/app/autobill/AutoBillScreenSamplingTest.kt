package com.billbook.app.autobill

import org.junit.Assert.*
import org.junit.Test

class AutoBillScreenSamplingTest {
    @Test fun stalledPageStopsAfterFiveReadsDespiteContinuousContentEvents() {
        val sampling = AutoBillScreenSampling()
        val scheduled = mutableListOf<Long>()
        var delay = sampling.request()
        while (delay != null) {
            scheduled.add(delay)
            // Animated or repeatedly redrawn pages must not restart their budget.
            repeat(100) { assertNull(sampling.request()) }
            delay = sampling.retry()
            assertTrue("A stalled page must not poll indefinitely", scheduled.size <= 5)
        }
        assertEquals(listOf(350L, 500L, 900L, 1_400L, 2_000L), scheduled)
        assertEquals(5_150L, scheduled.sum())
        repeat(100) {
            assertNull(sampling.request())
            assertNull(sampling.retry())
        }
    }

    @Test fun successfulReadStopsRetriesAndLaterContentCanStartAnotherPayment() {
        val sampling = AutoBillScreenSampling()
        assertEquals(350L, sampling.request())
        assertEquals(500L, sampling.retry())
        sampling.succeeded()
        assertNull(sampling.retry())

        // The same Activity can display another transaction without a new window.
        assertEquals(350L, sampling.request())
        assertNull(sampling.request())
        assertEquals(500L, sampling.retry())
    }

    @Test fun pendingPageCanCompleteAfterItsInitialReadBudgetExpires() {
        val sampling = AutoBillScreenSampling()
        sampling.request()
        repeat(5) { sampling.retry() }
        repeat(100) { assertNull(sampling.request()) }

        // A later changed status is evidence for a new read series, unlike redraws.
        assertEquals(350L, sampling.request(contentChanged = true))
        repeat(100) { assertNull(sampling.request(contentChanged = true)) }
        assertEquals(500L, sampling.retry())
        sampling.succeeded()
        assertNull(sampling.retry())
    }

    @Test fun newSessionRestoresTheBudgetAfterExhaustionOrInFlightRead() {
        val sampling = AutoBillScreenSampling()
        sampling.request()
        repeat(5) { sampling.retry() }
        assertNull(sampling.request())
        sampling.reset()
        assertEquals(350L, sampling.request())
        assertEquals(500L, sampling.retry())

        sampling.reset()
        assertNull(sampling.retry())
        assertEquals(350L, sampling.request())
        assertEquals(500L, sampling.retry())
    }

    @Test fun retryWithoutASeriesCannotStartPollingOrReviveExhaustion() {
        val sampling = AutoBillScreenSampling()
        assertNull(sampling.retry())
        sampling.succeeded()
        assertNull(sampling.retry())
        sampling.request()
        repeat(5) { sampling.retry() }
        sampling.succeeded()
        assertNull(sampling.request())
    }
}
