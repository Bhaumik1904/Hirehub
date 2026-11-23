package com.hirehub.service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CircuitBreaker — Thread-safe lightweight circuit breaker protecting the application
 * against cascading timeouts and wasted network calls during upstream AI service outages.
 *
 * <p>State transitions:
 * <ul>
 *   <li><b>CLOSED</b>: Normal operation. Requests pass through. Transient failures increment
 *       the failure counter. When failures reach {@code failureThreshold}, circuit trips to <b>OPEN</b>.</li>
 *   <li><b>OPEN</b>: Rejects requests immediately (fast-fail) without making network calls.
 *       After {@code cooldownDurationMs} elapses, transitions to <b>HALF_OPEN</b>.</li>
 *   <li><b>HALF_OPEN</b>: Allows a single probe request through. If successful, resets to
 *       <b>CLOSED</b>. If the probe fails, trips back to <b>OPEN</b> for another cooldown.</li>
 * </ul>
 */
public class CircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public static final int  DEFAULT_FAILURE_THRESHOLD = 3;
    public static final long DEFAULT_COOLDOWN_MS       = 30_000L; // 30 seconds

    private final int  failureThreshold;
    private final long cooldownDurationMs;

    private final AtomicReference<State> state                 = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger          consecutiveFailures   = new AtomicInteger(0);
    private final AtomicLong             lastFailureTimestamp  = new AtomicLong(0L);
    private final AtomicBoolean          halfOpenProbeInFlight = new AtomicBoolean(false);

    /**
     * Creates a CircuitBreaker with default settings (3 failures, 30s cooldown).
     */
    public CircuitBreaker() {
        this(DEFAULT_FAILURE_THRESHOLD, DEFAULT_COOLDOWN_MS);
    }

    /**
     * Creates a CircuitBreaker with custom threshold and cooldown.
     *
     * @param failureThreshold   consecutive transient failures required to open the circuit
     * @param cooldownDurationMs cooldown duration in milliseconds before half-open probe
     */
    public CircuitBreaker(int failureThreshold, long cooldownDurationMs) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be > 0");
        }
        if (cooldownDurationMs < 0) {
            throw new IllegalArgumentException("cooldownDurationMs must be >= 0");
        }
        this.failureThreshold = failureThreshold;
        this.cooldownDurationMs = cooldownDurationMs;
    }

    /**
     * Determines whether an outgoing request should be permitted.
     *
     * @return true if the request can proceed; false if rejected by an open circuit
     */
    public boolean allowRequest() {
        State current = getState();

        if (current == State.CLOSED) {
            return true;
        }

        if (current == State.OPEN) {
            return false;
        }

        // HALF_OPEN: allow exactly one probe request
        return halfOpenProbeInFlight.compareAndSet(false, true);
    }

    /**
     * Records a successful operation (e.g. HTTP 200).
     * Resets failure counter and closes the circuit.
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        halfOpenProbeInFlight.set(false);
        state.set(State.CLOSED);
    }

    /**
     * Records a transient failure (e.g. HTTP 500, 502, 503, 504, timeout).
     */
    public void recordFailure() {
        long now = System.currentTimeMillis();
        lastFailureTimestamp.set(now);
        halfOpenProbeInFlight.set(false);

        State current = state.get();
        if (current == State.HALF_OPEN) {
            // Probe failed; re-open circuit
            state.set(State.OPEN);
        } else if (current == State.CLOSED) {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= failureThreshold) {
                state.set(State.OPEN);
            }
        } else {
            // Already OPEN; refresh timestamp
            state.set(State.OPEN);
        }
    }

    /**
     * Returns the current state, dynamically transitioning from OPEN to HALF_OPEN
     * if the cooldown period has elapsed.
     */
    public State getState() {
        State current = state.get();
        if (current == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastFailureTimestamp.get();
            if (elapsed >= cooldownDurationMs) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    halfOpenProbeInFlight.set(false);
                    return State.HALF_OPEN;
                }
                return state.get();
            }
        }
        return current;
    }

    /**
     * Returns the remaining cooldown time in seconds when OPEN.
     * Returns 0 if CLOSED or HALF_OPEN.
     */
    public long getRemainingCooldownSeconds() {
        State current = getState();
        if (current != State.OPEN) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastFailureTimestamp.get();
        long remainingMs = cooldownDurationMs - elapsed;
        return remainingMs > 0 ? (remainingMs + 999) / 1000 : 0;
    }

    /**
     * Resets the circuit breaker to its initial CLOSED state.
     */
    public void reset() {
        consecutiveFailures.set(0);
        lastFailureTimestamp.set(0L);
        halfOpenProbeInFlight.set(false);
        state.set(State.CLOSED);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public long getCooldownDurationMs() {
        return cooldownDurationMs;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public long getLastFailureTimestamp() {
        return lastFailureTimestamp.get();
    }
}

