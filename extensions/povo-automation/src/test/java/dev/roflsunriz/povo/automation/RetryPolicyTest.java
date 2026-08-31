package dev.roflsunriz.povo.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RetryPolicyTest {
    @Test
    public void startsBeforeBoundaryToWarmTheConnection() {
        long now = 1_000_000L;
        long expiry = now + 60_000L;

        assertEquals(48_000L, RetryPolicy.firstAttemptDelay(now, expiry));
        assertEquals(0L, RetryPolicy.firstAttemptDelay(expiry - 12_000L, expiry));
    }

    @Test
    public void foregroundServiceWaitsOnlyInsideTheAlarmPreparationWindow() {
        long now = 1_000_000L;

        assertTrue(RetryPolicy.shouldWaitInForeground(
                now,
                now + RetryPolicy.MAX_FOREGROUND_WAIT_MS + RetryPolicy.PREWARM_LEAD_MS
        ));
        assertFalse(RetryPolicy.shouldWaitInForeground(
                now,
                now + RetryPolicy.MAX_FOREGROUND_WAIT_MS + RetryPolicy.PREWARM_LEAD_MS + 1L
        ));
    }

    @Test
    public void retriesQuicklyAcrossBoundaryThenSlowsDown() {
        long expiry = 1_000_000L;

        assertEquals(RetryPolicy.FAST_RETRY_MS, RetryPolicy.retryDelay(expiry - 5_000L, expiry));
        assertEquals(RetryPolicy.FAST_RETRY_MS, RetryPolicy.retryDelay(expiry + 9L * 60L * 1000L, expiry));
        assertEquals(RetryPolicy.SLOW_RETRY_MS, RetryPolicy.retryDelay(expiry + 11L * 60L * 1000L, expiry));
    }

    @Test
    public void givesUpOnlyAfterTwoHoursPastBoundary() {
        long expiry = 1_000_000L;

        assertFalse(RetryPolicy.shouldGiveUp(expiry + RetryPolicy.GIVE_UP_AFTER_MS, expiry));
        assertTrue(RetryPolicy.shouldGiveUp(expiry + RetryPolicy.GIVE_UP_AFTER_MS + 1L, expiry));
    }
}
