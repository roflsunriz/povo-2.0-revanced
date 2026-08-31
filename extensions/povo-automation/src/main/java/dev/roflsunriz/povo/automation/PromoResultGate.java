package dev.roflsunriz.povo.automation;

import java.util.concurrent.atomic.AtomicBoolean;

final class PromoResultGate {
    private final AtomicBoolean expected = new AtomicBoolean(false);

    void expectResult() {
        expected.set(true);
    }

    boolean consumeExpectedResult() {
        return expected.compareAndSet(true, false);
    }

    boolean isExpectingResult() {
        return expected.get();
    }

    void cancel() {
        expected.set(false);
    }
}
