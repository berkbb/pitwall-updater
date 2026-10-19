package com.berkbb.pitwall

import org.junit.Assert.assertEquals
import org.junit.Test

class SelfUpdateCheckerTest {

    @Test
    fun newerTagIsAvailable() {
        assertEquals(SelfUpdateStatus.Available("1.0.8", "1.0.7"), SelfUpdateChecker.statusFor("v1.0.8", "1.0.7"))
        assertEquals(SelfUpdateStatus.Available("1.1.0", "1.0.10"), SelfUpdateChecker.statusFor("v1.1.0", "1.0.10"))
    }

    @Test
    fun sameOrOlderTagIsUpToDate() {
        assertEquals(SelfUpdateStatus.UpToDate("1.0.7"), SelfUpdateChecker.statusFor("v1.0.7", "1.0.7"))
        assertEquals(SelfUpdateStatus.UpToDate("1.0.8"), SelfUpdateChecker.statusFor("v1.0.7", "1.0.8"))
    }

    @Test
    fun missingTagIsUnavailable() {
        assertEquals(SelfUpdateStatus.Unavailable, SelfUpdateChecker.statusFor("", "1.0.7"))
    }
}
