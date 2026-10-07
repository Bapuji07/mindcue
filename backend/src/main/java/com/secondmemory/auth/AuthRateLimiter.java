package com.secondmemory.auth;

import com.secondmemory.common.LimitExceededException;
import com.secondmemory.config.LimitsProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP sliding windows for failed logins and registrations. In-memory, which is right for the
 * single backend instance; with several instances behind a load balancer this would move to a
 * shared store (a Postgres table or Redis) so the windows are counted across instances.
 */
@Component
public class AuthRateLimiter {
    private static final int SWEEP_THRESHOLD = 10_000;

    private final Window loginFailures;
    private final Window registrations;

    @Autowired
    public AuthRateLimiter(LimitsProperties limits) {
        this(limits, Clock.systemUTC());
    }

    AuthRateLimiter(LimitsProperties limits, Clock clock) {
        this.loginFailures = new Window(limits.loginFailuresPerIp(), Duration.ofMinutes(limits.loginWindowMinutes()), clock);
        this.registrations = new Window(limits.registrationsPerIp(), Duration.ofMinutes(limits.registrationWindowMinutes()), clock);
    }

    /** Rejects a login attempt from an IP that has failed too often recently. */
    public void checkLogin(String ip) {
        if (loginFailures.count(ip) >= loginFailures.max) {
            throw new LimitExceededException("Too many failed sign-in attempts. Please wait a few minutes and try again.",
                    loginFailures.resetsAt(ip));
        }
    }

    public void recordLoginFailure(String ip) {
        loginFailures.add(ip);
    }

    public void clearLoginFailures(String ip) {
        loginFailures.clear(ip);
    }

    /** Counts a registration attempt from this IP, rejecting it once the window is full. */
    public void checkAndRecordRegistration(String ip) {
        if (!registrations.tryAdd(ip)) {
            throw new LimitExceededException("Too many accounts created from this network. Please try again later.",
                    registrations.resetsAt(ip));
        }
    }

    private static final class Window {
        private final int max;
        private final Duration length;
        private final Clock clock;
        private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

        Window(int max, Duration length, Clock clock) {
            this.max = max;
            this.length = length;
            this.clock = clock;
        }

        int count(String key) {
            Deque<Instant> deque = hits.get(key);
            if (deque == null) return 0;
            synchronized (deque) {
                prune(deque);
                return deque.size();
            }
        }

        void add(String key) {
            sweepIfLarge();
            Deque<Instant> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (deque) {
                prune(deque);
                deque.addLast(clock.instant());
            }
        }

        boolean tryAdd(String key) {
            sweepIfLarge();
            Deque<Instant> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (deque) {
                prune(deque);
                if (deque.size() >= max) return false;
                deque.addLast(clock.instant());
                return true;
            }
        }

        void clear(String key) {
            hits.remove(key);
        }

        Instant resetsAt(String key) {
            Deque<Instant> deque = hits.get(key);
            if (deque == null) return null;
            synchronized (deque) {
                Instant oldest = deque.peekFirst();
                return oldest == null ? null : oldest.plus(length);
            }
        }

        private void prune(Deque<Instant> deque) {
            Instant cutoff = clock.instant().minus(length);
            while (!deque.isEmpty() && !deque.peekFirst().isAfter(cutoff)) deque.removeFirst();
        }

        /** Keeps memory bounded: drops IPs whose windows have fully expired. */
        private void sweepIfLarge() {
            if (hits.size() < SWEEP_THRESHOLD) return;
            hits.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    prune(entry.getValue());
                    return entry.getValue().isEmpty();
                }
            });
        }
    }
}
