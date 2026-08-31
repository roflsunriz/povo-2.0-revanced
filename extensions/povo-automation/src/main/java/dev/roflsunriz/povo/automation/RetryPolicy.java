package dev.roflsunriz.povo.automation;

final class RetryPolicy {
    static final long PREWARM_LEAD_MS = 12_000L;
    static final long FAST_RETRY_MS = 3_000L;
    static final long SLOW_RETRY_MS = 60_000L;
    static final long NETWORK_RETRY_MS = 5_000L;
    static final long REQUEST_WATCHDOG_MS = 60_000L;
    static final long GIVE_UP_AFTER_MS = 2L * 60L * 60L * 1000L;
    static final long WAKE_LOCK_TIMEOUT_MS = 20L * 60L * 1000L;
    static final long MAX_FOREGROUND_WAIT_MS = 5L * 60L * 1000L;

    private RetryPolicy() {}

    static long firstAttemptDelay(long now, long expiry) {
        return Math.max(0L, expiry - PREWARM_LEAD_MS - now);
    }

    static boolean shouldWaitInForeground(long now, long expiry) {
        return firstAttemptDelay(now, expiry) <= MAX_FOREGROUND_WAIT_MS;
    }

    static long retryDelay(long now, long expiry) {
        long elapsed = now - expiry;
        return elapsed < 10L * 60L * 1000L ? FAST_RETRY_MS : SLOW_RETRY_MS;
    }

    static boolean shouldGiveUp(long now, long expiry) {
        return now - expiry > GIVE_UP_AFTER_MS;
    }
}
